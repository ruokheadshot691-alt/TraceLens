package com.tracelens.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Nilai harapan dihasilkan oleh referensi Python independen (tools/ref_gen.py: numpy + SVD Umeyama).
 * Jika ada test yang gagal, berarti port Kotlin menyimpang dari referensi.
 */
class CoreTest {
    private fun synthPixels(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val r = (x * 5 + y * 3) % 256; val g = (x * y) % 256; val b = (x * x + y) % 256
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun assertWin(sig: ImageSignature, i: Int, p: String, d: String) {
        assertEquals("pHash win$i", p, ImageHash.toHex(sig.pHash(i)))
        assertEquals("dHash win$i", d, ImageHash.toHex(sig.dHash(i)))
    }

    @Test fun windowCount() = assertEquals(37, ImageHash.WINDOWS.size)

    @Test fun thumbAndHashesMatchPython_normalPath() {
        val t = ImageHash.buildThumb(synthPixels(200, 150), 200, 150)
        assertEquals(2050830.25, t.gray.sum(), 1e-6)
        val expectColor = intArrayOf(129, 113, 110, 129, 126, 119, 129, 124, 105, 129, 123, 115, 127, 124, 119, 127, 127, 134, 127, 126, 120, 127, 126, 130,
            128, 125, 131, 128, 128, 134, 128, 126, 128, 127, 126, 134, 127, 125, 139, 128, 125, 124, 128, 125, 136, 128, 125, 125)
        for (i in expectColor.indices) assertEquals("color[$i]", expectColor[i], t.color[i].toInt() and 0xFF)
        val s = ImageHash.signature(t)
        assertWin(s, 0, "85087f2a007fab5f", "a5aa5255b5ab6a56")
        assertWin(s, 1, "88093a5415ff0b7f", "b4ad6b5ad2b4a569")
        assertWin(s, 5, "ef6ebfaa0021d055", "2d6b52d6a5ad6b5a")
        assertWin(s, 20, "ce0356fc295c217d", "666dc9db93b6266c")
        assertWin(s, 36, "d3f95206bf0208f7", "99bb337666ecccd9")
    }

    @Test fun thumbAndHashesMatchPython_smallImageSamplingPath() {
        val t = ImageHash.buildThumb(synthPixels(100, 90), 100, 90)
        assertEquals(2003300.0, t.gray.sum(), 1e-6)
        val s = ImageHash.signature(t)
        assertWin(s, 0, "80040f1b0a7f3f7f", "e7eede9cb93b73e7")
        assertWin(s, 1, "80040f58157f3f7f", "f3a7cfce9cbd397b")
        assertWin(s, 5, "f9e40f5a00594fcd", "e7cede9c3d7973f7")
        assertWin(s, 20, "b5624e1d285f151f", "e7678b9f9e3e7479")
        assertWin(s, 36, "e04998539e52fb72", "f9f3b7c7cfdf9d3f")
    }

    @Test fun identicalSignatureScoresOne() {
        val s = ImageHash.signature(ImageHash.buildThumb(synthPixels(200, 150), 200, 150))
        val m = ImageMatcher.compare(s, s)
        assertEquals(1.0, m.full, 1e-9); assertEquals(1.0, m.color, 1e-9)
        val s2 = ImageSignature.fromBlobs(s.toHashBlob(), s.color)
        assertEquals(1.0, ImageMatcher.compare(s, s2).full, 1e-9)
    }

    private fun smooth(w: Int, h: Int, ox: Int = 0, oy: Int = 0, fullW: Int = w, fullH: Int = h): IntArray = IntArray(w * h) { i ->
        val x = (i % w + ox).toDouble() / fullW; val y = (i / w + oy).toDouble() / fullH
        val v = 128 + 70 * sin(x * 9.0) * kotlin.math.cos(y * 7.0) + 40 * sin((x + y) * 17.0)
        val c = v.toInt().coerceIn(0, 255)
        (0xFF shl 24) or (c shl 16) or ((255 - c) shl 8) or ((c * 3) % 256)
    }

    @Test fun croppedImageMatchesViaWindow() {
        val fw = 400; val fh = 300
        val full = ImageHash.signature(ImageHash.buildThumb(smooth(fw, fh), fw, fh))
        // crop kiri-atas 60% x 60% → sama dengan jendela index 28
        val cw = (fw * 0.6).toInt(); val ch = (fh * 0.6).toInt()
        val crop = ImageHash.signature(ImageHash.buildThumb(smooth(cw, ch, 0, 0, fw, fh), cw, ch))
        val m = ImageMatcher.compare(crop, full)
        assertTrue("full=${m.full} region=${m.region}", m.region >= 0.90)
        assertTrue("crop harus terdeteksi lewat jendela: full=${m.full}", m.viaCrop)
    }

    @Test fun differentImagesScoreLow() {
        val a = ImageHash.signature(ImageHash.buildThumb(synthPixels(200, 150), 200, 150))
        val b = ImageHash.signature(ImageHash.buildThumb(smooth(200, 150), 200, 150))
        val m = ImageMatcher.compare(a, b)
        assertTrue("region=${m.region}", m.best < 0.80)
    }

    @Test fun similarityTransformMatchesUmeyama() {
        val k1 = floatArrayOf(120.5f, 80.2f, 170.1f, 78.9f, 146.0f, 110.3f, 128.7f, 140.8f, 166.2f, 139.5f)
        val e1 = floatArrayOf(0.69265f, -0.01921f, -43.19556f, 0.01921f, 0.69265f, -7.0596f)
        val m1 = FaceGeometry.similarity(k1)
        for (i in 0 until 6) assertEquals("SIM[$i]", e1[i].toDouble(), m1[i].toDouble(), 2e-3)
        val k2 = floatArrayOf(33f, 41f, 60f, 35f, 50f, 60f, 38f, 75f, 62f, 70f)
        val e2 = floatArrayOf(1.17027f, -0.16181f, 8.24457f, 0.16181f, 1.17027f, -1.73214f)
        val m2 = FaceGeometry.similarity(k2)
        for (i in 0 until 6) assertEquals("SIM2[$i]", e2[i].toDouble(), m2[i].toDouble(), 2e-3)
    }

    @Test fun squareCropCentersFace() {
        val box = FaceBox(100f, 100f, 200f, 180f, 0.9f)
        val m = FaceGeometry.squareCrop(box, 0.15f, 112)
        val c = FaceGeometry.apply(m, box.cx, box.cy)
        assertEquals(56.0, c[0].toDouble(), 1e-3); assertEquals(56.0, c[1].toDouble(), 1e-3)
        val corner = FaceGeometry.apply(m, box.cx - 65f, box.cy - 65f) // setengah sisi = 130/2
        assertEquals(0.0, corner[0].toDouble(), 1e-3); assertEquals(0.0, corner[1].toDouble(), 1e-3)
    }

    @Test fun nmsRemovesOverlaps() {
        val boxes = listOf(
            FaceBox(0f, 0f, 100f, 100f, 0.9f), FaceBox(5f, 5f, 105f, 105f, 0.8f), FaceBox(300f, 300f, 400f, 400f, 0.7f),
        )
        val keep = Nms.run(boxes, 0.3f)
        assertEquals(2, keep.size); assertEquals(0.9f, keep[0].score, 1e-6f); assertEquals(0.7f, keep[1].score, 1e-6f)
    }

    @Test fun rfbDecoderScalesAndThresholds() {
        val scores = floatArrayOf(0.9f, 0.1f, 0.2f, 0.8f, 0.3f, 0.7f)   // (bg,face) x3 → face = 0.1, 0.8, 0.7
        val boxes = floatArrayOf(0f, 0f, 0.1f, 0.1f, 0.25f, 0.5f, 0.5f, 1.0f, 0.26f, 0.5f, 0.51f, 1.0f)
        val r = RfbDecoder.decode(scores, boxes, 200, 100, 0.7f, 0.3f)
        assertEquals(1, r.size)   // dua box tumpang tindih → NMS sisakan skor 0.8
        assertEquals(50.0, r[0].x1.toDouble(), 1e-3); assertEquals(50.0, r[0].y1.toDouble(), 1e-3)
        assertEquals(100.0, r[0].x2.toDouble(), 1e-3); assertEquals(100.0, r[0].y2.toDouble(), 1e-3)
    }

    @Test fun scrfdDecoderMatchesPython() {
        val size = 64
        val outs = Array(9) { li ->
            val stride = intArrayOf(8, 16, 32)[li % 3]
            val n = (size / stride) * (size / stride) * 2
            when (li / 3) {
                0 -> FloatArray(n) { i -> (((i * 37 + stride) % 101) / 100.0).toFloat() }
                1 -> FloatArray(n * 4) { i -> (((i * 13 + 7) % 17) / 10.0).toFloat() }
                else -> FloatArray(n * 10) { i -> ((((i * 11 + 3) % 23) - 11) / 10.0).toFloat() }
            }
        }
        val r = ScrfdDecoder.decode(outs, size, 0.5f, 0.5f, 0.4f)
        assertEquals(73, r.size)
        fun has(x1: Double, y1: Double, x2: Double, y2: Double, s: Double, kx: Double, ky: Double) = r.any {
            abs(it.x1 - x1) < 0.02 && abs(it.y1 - y1) < 0.02 && abs(it.x2 - x2) < 0.02 && abs(it.y2 - y2) < 0.02 &&
                abs(it.score - s) < 0.006 && abs(it.landmarks!![0] - kx) < 0.02 && abs(it.landmarks[1] - ky) < 0.02
        }
        assertTrue(has(20.8, 59.2, 57.6, 83.2, 1.0, 27.2, 76.8))
        assertTrue(has(25.6, -25.6, 76.8, 0.0, 1.0, 32.0, 3.2))
        assertTrue(has(11.2, 70.4, 35.2, 108.8, 0.99, 28.8, 89.6))
        assertTrue(has(43.2, 33.6, 72.0, 49.6, 0.97, 73.6, 38.4))
        assertTrue(has(17.6, 40.0, 33.6, 70.4, 0.5, 36.8, 33.6))
    }

    @Test fun blobsAndVectors() {
        val f = floatArrayOf(1f, -2.5f, 3.25f)
        assertTrue(f.contentEquals(Blob.bytesToFloats(Blob.floatsToBytes(f))))
        val l = longArrayOf(Long.MIN_VALUE, 0x123456789abcdefL, -1L)
        assertTrue(l.contentEquals(Blob.bytesToLongs(Blob.longsToBytes(l))))
        val n = Vec.l2Normalize(floatArrayOf(3f, 4f))
        assertEquals(0.6, n[0].toDouble(), 1e-6); assertEquals(1.0, Vec.dot(n, n).toDouble(), 1e-6)
        assertEquals(0f, Vec.l2Normalize(floatArrayOf(0f, 0f))[0], 0f)
    }

    @Test fun topKKeepsBest() {
        val t = TopK<String>(3)
        listOf(0.1f to "a", 0.9f to "b", 0.5f to "c", 0.7f to "d", 0.3f to "e").forEach { t.offer(it.first, it.second) }
        assertEquals(listOf("b", "d", "c"), t.sortedDescending().map { it.second })
        assertEquals(0.5f, t.minScore(), 1e-6f)
    }
}
