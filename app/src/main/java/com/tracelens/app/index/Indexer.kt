package com.tracelens.app.index

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import com.tracelens.app.core.Blob
import com.tracelens.app.core.ImageHash
import com.tracelens.app.data.AppDatabase
import com.tracelens.app.data.FaceEntity
import com.tracelens.app.data.ImageEntity
import com.tracelens.app.data.ImageStatus
import com.tracelens.app.data.Prefs
import com.tracelens.app.meta.MetadataReader
import com.tracelens.app.ml.DetectedFace
import com.tracelens.app.ml.FaceEngine
import com.tracelens.app.ml.FaceProfile
import com.tracelens.app.ml.FaceThumbStore
import com.tracelens.app.ml.ImageIo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Pengindeks: menghitung SEKALI per gambar → hash (pHash/dHash 37 jendela + warna), wajah + embedding, EXIF date.
 * Bisa dilanjutkan kapan saja: hanya memproses gambar berstatus PENDING atau yang dihitung dengan profil model lain.
 */
class Indexer(
    private val context: Context,
    private val db: AppDatabase,
    private val engine: FaceEngine,
    private val prefs: Prefs,
    private val thumbs: FaceThumbStore,
) {
    private val _active = MutableStateFlow<Long?>(null)

    /** id koleksi yang sedang diproses (null = idle). */
    val activeCollection: StateFlow<Long?> = _active

    /** Sinkronkan daftar gambar koleksi-folder dengan isi folder sebenarnya. */
    suspend fun sync(collectionId: Long) {
        val c = db.collections().get(collectionId) ?: return
        val tree = c.treeUri ?: return
        _active.value = collectionId
        try {
            val found = withContext(Dispatchers.IO) { SafScanner.listImages(context, Uri.parse(tree)) }
            val existing = db.images().stubs(collectionId).associateBy { it.uri }
            val foundUris = HashSet<String>(found.size * 2)
            found.forEach { foundUris.add(it.uri) }

            found.filter { it.uri !in existing }
                .map { ImageEntity(collectionId = collectionId, uri = it.uri, name = it.name, mime = it.mime, size = it.size, modified = it.modified) }
                .chunked(500).forEach { db.images().insertAll(it) }

            for (f in found) {
                val e = existing[f.uri] ?: continue
                if (e.modified != f.modified || e.size != f.size) {
                    thumbs.delete(db.faces().idsForImage(e.id))
                    db.faces().deleteForImage(e.id)
                    db.images().markChanged(e.id, f.modified, f.size)
                }
            }
            val removed = existing.values.filter { it.uri !in foundUris }.map { it.id }
            removed.forEach { thumbs.delete(db.faces().idsForImage(it)) }
            removed.chunked(500).forEach { db.images().deleteByIds(it) }
        } finally {
            _active.value = null
        }
    }

    /** Proses semua gambar yang menunggu di koleksi. Aman dibatalkan (coroutine cancel). */
    suspend fun process(collectionId: Long) {
        val profile = engine.activeProfile()
        _active.value = collectionId
        try {
            db.images().retryErrors(collectionId)
            while (true) {
                currentCoroutineContext().ensureActive()
                val batch = db.images().pending(collectionId, profile.id, if (prefs.lowRam) 8 else 24)
                if (batch.isEmpty()) break
                for (img in batch) {
                    currentCoroutineContext().ensureActive()
                    processOne(img, profile)
                }
            }
        } finally {
            _active.value = null
        }
    }

    suspend fun syncAndProcess(collectionId: Long) {
        sync(collectionId)
        process(collectionId)
    }

    private class Computed(val image: ImageEntity, val faces: List<DetectedFace>)

    private suspend fun processOne(img: ImageEntity, profile: FaceProfile) {
        val computed: Computed = withContext(Dispatchers.Default) {
            try {
                compute(img, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) { // termasuk OutOfMemoryError → tandai ERROR, lanjut ke gambar berikutnya
                Computed(img.copy(status = ImageStatus.ERROR, indexedAt = System.currentTimeMillis()), emptyList())
            }
        }
        try {
            val ids = db.withTransaction {
                db.faces().deleteForImage(img.id)
                val entities = computed.faces.map {
                    FaceEntity(
                        imageId = img.id, modelId = profile.id, embedding = Blob.floatsToBytes(it.embedding),
                        x1 = it.nx1, y1 = it.ny1, x2 = it.nx2, y2 = it.ny2, score = it.box.score,
                    )
                }
                val newIds = db.faces().insertAll(entities)
                db.images().update(computed.image)
                newIds
            }
            computed.faces.forEachIndexed { i, f -> runCatching { thumbs.save(ids[i], f.thumb) } }
        } catch (_: SQLiteConstraintException) {
            // gambar/koleksi sudah dihapus saat indexing berjalan → abaikan
        } finally {
            computed.faces.forEach { it.thumb.recycle() }
        }
    }

    private fun compute(img: ImageEntity, profile: FaceProfile): Computed {
        val uri = Uri.parse(img.uri)
        val dec = ImageIo.decode(context, uri, prefs.decodeMaxSide)
            ?: return Computed(img.copy(status = ImageStatus.ERROR, indexedAt = System.currentTimeMillis()), emptyList())
        val bmp = dec.bitmap
        try {
            val sig = ImageHash.signature(ImageHash.buildThumb(ImageIo.pixels(bmp), bmp.width, bmp.height))
            val faces = engine.analyze(bmp)
            val updated = img.copy(
                width = dec.origWidth, height = dec.origHeight,
                exifDate = MetadataReader.exifDate(context, uri),
                hashBlob = sig.toHashBlob(), colorBlob = sig.color,
                faceModelId = profile.id, faceCount = faces.size,
                status = ImageStatus.DONE, indexedAt = System.currentTimeMillis(),
            )
            return Computed(updated, faces)
        } finally {
            bmp.recycle()
        }
    }
}
