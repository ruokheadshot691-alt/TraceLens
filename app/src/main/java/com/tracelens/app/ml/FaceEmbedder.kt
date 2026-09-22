package com.tracelens.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.tracelens.app.core.Vec
import java.nio.FloatBuffer

interface FaceEmbedder : AutoCloseable {
    val dim: Int

    /** [faces] = crop wajah 112×112. Hasil sudah L2-normalisasi. */
    fun embed(faces: List<Bitmap>): List<FloatArray>
}

private const val S = 112

private fun fillNchw(buf: FloatBuffer, offset: Int, bmp: Bitmap, mean: Float, std: Float) {
    val px = IntArray(S * S)
    bmp.getPixels(px, 0, S, 0, 0, S, S)
    val plane = S * S
    for (i in 0 until plane) {
        val p = px[i]
        buf.put(offset + i, (((p shr 16) and 0xFF) - mean) / std)
        buf.put(offset + plane + i, (((p shr 8) and 0xFF) - mean) / std)
        buf.put(offset + 2 * plane + i, ((p and 0xFF) - mean) / std)
    }
}

/**
 * MobileFaceNet 192-d (syaringan357/sirius-ai, MIT/Apache) dikonversi TFLite→ONNX.
 * Model punya batch TETAP = 2, jadi wajah diproses berpasangan (ganjil → duplikat pelengkap).
 * Preprocessing asli: RGB, (v-127.5)/128.
 */
class MobileFaceNetEmbedder(context: Context, asset: String, threads: Int) : FaceEmbedder {
    private val session: OrtSession = Ort.session(context, asset, threads)
    private val inputName = session.inputNames.first()
    override val dim = 192

    override fun embed(faces: List<Bitmap>): List<FloatArray> {
        val out = ArrayList<FloatArray>(faces.size)
        val plane3 = 3 * S * S
        var i = 0
        while (i < faces.size) {
            val a = faces[i]
            val b = if (i + 1 < faces.size) faces[i + 1] else a
            val buf = Ort.directFloatBuffer(2 * plane3)
            fillNchw(buf, 0, a, 127.5f, 128f)
            fillNchw(buf, plane3, b, 127.5f, 128f)
            Ort.tensor(buf, 2, 3, S.toLong(), S.toLong()).use { input ->
                session.run(mapOf(inputName to input)).use { res ->
                    val flat = Ort.toFloatArray(res[0] as OnnxTensor)
                    out.add(Vec.l2Normalize(flat.copyOfRange(0, dim)))
                    if (i + 1 < faces.size) out.add(Vec.l2Normalize(flat.copyOfRange(dim, 2 * dim)))
                }
            }
            i += 2
        }
        return out
    }

    override fun close() = session.close()
}

/** ArcFace MobileFaceNet w600k (InsightFace) 512-d. Preprocessing: RGB, (v-127.5)/127.5. */
class ArcFaceEmbedder(context: Context, asset: String, threads: Int) : FaceEmbedder {
    private val session: OrtSession = Ort.session(context, asset, threads)
    private val inputName = session.inputNames.first()
    override val dim = 512

    override fun embed(faces: List<Bitmap>): List<FloatArray> = faces.map { f ->
        val buf = Ort.directFloatBuffer(3 * S * S)
        fillNchw(buf, 0, f, 127.5f, 127.5f)
        Ort.tensor(buf, 1, 3, S.toLong(), S.toLong()).use { input ->
            session.run(mapOf(inputName to input)).use { res ->
                Vec.l2Normalize(Ort.toFloatArray(res[0] as OnnxTensor).copyOfRange(0, dim))
            }
        }
    }

    override fun close() = session.close()
}
