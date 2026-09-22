package com.tracelens.app.web

import android.content.Intent
import android.net.Uri

/**
 * Modul pencarian sumber publik (opsional, modular).
 * Sengaja HANYA membuka halaman pencarian resmi di browser — pengguna memilih/mengunggah foto sendiri di sana.
 * Aplikasi TIDAK mengunggah foto, TIDAK scraping, TIDAK melewati CAPTCHA/login/paywall, TIDAK mengakses akun privat.
 * Provider baru cukup mengimplementasi [SourceProvider] dan didaftarkan di [SourceProviders].
 */
interface SourceProvider {
    val id: String
    val name: String
    val description: String
    fun buildIntent(): Intent
}

class BrowserPageProvider(
    override val id: String,
    override val name: String,
    override val description: String,
    private val url: String,
) : SourceProvider {
    override fun buildIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
}

object SourceProviders {
    private val registry = mutableListOf<SourceProvider>(
        BrowserPageProvider("google-images", "Google Images / Lens", "Reverse image search umum", "https://images.google.com/"),
        BrowserPageProvider("tineye", "TinEye", "Cari sumber & versi lain dari sebuah gambar", "https://tineye.com/"),
        BrowserPageProvider("bing-visual", "Bing Visual Search", "Pencarian visual Microsoft", "https://www.bing.com/visualsearch"),
        BrowserPageProvider("yandex-images", "Yandex Images", "Reverse image search Yandex", "https://yandex.com/images/"),
    )

    val all: List<SourceProvider> get() = registry.toList()
    fun register(p: SourceProvider) { registry.removeAll { it.id == p.id }; registry.add(p) }
}
