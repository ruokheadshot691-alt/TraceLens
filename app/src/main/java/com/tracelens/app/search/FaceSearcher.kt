package com.tracelens.app.search

import android.content.Context
import android.net.Uri
import com.tracelens.app.core.Blob
import com.tracelens.app.core.TopK
import com.tracelens.app.core.Vec
import com.tracelens.app.data.AppDatabase
import com.tracelens.app.data.FaceRow
import com.tracelens.app.data.Prefs
import com.tracelens.app.ml.FaceEngine
import com.tracelens.app.ml.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Foto → Face Detection → Face Embedding → Vector Search (cosine, brute-force per halaman) → Ranking. */
class FaceSearcher(
    private val context: Context,
    private val db: AppDatabase,
    private val engine: FaceEngine,
    private val prefs: Prefs,
) {
    /** Deteksi semua wajah pada foto query. */
    suspend fun detect(uri: Uri): QueryFaces = withContext(Dispatchers.Default) {
        val dec = ImageIo.decode(context, uri, prefs.decodeMaxSide) ?: error("Gambar tidak bisa dibaca")
        try {
            QueryFaces(engine.analyze(dec.bitmap), dec.bitmap.width, dec.bitmap.height)
        } finally {
            dec.bitmap.recycle()
        }
    }

    fun defaultThreshold(): Float = prefs.minFaceSim.takeIf { it >= 0f } ?: engine.activeProfile().possible

    suspend fun indexedFaceCount(collectionId: Long?): Int = db.faces().count(engine.activeProfile().id, collectionId)

    suspend fun search(query: FloatArray, collectionId: Long?, minCosine: Float, topK: Int = 100): List<FaceHit> =
        withContext(Dispatchers.Default) {
            val profile = engine.activeProfile()
            val heap = TopK<FaceRow>(topK * 3)
            var after = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = db.faces().page(profile.id, after, collectionId, 2000)
                if (page.isEmpty()) break
                for (r in page) {
                    val cos = Vec.dot(query, Blob.bytesToFloats(r.embedding))
                    if (cos >= minCosine && cos > heap.minScore()) heap.offer(cos, r)
                }
                after = page.last().faceId
            }
            val bestPerImage = LinkedHashMap<Long, Pair<Float, FaceRow>>()
            for ((cos, row) in heap.sortedDescending()) {
                if (row.imageId !in bestPerImage) bestPerImage[row.imageId] = cos to row
            }
            val names = HashMap<Long, String>()
            bestPerImage.values.take(topK).mapNotNull { (cos, row) ->
                val info = db.images().info(row.imageId) ?: return@mapNotNull null
                val cname = names.getOrPut(info.collectionId) { db.collections().nameOf(info.collectionId) ?: "-" }
                FaceHit(
                    row.faceId, info.id, info.uri, info.name, cname, cos, profile.label(cos),
                    row.x1, row.y1, row.x2, row.y2,
                )
            }
        }
}
