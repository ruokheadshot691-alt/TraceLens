package com.tracelens.app.meta

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

class ImageMeta(
    val sections: List<Pair<String, List<Pair<String, String>>>>,
    val exifDate: String?,
)

object MetadataReader {
    fun exifDate(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use {
            val e = ExifInterface(it)
            e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
        }
    } catch (_: Exception) {
        null
    }

    fun read(context: Context, uri: Uri): ImageMeta {
        val cr = context.contentResolver
        var name: String? = null
        var size = -1L
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) -1L else c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        val file = mutableListOf<Pair<String, String>>()
        file += "Nama" to (name ?: "-")
        file += "Tipe" to (cr.getType(uri) ?: "-")
        if (size >= 0) file += "Ukuran" to humanSize(size)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try { cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } } catch (_: Exception) {}
        if (bounds.outWidth > 0) file += "Dimensi" to "${bounds.outWidth} × ${bounds.outHeight} px"

        val camera = mutableListOf<Pair<String, String>>()
        val exposure = mutableListOf<Pair<String, String>>()
        val location = mutableListOf<Pair<String, String>>()
        var date: String? = null
        try {
            cr.openInputStream(uri)?.use { s ->
                val e = ExifInterface(s)
                fun put(list: MutableList<Pair<String, String>>, label: String, tag: String) {
                    e.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { list += label to it }
                }
                put(camera, "Merek", ExifInterface.TAG_MAKE)
                put(camera, "Model", ExifInterface.TAG_MODEL)
                put(camera, "Software", ExifInterface.TAG_SOFTWARE)
                date = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
                date?.let { camera += "Waktu ambil" to it }
                put(exposure, "Apertur (f)", ExifInterface.TAG_F_NUMBER)
                put(exposure, "Shutter (s)", ExifInterface.TAG_EXPOSURE_TIME)
                put(exposure, "ISO", ExifInterface.TAG_ISO_SPEED_RATINGS)
                put(exposure, "Focal length", ExifInterface.TAG_FOCAL_LENGTH)
                put(exposure, "Flash", ExifInterface.TAG_FLASH)
                put(exposure, "Orientasi EXIF", ExifInterface.TAG_ORIENTATION)
                val ll = e.latLong
                if (ll != null) location += "Koordinat GPS" to "%.5f, %.5f".format(ll[0], ll[1])
            }
        } catch (_: Exception) {
        }
        val sections = buildList {
            add("File" to file)
            if (camera.isNotEmpty()) add("Kamera" to camera)
            if (exposure.isNotEmpty()) add("Eksposur" to exposure)
            add("Lokasi (EXIF)" to (if (location.isEmpty()) listOf("GPS" to "Tidak ada data lokasi") else location))
        }
        return ImageMeta(sections, date)
    }

    fun humanSize(b: Long): String = when {
        b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
        b >= 1024 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }
}
