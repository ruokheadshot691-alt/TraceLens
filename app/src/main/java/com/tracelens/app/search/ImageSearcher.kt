package com.tracelens.app.search

import android.content.Context
import android.net.Uri
import com.tracelens.app.core.ImageHash
import com.tracelens.app.core.ImageMatcher
import com.tracelens.app.core.ImageSignature
import com.tracelens.app.core.TopK
import com.tracelens.app.data.AppDatabase
import com.tracelens.app.data.ImageHashRow
import com.tracelens.app.data.Prefs
import com.tracelens.app.ml.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Foto → pHash/dHash (+ 36 jendela crop + tata letak warna) → Database → Similarity Ranking. */
class ImageSearcher(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: Prefs,
) {
    fun signatureOf(uri: Uri): ImageSignature {
        val dec = ImageIo.decode(context, uri, prefs.decodeMaxSide) ?: error("Gambar tidak bisa dibaca")
        try {
            return ImageHash.signature(ImageHash.buildThumb(ImageIo.pixels(dec.bitmap), dec.bitmap.width, dec.bitmap.height))
        } finally {
            dec.bitmap.recycle()
        }
    }

    fun defaultThreshold(mode: ImageMode): Float = when (mode) {
        ImageMode.NEAR_DUPLICATE -> prefs.minImageSim
        ImageMode.SIMILAR_LOOK -> 0.80f
    }

    suspend fun indexedCount(collectionId: Long?): Int = db.images().indexedCount(collectionId)

    suspend fun search(sig: ImageSignature, mode: ImageMode, collectionId: Long?, minScore: Float, topK: Int = 100): List<ImageHit> =
        withContext(Dispatchers.Default) {
            val heap = TopK<Pair<ImageHashRow, com.tracelens.app.core.MatchScore>>(topK)
            var after = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = db.images().hashPage(after, collectionId, 1000)
                if (page.isEmpty()) break
                for (row in page) {
                    val m = ImageMatcher.compare(sig, ImageSignature.fromBlobs(row.hashBlob, row.colorBlob))
                    val s = when (mode) {
                        ImageMode.NEAR_DUPLICATE -> m.best
                        ImageMode.SIMILAR_LOOK -> ImageMatcher.similarLook(m)
                    }.toFloat()
                    if (s >= minScore && s > heap.minScore()) heap.offer(s, row to m)
                }
                after = page.last().id
            }
            val names = HashMap<Long, String>()
            heap.sortedDescending().map { (s, pair) ->
                val (row, m) = pair
                val cname = names.getOrPut(row.collectionId) { db.collections().nameOf(row.collectionId) ?: "-" }
                ImageHit(row.id, row.uri, row.name, cname, s, m.full.toFloat(), m.region.toFloat(), m.color.toFloat(), m.viaCrop && mode == ImageMode.NEAR_DUPLICATE)
            }
        }
}
