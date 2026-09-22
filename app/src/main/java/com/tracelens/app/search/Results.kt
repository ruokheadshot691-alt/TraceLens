package com.tracelens.app.search

import android.graphics.Bitmap
import com.tracelens.app.ml.DetectedFace
import com.tracelens.app.ml.SimLabel

/** Hasil Face Search: kemiripan visual (bukan identifikasi identitas). */
class FaceHit(
    val faceId: Long,
    val imageId: Long,
    val imageUri: String,
    val imageName: String,
    val collectionName: String,
    /** cosine similarity -1..1 */
    val cosine: Float,
    val label: SimLabel,
    val nx1: Float, val ny1: Float, val nx2: Float, val ny2: Float,
) {
    /** persen untuk ditampilkan (0..100, cosine negatif dipotong ke 0) */
    val percent: Int get() = (cosine.coerceIn(0f, 1f) * 100f).toInt()
}

enum class ImageMode(val title: String) {
    NEAR_DUPLICATE("Near-duplicate (resize / crop / kompresi)"),
    SIMILAR_LOOK("Similar look (warna & layout)"),
}

class ImageHit(
    val imageId: Long,
    val uri: String,
    val name: String,
    val collectionName: String,
    val score: Float,
    val full: Float,
    val region: Float,
    val color: Float,
    val viaCrop: Boolean,
) {
    val percent: Int get() = (score.coerceIn(0f, 1f) * 100f).toInt()
}

class QueryFaces(val faces: List<DetectedFace>, val previewWidth: Int, val previewHeight: Int) {
    fun recycle() = faces.forEach { runCatching { it.thumb.recycle() } }
}

class QueryImage(val bitmap: Bitmap?, val name: String)
