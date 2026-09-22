package com.tracelens.app.ml

import android.content.Context

enum class SimLabel(val text: String) {
    STRONG("Strong visual similarity"),
    POSSIBLE("Possible match"),
    WEAK("Weak similarity"),
}

/**
 * Pasangan detektor+embedder. Vektor dari profil berbeda TIDAK kompatibel, jadi setiap wajah
 * di database menyimpan [id] profil yang menghasilkannya.
 */
enum class FaceProfile(
    val id: String,
    val title: String,
    val detectorAsset: String,
    val embedderAsset: String,
    val dim: Int,
    /** ambang cosine "Possible match" dan "Strong" (heuristik dari uji internal, bukan probabilitas identitas). */
    val possible: Float,
    val strong: Float,
    val bundledByDefault: Boolean,
    val licenseNote: String,
) {
    LITE(
        "lite", "Lite — RFB-640 + MobileFaceNet-192",
        "models/rfb640.onnx", "models/mobilefacenet_192.onnx", 192, 0.55f, 0.70f, true,
        "Kode MIT (Linzaer, syaringan357). Data latih: WIDER FACE / MS-Celeb-1M turunan — cek istilah dataset sebelum dipakai komersial.",
    ),
    INSIGHT(
        "insight", "InsightFace — SCRFD-500M + ArcFace MBF (512)",
        "models/det_500m.onnx", "models/w600k_mbf.onnx", 512, 0.35f, 0.50f, false,
        "Bobot model InsightFace: HANYA riset non-komersial. Tidak dibundel; pasang manual lewat tools/fetch_insightface.sh.",
    );

    /** Profil ini memakai 5 landmark (alignment mata/hidung/mulut) atau hanya box. */
    val usesLandmarks: Boolean get() = this == INSIGHT

    fun isAvailable(context: Context): Boolean {
        val names = context.assets.list("models")?.toSet() ?: emptySet()
        return detectorAsset.substringAfter('/') in names && embedderAsset.substringAfter('/') in names
    }

    fun label(cos: Float): SimLabel = when {
        cos >= strong -> SimLabel.STRONG
        cos >= possible -> SimLabel.POSSIBLE
        else -> SimLabel.WEAK
    }

    companion object {
        fun byId(id: String): FaceProfile = entries.firstOrNull { it.id == id } ?: LITE
    }
}
