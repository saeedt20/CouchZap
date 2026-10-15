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

    fun getEffectsMode(): String = prefs.getString("effects_mode", "auto")?.takeIf { it in listOf("auto","full","safe","off") } ?: "auto"
    fun setEffectsMode(mode: String) { require(mode in listOf("auto","full","safe","off")); prefs.edit().putString("effects_mode",mode).apply() }

    fun getLanguage(): String? = prefs.getString("language", null)?.takeIf(AppLanguage::isSupported)

    fun setLanguage(language: String) {
        require(AppLanguage.isSupported(language))
        prefs.edit().putString("language", language).apply()
    }

    fun hasSeenIntro(): Boolean = prefs.getBoolean("welcome_intro_seen", false)
    fun markIntroSeen() { prefs.edit().putBoolean("welcome_intro_seen", true).apply() }
    fun isCrtSoundEnabled(): Boolean = prefs.getBoolean("crt_sound", false)
    fun setCrtSoundEnabled(value: Boolean) { prefs.edit().putBoolean("crt_sound", value).apply() }

    companion object {
        const val ASPECT_FIT = 0
        const val ASPECT_FILL = 1
        const val ASPECT_16_9 = 2
        const val ASPECT_4_3 = 3
    }
}
