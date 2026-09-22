package com.tracelens.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor

/** Thumbnail abu-abu 128×128 + tanda warna 4×4×RGB dari piksel ARGB. */
class Thumb(val gray: DoubleArray, val color: ByteArray)

/** Signature satu gambar: pHash+dHash untuk setiap jendela (index 0 = gambar penuh) + tata letak warna. */
class ImageSignature(val hashes: LongArray, val color: ByteArray) {
    val windowCount get() = hashes.size / 2
    fun pHash(i: Int) = hashes[2 * i]
    fun dHash(i: Int) = hashes[2 * i + 1]
    fun toHashBlob(): ByteArray = Blob.longsToBytes(hashes)
    companion object {
        fun fromBlobs(hashBlob: ByteArray, colorBlob: ByteArray) = ImageSignature(Blob.bytesToLongs(hashBlob), colorBlob)
    }
}

class MatchScore(
    /** kemiripan gambar penuh vs penuh, 0..1 */ val full: Double,
    /** kemiripan terbaik lewat jendela (crop), 0..1 */ val region: Double,
    /** kemiripan tata letak warna, 0..1 */ val color: Double,
) {
    val best get() = maxOf(full, region)
    val viaCrop get() = region > full + 0.02
}

object ImageHash {
    const val THUMB = 128
    const val COLOR_GRID = 4

    /** Jendela crop (x0,y0,x1,y1 pecahan). Index 0 = penuh. 3 skala jendela persegi-relatif × 9 posisi + skala 0.6. */
    val WINDOWS: Array<DoubleArray> = buildList {
        add(doubleArrayOf(0.0, 0.0, 1.0, 1.0))
        for (f in doubleArrayOf(0.9, 0.8, 0.7, 0.6)) {
            for (oy in doubleArrayOf(0.0, 0.5, 1.0)) for (ox in doubleArrayOf(0.0, 0.5, 1.0)) {
                val x0 = (1 - f) * ox; val y0 = (1 - f) * oy
                add(doubleArrayOf(x0, y0, x0 + f, y0 + f))
            }
        }
    }.toTypedArray()

    private val DCT8: Array<DoubleArray> = Array(8) { u -> DoubleArray(32) { x -> cos((2 * x + 1) * u * PI / 64.0) } }

    private fun lum(p: Int): Int {
        val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
        return (77 * r + 150 * g + 29 * b) shr 8
    }

    /** Bangun thumbnail dari piksel ARGB. Binning integer deterministik. */
    fun buildThumb(argb: IntArray, w: Int, h: Int): Thumb {
        val n = THUMB
        val gray = DoubleArray(n * n)
        val rs = DoubleArray(n * n); val gs = DoubleArray(n * n); val bs = DoubleArray(n * n)
        if (w >= n && h >= n) {
            val cnt = IntArray(n * n)
            val sg = LongArray(n * n); val sr = LongArray(n * n); val sgc = LongArray(n * n); val sb = LongArray(n * n)
            for (y in 0 until h) {
                val by = (y.toLong() * n / h).toInt()
                val row = y * w
                for (x in 0 until w) {
                    val bx = (x.toLong() * n / w).toInt()
                    val p = argb[row + x]
                    val i = by * n + bx
                    cnt[i]++
                    sg[i] += lum(p).toLong()
                    sr[i] += ((p shr 16) and 0xFF).toLong(); sgc[i] += ((p shr 8) and 0xFF).toLong(); sb[i] += (p and 0xFF).toLong()
                }
            }
            for (i in 0 until n * n) {
                val c = cnt[i].toDouble()
                gray[i] = sg[i] / c; rs[i] = sr[i] / c; gs[i] = sgc[i] / c; bs[i] = sb[i] / c
            }
        } else { // gambar sangat kecil: sampling terdekat
            for (by in 0 until n) for (bx in 0 until n) {
                val sx = minOf(w - 1, (bx.toLong() * w / n).toInt()); val sy = minOf(h - 1, (by.toLong() * h / n).toInt())
                val p = argb[sy * w + sx]
                val i = by * n + bx
                gray[i] = lum(p).toDouble()
                rs[i] = ((p shr 16) and 0xFF).toDouble(); gs[i] = ((p shr 8) and 0xFF).toDouble(); bs[i] = (p and 0xFF).toDouble()
            }
        }
        val g = COLOR_GRID
        val cell = n / g
        val color = ByteArray(g * g * 3)
        for (cy in 0 until g) for (cx in 0 until g) {
            var r = 0.0; var gg = 0.0; var b = 0.0
            for (yy in 0 until cell) for (xx in 0 until cell) {
                val i = (cy * cell + yy) * n + (cx * cell + xx)
                r += rs[i]; gg += gs[i]; b += bs[i]
            }
            val d = (cell * cell).toDouble()
            val o = (cy * g + cx) * 3
            color[o] = floor(r / d + 0.5).toInt().coerceIn(0, 255).toByte()
            color[o + 1] = floor(gg / d + 0.5).toInt().coerceIn(0, 255).toByte()
            color[o + 2] = floor(b / d + 0.5).toInt().coerceIn(0, 255).toByte()
        }
        return Thumb(gray, color)
    }

