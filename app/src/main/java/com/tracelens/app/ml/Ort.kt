package com.tracelens.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Utilitas ONNX Runtime. */
object Ort {
    val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    fun session(context: Context, asset: String, threads: Int): OrtSession {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val opts = OrtSession.SessionOptions()
        opts.setIntraOpNumThreads(threads)
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        return env.createSession(bytes, opts)
    }

    fun directFloatBuffer(count: Int): FloatBuffer =
        ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun tensor(buf: FloatBuffer, vararg shape: Long): OnnxTensor {
        buf.rewind()
        return OnnxTensor.createTensor(env, buf, shape)
    }

    fun toFloatArray(t: OnnxTensor): FloatArray {
        val fb = t.floatBuffer
        val out = FloatArray(fb.remaining())
        fb.get(out)
        return out
    }
}
