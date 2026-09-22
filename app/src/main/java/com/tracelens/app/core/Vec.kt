package com.tracelens.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.PriorityQueue
import kotlin.math.sqrt

/** Operasi vektor & serialisasi blob. Pure Kotlin (tanpa dependensi Android). */
object Vec {
    fun l2Normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x.toDouble() * x
        val n = sqrt(s).toFloat()
        val out = FloatArray(v.size)
        if (n < 1e-12f) return out
        for (i in v.indices) out[i] = v[i] / n
        return out
    }

    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}

object Blob {
    fun floatsToBytes(f: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        bb.asFloatBuffer().put(f)
        return bb.array()
    }

    fun bytesToFloats(b: ByteArray): FloatArray {
        val out = FloatArray(b.size / 4)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }

    fun longsToBytes(l: LongArray): ByteArray {
        val bb = ByteBuffer.allocate(l.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        bb.asLongBuffer().put(l)
        return bb.array()
    }

    fun bytesToLongs(b: ByteArray): LongArray {
        val out = LongArray(b.size / 8)
        ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().get(out)
        return out
    }
}

/** Menyimpan K skor terbesar (min-heap). */
class TopK<T>(private val k: Int) {
    private class Entry<T>(val score: Float, val item: T)

    private val heap = PriorityQueue<Entry<T>>(k + 1) { a, b -> a.score.compareTo(b.score) }

    fun offer(score: Float, item: T) {
        if (heap.size < k) heap.add(Entry(score, item))
        else if (score > heap.peek()!!.score) {
            heap.poll(); heap.add(Entry(score, item))
        }
    }

    fun minScore(): Float = if (heap.size < k) Float.NEGATIVE_INFINITY else heap.peek()!!.score

    fun sortedDescending(): List<Pair<Float, T>> =
        heap.sortedByDescending { it.score }.map { it.score to it.item }
}