    /** Resize area-average (fractional coverage) region [x0,x1)×[y0,y1) dari src persegi [sw]. */
    fun areaResize(src: DoubleArray, sw: Int, x0: Int, y0: Int, x1: Int, y1: Int, dw: Int, dh: Int): DoubleArray {
        val rw = (x1 - x0).toDouble(); val rh = (y1 - y0).toDouble()
        val out = DoubleArray(dw * dh)
        val fx = rw / dw; val fy = rh / dh
        for (dy in 0 until dh) {
            val ya = y0 + dy * fy; val yb = y0 + (dy + 1) * fy
            for (dx in 0 until dw) {
                val xa = x0 + dx * fx; val xb = x0 + (dx + 1) * fx
                var acc = 0.0; var wsum = 0.0
                var iy = floor(ya).toInt()
                while (iy < yb) {
                    val wy = minOf(yb, iy + 1.0) - maxOf(ya, iy.toDouble())
                    if (wy > 0) {
                        var ix = floor(xa).toInt()
                        while (ix < xb) {
                            val wx = minOf(xb, ix + 1.0) - maxOf(xa, ix.toDouble())
                            if (wx > 0) { val wgt = wx * wy; acc += src[iy * sw + ix] * wgt; wsum += wgt }
                            ix++
                        }
                    }
                    iy++
                }
                out[dy * dw + dx] = acc / wsum
            }
        }
        return out
    }

    fun pHash(gray32: DoubleArray): Long {
        val tmp = Array(8) { DoubleArray(32) }
        for (u in 0 until 8) for (x in 0 until 32) {
            var s = 0.0
            for (y in 0 until 32) s += DCT8[u][y] * gray32[y * 32 + x]
            tmp[u][x] = s
        }
        val low = DoubleArray(64)
        for (u in 0 until 8) for (v in 0 until 8) {
            var s = 0.0
            for (x in 0 until 32) s += tmp[u][x] * DCT8[v][x]
            low[u * 8 + v] = s
        }
        val sorted = low.sortedArray()
        val med = (sorted[31] + sorted[32]) / 2.0
        var h = 0L
        for (v in low) h = (h shl 1) or (if (v > med) 1L else 0L)
        return h
    }

    /** [gray72] = 8 baris × 9 kolom. */
    fun dHash(gray72: DoubleArray): Long {
        var h = 0L
        for (r in 0 until 8) for (c in 0 until 8) h = (h shl 1) or (if (gray72[r * 9 + c + 1] > gray72[r * 9 + c]) 1L else 0L)
        return h
    }

    fun signature(t: Thumb): ImageSignature {
        val hs = LongArray(WINDOWS.size * 2)
        for ((i, w) in WINDOWS.withIndex()) {
            val x0 = floor(w[0] * THUMB + 0.5).toInt(); val y0 = floor(w[1] * THUMB + 0.5).toInt()
            val x1 = floor(w[2] * THUMB + 0.5).toInt(); val y1 = floor(w[3] * THUMB + 0.5).toInt()
            hs[2 * i] = pHash(areaResize(t.gray, THUMB, x0, y0, x1, y1, 32, 32))
            hs[2 * i + 1] = dHash(areaResize(t.gray, THUMB, x0, y0, x1, y1, 9, 8))
        }
        return ImageSignature(hs, t.color)
    }

    fun toHex(h: Long): String = java.lang.Long.toHexString(h).padStart(16, '0')
}

object ImageMatcher {
    /** Kemiripan 0..1 dari rata-rata jarak Hamming pHash & dHash (128 bit total). */
    fun hashSim(p1: Long, d1: Long, p2: Long, d2: Long): Double =
        1.0 - (java.lang.Long.bitCount(p1 xor p2) + java.lang.Long.bitCount(d1 xor d2)) / 128.0

    fun colorSim(a: ByteArray, b: ByteArray): Double {
        var s = 0
        for (i in a.indices) s += kotlin.math.abs((a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF))
        return 1.0 - s / (255.0 * a.size)
    }

    fun compare(q: ImageSignature, d: ImageSignature): MatchScore {
        val full = hashSim(q.pHash(0), q.dHash(0), d.pHash(0), d.dHash(0))
        var region = 0.0
        val nw = minOf(q.windowCount, d.windowCount)
        for (j in 1 until nw) {
            // query = crop dari gambar DB  /  gambar DB = crop dari query
            region = maxOf(region, hashSim(q.pHash(0), q.dHash(0), d.pHash(j), d.dHash(j)))
            region = maxOf(region, hashSim(q.pHash(j), q.dHash(j), d.pHash(0), d.dHash(0)))
        }
        return MatchScore(full, region, colorSim(q.color, d.color))
    }

    /** Skor mode "Similar look": campuran struktur (penuh) dan tata letak warna. */
    fun similarLook(m: MatchScore): Double = 0.4 * m.full + 0.6 * m.color
}
