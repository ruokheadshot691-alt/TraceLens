package com.tracelens.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.tracelens.app.core.FaceBox
import com.tracelens.app.core.RfbDecoder
import com.tracelens.app.core.ScrfdDecoder
import kotlin.math.max

interface FaceDetector : AutoCloseable {
    /** Box dalam piksel [bitmap]. */
    fun detect(bitmap: Bitmap): List<FaceBox>
}

/** Ultra-Light-Fast-Generic-Face-Detector-1MB (RFB-640), MIT. Hanya box, tanpa landmark. */
class RfbFaceDetector(context: Context, asset: String, threads: Int) : FaceDetector {
    private val session: OrtSession = Ort.session(context, asset, threads)
    private val inputName = session.inputNames.first()
    private val w = 640
    private val h = 480

    override fun detect(bitmap: Bitmap): List<FaceBox> {
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        val hw = w * h
        val buf = Ort.directFloatBuffer(3 * hw)
        for (i in 0 until hw) {
            val p = px[i]
            buf.put(i, (((p shr 16) and 0xFF) - 127f) / 128f)
            buf.put(hw + i, (((p shr 8) and 0xFF) - 127f) / 128f)
            buf.put(2 * hw + i, ((p and 0xFF) - 127f) / 128f)
        }
        return Ort.tensor(buf, 1, 3, h.toLong(), w.toLong()).use { input ->
            session.run(mapOf(inputName to input)).use { res ->
                val scores = Ort.toFloatArray(res[0] as OnnxTensor)
                val boxes = Ort.toFloatArray(res[1] as OnnxTensor)
                RfbDecoder.decode(scores, boxes, bitmap.width, bitmap.height, 0.7f, 0.3f)
            }
        }
    }

    override fun close() = session.close()
}

/** SCRFD-500M (InsightFace). Box + 5 landmark. Input 640×640 letterbox. */
class ScrfdFaceDetector(context: Context, asset: String, threads: Int) : FaceDetector {
    private val session: OrtSession = Ort.session(context, asset, threads)
    private val inputName = session.inputNames.first()
    private val size = 640

    override fun detect(bitmap: Bitmap): List<FaceBox> {
        val scale = size.toFloat() / max(bitmap.width, bitmap.height)
        val nw = max(1, (bitmap.width * scale).toInt())
        val nh = max(1, (bitmap.height * scale).toInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true)
        val px = IntArray(nw * nh)
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh)
        if (scaled !== bitmap) scaled.recycle()
        val plane = size * size
        val buf = Ort.directFloatBuffer(3 * plane)
        val pad = (0f - 127.5f) / 128f
        for (y in 0 until size) for (x in 0 until size) {
            val i = y * size + x
            if (x < nw && y < nh) {
                val p = px[y * nw + x]
                buf.put(i, (((p shr 16) and 0xFF) - 127.5f) / 128f)
                buf.put(plane + i, (((p shr 8) and 0xFF) - 127.5f) / 128f)
                buf.put(2 * plane + i, ((p and 0xFF) - 127.5f) / 128f)
            } else {
                buf.put(i, pad); buf.put(plane + i, pad); buf.put(2 * plane + i, pad)
            }
        }
        return Ort.tensor(buf, 1, 3, size.toLong(), size.toLong()).use { input ->
            session.run(mapOf(inputName to input)).use { res ->
                val outs = Array(9) { Ort.toFloatArray(res[it] as OnnxTensor) }
                ScrfdDecoder.decode(outs, size, scale, 0.5f, 0.4f)
            }
        }
    }

    override fun close() = session.close()
}
