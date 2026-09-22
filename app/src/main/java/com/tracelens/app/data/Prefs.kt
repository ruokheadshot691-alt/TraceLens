package com.tracelens.app.data

import android.content.Context

/** Pengaturan sederhana (SharedPreferences). Semua lokal. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("tracelens", Context.MODE_PRIVATE)

    var profileId: String
        get() = sp.getString("profile", "lite") ?: "lite"
        set(v) = sp.edit().putString("profile", v).apply()

    /** Mode RAM rendah: gambar di-decode lebih kecil, thread lebih sedikit. */
    var lowRam: Boolean
        get() = sp.getBoolean("lowRam", false)
        set(v) = sp.edit().putBoolean("lowRam", v).apply()

    /** Ambang minimum kemiripan wajah (0..1). <0 = pakai default profil. */
    var minFaceSim: Float
        get() = sp.getFloat("minFaceSim", -1f)
        set(v) = sp.edit().putFloat("minFaceSim", v).apply()

    var minImageSim: Float
        get() = sp.getFloat("minImageSim", 0.75f)
        set(v) = sp.edit().putFloat("minImageSim", v).apply()

    val decodeMaxSide: Int get() = if (lowRam) 800 else 1280
    val threads: Int get() = if (lowRam) 2 else Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
}
