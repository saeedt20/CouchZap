package com.couchzap.iptv.util

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import com.couchzap.iptv.R
import java.util.Locale

object AppLanguage {
    const val DEFAULT = "en"
    data class Language(val code: String, val name: String)
    val supported = listOf(
        Language("en", "English"), Language("es", "Español"),
        Language("hi", "हिन्दी"), Language("th", "ไทย"),
        Language("fa", "فارسی"), Language("ar", "العربية")
    )
    fun isSupported(language: String): Boolean = supported.any { it.code == language }
    fun isRtl(language: String?): Boolean = language == "fa" || language == "ar"

    fun wrap(context: Context): Context {
        val locale = Locale(Prefs(context).getLanguage() ?: DEFAULT)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return context.createConfigurationContext(config)
    }
    @Suppress("DEPRECATION")
    fun apply(context: Context, root: View, language: String) {
        val locale = Locale(language)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        root.layoutDirection = if (isRtl(language)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        translate(context, root)
    }
    private fun label(root: View, id: Int, text: CharSequence) {
        when(val view=root.findViewById<View>(id)) {
            is com.couchzap.iptv.ui.SettingRow -> view.setLabel(text)
            is TextView -> view.text=text
        }
    }
    fun translate(context: Context, root: View) {
        root.findViewById<TextView>(R.id.tv_settings_crt_label)?.text = context.getString(R.string.crt_sound)
        root.findViewById<View>(R.id.btn_settings_crt_row)?.contentDescription = context.getString(R.string.crt_sound)
        root.findViewById<TextView>(R.id.import_eyebrow)?.text = context.getString(R.string.welcome_eyebrow)
        root.findViewById<View>(R.id.btn_import_language)?.contentDescription = context.getString(R.string.language)
        root.findViewById<TextView>(R.id.btn_import_language)?.text = supported.firstOrNull { it.code == (Prefs(context).getLanguage() ?: DEFAULT) }?.name ?: "English"
        label(root, R.id.btn_settings_effects, context.getString(R.string.cz_effects))
        root.findViewById<TextView>(R.id.tv_settings_language_value)?.text = supported.firstOrNull { it.code == (Prefs(context).getLanguage() ?: DEFAULT) }?.name ?: "English"

        listOf(R.id.btn_osd_channels to R.string.channels, R.id.btn_osd_prev to R.string.previous_channel,
            R.id.btn_osd_play_pause to R.string.pause, R.id.btn_osd_next to R.string.next_channel,
            R.id.btn_osd_aspect to R.string.choose_aspect, R.id.btn_osd_settings to R.string.settings,
            R.id.btn_quick_settings to R.string.settings).forEach { (id, text) -> root.findViewById<View>(id)?.contentDescription = context.getString(text) }

        root.findViewById<View>(R.id.localized_label_1)?.contentDescription = context.getString(R.string.app_name)
        root.findViewById<TextView>(R.id.localized_label_2)?.text = context.getString(R.string.app_name)
        root.findViewById<TextView>(R.id.tv_import_title)?.text = context.getString(R.string.import_title)
        root.findViewById<TextView>(R.id.tv_import_hint)?.text = context.getString(R.string.import_hint)
        label(root, R.id.btn_import, context.getString(R.string.btn_import_playlist))
        root.findViewById<TextView>(R.id.localized_label_3)?.text = context.getString(R.string.playlist_formats)
        label(root, R.id.btn_public, context.getString(R.string.public_channels))
        root.findViewById<TextView>(R.id.localized_label_5)?.text = context.getString(R.string.public_credit)
        root.findViewById<TextView>(R.id.tv_import_remote)?.text = context.getString(R.string.remote_hint)
        root.findViewById<View>(R.id.settings_back)?.contentDescription = context.getString(R.string.back)
        root.findViewById<TextView>(R.id.settings_title)?.text = context.getString(R.string.settings)
        root.findViewById<View>(R.id.btn_settings_close)?.contentDescription = context.getString(R.string.close)
        root.findViewById<TextView>(R.id.localized_label_6)?.text = context.getString(R.string.playback_section)
        root.findViewById<TextView>(R.id.localized_label_7)?.text = context.getString(R.string.choose_aspect)
        root.findViewById<TextView>(R.id.tv_settings_aspect_value)?.text = context.getString(R.string.aspect_fit)
        label(root, R.id.btn_settings_tracks, context.getString(R.string.audio_subtitles))
        root.findViewById<TextView>(R.id.localized_label_8)?.text = context.getString(R.string.sleep_timer)
        root.findViewById<TextView>(R.id.tv_settings_sleep_value)?.text = context.getString(R.string.off)
        root.findViewById<TextView>(R.id.localized_label_9)?.text = context.getString(R.string.playlist_section)
        label(root, R.id.btn_settings_reimport, context.getString(R.string.playlist_sources))
        root.findViewById<TextView>(R.id.tv_settings_source)?.text = context.getString(R.string.playlist_not_added)
        label(root, R.id.btn_settings_guide, context.getString(R.string.programme_guide))
        root.findViewById<TextView>(R.id.localized_label_10)?.text = context.getString(R.string.app_section)
        root.findViewById<TextView>(R.id.localized_label_11)?.text = context.getString(R.string.interface_mode)
        root.findViewById<TextView>(R.id.tv_settings_mode_value)?.text = context.getString(R.string.automatic)
        label(root, R.id.btn_settings_language, context.getString(R.string.language))
        label(root, R.id.btn_settings_update, context.getString(R.string.check_updates))
        label(root, R.id.btn_settings_logs, context.getString(R.string.diagnostic_log))
        label(root, R.id.btn_settings_about, context.getString(R.string.about))
        label(root, R.id.btn_settings_exit, context.getString(R.string.settings_exit))
        root.findViewById<TextView>(R.id.tv_playback_error)?.text = context.getString(R.string.channel_unavailable)
        root.findViewById<TextView>(R.id.btn_retry)?.text = context.getString(R.string.retry)
        root.findViewById<TextView>(R.id.btn_error_next)?.text = context.getString(R.string.next_channel)
        root.findViewById<TextView>(R.id.localized_label_12)?.text = context.getString(R.string.app_name)
        root.findViewById<TextView>(R.id.tv_channel_list_header)?.text = context.getString(R.string.channels)
        root.findViewById<View>(R.id.btn_close_channel_list)?.contentDescription = context.getString(R.string.close)
        root.findViewById<TextView>(R.id.et_channel_search)?.hint = context.getString(R.string.search_channels)
        root.findViewById<TextView>(R.id.btn_filter_all)?.text = context.getString(R.string.all_channels)
        root.findViewById<TextView>(R.id.btn_filter_favourites)?.text = context.getString(R.string.favourites)
        root.findViewById<TextView>(R.id.btn_filter_groups)?.text = context.getString(R.string.groups)
        root.findViewById<TextView>(R.id.tv_empty_channels)?.text = context.getString(R.string.no_matches)
        root.findViewById<TextView>(R.id.localized_label_13)?.text = context.getString(R.string.cz_channel_hint)
        root.findViewById<View>(R.id.btn_osd_favourite)?.let {
            it.contentDescription = context.getString(if (it.isSelected) R.string.favourite_remove_action else R.string.favourite_add_action)
        }
        root.findViewById<TextView>(R.id.localized_label_14)?.text = context.getString(R.string.buffering)
        root.findViewById<TextView>(R.id.tv_osd_action_label)?.text = context.getString(R.string.channels)
        root.findViewById<TextView>(R.id.localized_label_15)?.text = context.getString(R.string.details_hint)
        root.findViewById<TextView>(R.id.btn_osd_last_channel)?.text = context.getString(R.string.previous_watched)
    }
}
