package com.tracelens.app.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class OcrResult(val text: String, val confidence: Int)

/** OCR offline dengan Tesseract (tessdata_fast eng + ind, Apache-2.0). Tidak thread-safe → serialisasi. */
class OcrEngine(private val context: Context) {
    private val root = File(context.filesDir, "tesseract")

    private fun ensureData(langs: List<String>) {
        val dir = File(root, "tessdata").apply { mkdirs() }
        for (l in langs) {
            val f = File(dir, "$l.traineddata")
            if (!f.exists() || f.length() == 0L) {
                context.assets.open("tessdata/$l.traineddata").use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            }
        }
    }

    @Synchronized
    fun recognize(bitmap: Bitmap, langs: String = "eng+ind"): OcrResult {
        ensureData(langs.split('+'))
        val api = TessBaseAPI()
        try {
            if (!api.init(root.absolutePath, langs)) error("Tesseract gagal init ($langs)")
            api.setImage(bitmap)
            val text = api.getUTF8Text() ?: ""
            return OcrResult(text.trim(), api.meanConfidence())
        } finally {
            api.recycle()
        }
    }
}
