package com.couchzap.iptv.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("couchzap_prefs", Context.MODE_PRIVATE)

    fun getAspectRatioMode(): Int {
        return prefs.getInt("aspect_ratio_mode", ASPECT_FIT)
    }

    fun setAspectRatioMode(mode: Int) {
        prefs.edit().putInt("aspect_ratio_mode", mode).apply()
    }

    fun getInterfaceMode(): String = prefs.getString("interface_mode", "Auto") ?: "Auto"

    fun setInterfaceMode(mode: String) {
        prefs.edit().putString("interface_mode", mode).apply()
    }

    companion object {
        const val ASPECT_FIT = 0
        const val ASPECT_FILL = 1
        const val ASPECT_16_9 = 2
        const val ASPECT_4_3 = 3
    }
}
