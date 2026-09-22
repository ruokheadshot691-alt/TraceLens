package com.tracelens.app.index

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.tracelens.app.data.AppDatabase
import com.tracelens.app.data.CollectionEntity
import com.tracelens.app.data.CollectionSummary
import com.tracelens.app.data.ImageEntity
import com.tracelens.app.ml.FaceThumbStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CollectionRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val thumbs: FaceThumbStore,
) {
    fun observe(): Flow<List<CollectionSummary>> = db.collections().observeSummaries()

    private fun persist(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // izin tidak bisa dipersist (mis. dari picker sementara) → tetap dicoba dipakai sesi ini
        }
    }

    /** Koleksi dari satu folder. Isi dipindai saat indexing. */
    suspend fun createFolder(tree: Uri): Long {
        persist(tree)
        val docId = DocumentsContract.getTreeDocumentId(tree)
        val name = docId.substringAfterLast('/').substringAfterLast(':').ifBlank { "Folder" }
        return db.collections().insert(CollectionEntity(name = name, treeUri = tree.toString(), createdAt = System.currentTimeMillis()))
    }

    /** Koleksi dari gambar-gambar yang dipilih manual. */
    suspend fun createFromImages(name: String, uris: List<Uri>): Long {
        val id = db.collections().insert(CollectionEntity(name = name, treeUri = null, createdAt = System.currentTimeMillis()))
        addImages(id, uris)
        return id
    }

    suspend fun addImages(collectionId: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val rows = uris.map { u ->
            persist(u)
            var name = u.lastPathSegment ?: "image"
            var size = 0L
            var modified = 0L
            try {
                context.contentResolver.query(
                    u,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                        if (c.columnCount > 2 && !c.isNull(2)) modified = c.getLong(2)
                    }
                }
            } catch (_: Exception) {
            }
            ImageEntity(collectionId = collectionId, uri = u.toString(), name = name, mime = context.contentResolver.getType(u), size = size, modified = modified)
        }
        rows.chunked(500).forEach { db.images().insertAll(it) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val c = db.collections().get(id)
        thumbs.delete(db.faces().idsForCollection(id))
        db.collections().delete(id)          // CASCADE → images → faces
        c?.treeUri?.let {
            try {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
        }
    }
}
