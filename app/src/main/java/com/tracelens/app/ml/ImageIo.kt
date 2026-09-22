package com.tracelens.app.ml

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

class Decoded(
    val bitmap: Bitmap,
    /** ukuran asli file setelah rotasi EXIF (bukan ukuran bitmap hasil downscale). */
    val origWidth: Int,
    val origHeight: Int,
)

object ImageIo {
    /** Decode hemat memori: inSampleSize + downscale ke sisi terpanjang [maxSide], lalu terapkan rotasi EXIF. */
    fun decode(context: Context, uri: Uri, maxSide: Int): Decoded? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            val nb = Bitmap.createScaledBitmap(bmp, max(1, (bmp.width * s).roundToInt()), max(1, (bmp.height * s).roundToInt()), true)
            if (nb !== bmp) bmp.recycle()
            bmp = nb
        }
        val orientation = readOrientation(cr, uri)
        val m = orientationMatrix(orientation)
        if (m != null) {
            val rb = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rb !== bmp) bmp.recycle()
            bmp = rb
        }
        val swap = orientation in 5..8
        return Decoded(bmp, if (swap) bounds.outHeight else bounds.outWidth, if (swap) bounds.outWidth else bounds.outHeight)
    }

    fun readOrientation(cr: ContentResolver, uri: Uri): Int = try {
        cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: 1
    } catch (_: Exception) {
        1
    }

    private fun orientationMatrix(o: Int): Matrix? {
        val m = Matrix()
        when (o) {
            2 -> m.setScale(-1f, 1f)
            3 -> m.setRotate(180f)
            4 -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            5 -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            6 -> m.setRotate(90f)
            7 -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            8 -> m.setRotate(-90f)
            else -> return null
        }
        return m
    }

    fun pixels(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px
    }
}

/** Crop + warp wajah ke 112×112 memakai matriks affine 2×3 (koordinat gambar → crop). */
object FaceCropper {
    fun warp(src: Bitmap, m: FloatArray, size: Int = 112): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(0xFF000000.toInt())
        val mat = Matrix()
        mat.setValues(floatArrayOf(m[0], m[1], m[2], m[3], m[4], m[5], 0f, 0f, 1f))
        canvas.drawBitmap(src, mat, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }
}

/** Thumbnail wajah (crop 112×112) disimpan sebagai JPEG di penyimpanan privat aplikasi. */
class FaceThumbStore(context: Context) {
    private val dir = File(context.filesDir, "facethumbs").apply { mkdirs() }

    fun file(faceId: Long) = File(dir, "$faceId.jpg")

    fun save(faceId: Long, bmp: Bitmap) {
        FileOutputStream(file(faceId)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    fun delete(ids: List<Long>) = ids.forEach { file(it).delete() }

    fun deleteAll() = dir.listFiles()?.forEach { it.delete() }
}
