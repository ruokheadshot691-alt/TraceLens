package com.tracelens.app.ml

import android.content.Context
import android.graphics.Bitmap
import com.tracelens.app.core.FaceBox
import com.tracelens.app.core.FaceGeometry
import com.tracelens.app.data.Prefs
import kotlin.math.max
import kotlin.math.min

class DetectedFace(
    /** box dalam piksel bitmap yang dianalisis */
    val box: FaceBox,
    /** box ternormalisasi 0..1 */
    val nx1: Float, val ny1: Float, val nx2: Float, val ny2: Float,
    /** crop 112×112 (recycle sendiri setelah dipakai) */
    val thumb: Bitmap,
    /** ter-L2-normalisasi */
    val embedding: FloatArray,
)

/** Pipeline: Face Detection → alignment/crop → Face Embedding. Model dimuat malas & dibagikan. */
class FaceEngine(private val context: Context, private val prefs: Prefs) {
    private var loaded: FaceProfile? = null
    private var loadedThreads = 0
    private var detector: FaceDetector? = null
    private var embedder: FaceEmbedder? = null

    /** Ketersediaan file model dicek sekali (assets.list itu I/O) — dipanggil sering oleh UI. */
    private val available: Map<FaceProfile, Boolean> by lazy { FaceProfile.entries.associateWith { it.isAvailable(context) } }

    fun activeProfile(): FaceProfile {
        val p = FaceProfile.byId(prefs.profileId)
        return if (available[p] == true) p else FaceProfile.LITE
    }

    @Synchronized
    private fun ensureLoaded(): FaceProfile {
        val p = activeProfile()
        val threads = prefs.threads
        if (loaded != p || loadedThreads != threads) {
            release()
            when (p) {
                FaceProfile.LITE -> {
                    detector = RfbFaceDetector(context, p.detectorAsset, threads)
                    embedder = MobileFaceNetEmbedder(context, p.embedderAsset, threads)
                }
                FaceProfile.INSIGHT -> {
                    detector = ScrfdFaceDetector(context, p.detectorAsset, threads)
                    embedder = ArcFaceEmbedder(context, p.embedderAsset, threads)
                }
            }
            loaded = p
            loadedThreads = threads
        }
        return p
    }

    /** Deteksi + embedding semua wajah di [bmp] (dibatasi [maxFaces] terbesar). */
    @Synchronized
    fun analyze(bmp: Bitmap, maxFaces: Int = 40): List<DetectedFace> {
        val p = ensureLoaded()
        val minSide = max(24f, 0.03f * min(bmp.width, bmp.height))
        val boxes = detector!!.detect(bmp)
            .filter { min(it.width, it.height) >= minSide }
            .sortedByDescending { it.area }
            .take(maxFaces)
        if (boxes.isEmpty()) return emptyList()
        val thumbs = boxes.map { b ->
            val m = if (p.usesLandmarks && b.landmarks != null) FaceGeometry.similarity(b.landmarks) else FaceGeometry.squareCrop(b)
            FaceCropper.warp(bmp, m)
        }
        val embs = try {
            embedder!!.embed(thumbs)
        } catch (t: Throwable) {
            thumbs.forEach { it.recycle() }
            throw t
        }
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        return boxes.mapIndexed { i, b ->
            DetectedFace(
                b,
                (b.x1 / w).coerceIn(0f, 1f), (b.y1 / h).coerceIn(0f, 1f),
                (b.x2 / w).coerceIn(0f, 1f), (b.y2 / h).coerceIn(0f, 1f),
                thumbs[i], embs[i],
            )
        }
    }

    @Synchronized
    fun release() {
        detector?.close(); embedder?.close()
        detector = null; embedder = null; loaded = null
    }
}
