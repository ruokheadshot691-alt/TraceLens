package com.tracelens.app.core

import kotlin.math.max
import kotlin.math.min

/** Kotak wajah dalam piksel gambar. [landmarks] = 5 titik (x,y) ×5 = 10 float, atau null. */
class FaceBox(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val score: Float,
    val landmarks: FloatArray? = null,
) {
    val width get() = x2 - x1
    val height get() = y2 - y1
    val cx get() = (x1 + x2) / 2f
    val cy get() = (y1 + y2) / 2f
    val area get() = max(0f, width) * max(0f, height)
}

object Nms {
    fun run(boxes: List<FaceBox>, iouThr: Float): List<FaceBox> {
        val sorted = boxes.sortedByDescending { it.score }
        val keep = ArrayList<FaceBox>()
        val dead = BooleanArray(sorted.size)
        for (i in sorted.indices) {
            if (dead[i]) continue
            val a = sorted[i]
            keep.add(a)
            for (j in i + 1 until sorted.size) {
                if (dead[j]) continue
                val b = sorted[j]
                val iw = max(0f, min(a.x2, b.x2) - max(a.x1, b.x1))
                val ih = max(0f, min(a.y2, b.y2) - max(a.y1, b.y1))
                val inter = iw * ih
                val iou = inter / (a.area + b.area - inter + 1e-9f)
                if (iou > iouThr) dead[j] = true
            }
        }
        return keep
    }
}

/** Transformasi affine 2×3 = [m00,m01,m02, m10,m11,m12], memetakan koordinat gambar → koordinat crop. */
object FaceGeometry {
    /** Template 5 titik ArcFace untuk crop 112×112 (mata kiri, mata kanan, hidung, mulut kiri, mulut kanan). */
    val ARC_TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
    )

    /** Similarity transform (skala+rotasi+translasi, tanpa refleksi) least-squares src→dst. */
    fun similarity(src: FloatArray, dst: FloatArray = ARC_TEMPLATE): FloatArray {
        val n = src.size / 2
        var msx = 0.0; var msy = 0.0; var mdx = 0.0; var mdy = 0.0
        for (i in 0 until n) {
            msx += src[2 * i]; msy += src[2 * i + 1]; mdx += dst[2 * i]; mdy += dst[2 * i + 1]
        }
        msx /= n; msy /= n; mdx /= n; mdy /= n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val px = src[2 * i] - msx; val py = src[2 * i + 1] - msy
            val qx = dst[2 * i] - mdx; val qy = dst[2 * i + 1] - mdy
            num1 += px * qx + py * qy
            num2 += px * qy - py * qx
            den += px * px + py * py
        }
        if (den < 1e-9) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        val a = num1 / den
        val b = num2 / den
        // M = [[a, -b], [b, a]]
        val tx = mdx - (a * msx - b * msy)
        val ty = mdy - (b * msx + a * msy)
        return floatArrayOf(a.toFloat(), (-b).toFloat(), tx.toFloat(), b.toFloat(), a.toFloat(), ty.toFloat())
    }

    /** Crop persegi di sekitar [box] dengan margin relatif, di-resize ke [out]×[out]. */
    fun squareCrop(box: FaceBox, margin: Float = 0.15f, out: Int = 112): FloatArray {
        val side = max(box.width, box.height) * (1f + 2f * margin)
        val s = out / side
        val half = out / 2f
        return floatArrayOf(s, 0f, half - box.cx * s, 0f, s, half - box.cy * s)
    }

    fun apply(m: FloatArray, x: Float, y: Float): FloatArray =
        floatArrayOf(m[0] * x + m[1] * y + m[2], m[3] * x + m[4] * y + m[5])
}
