package com.couchzap.iptv.util

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.widget.TextView
import com.couchzap.iptv.R
import java.util.Locale

object AppLanguage {
    fun wrap(context: Context): Context {
        val locale = Locale(Prefs(context).getLanguage() ?: "en")
        val config = Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        return context.createConfigurationContext(config)
    }
    @Suppress("DEPRECATION")
    fun apply(context: Context, root: View, language: String) {
        val locale = Locale(language)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale); setLayoutDirection(locale) }
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        root.layoutDirection = if (language == "fa") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        translate(context, root)
    }
    fun translate(context: Context, root: View) {
        root.findViewById<TextView>(R.id.btn_settings_crt)?.text = context.getString(R.string.crt_sound)
        root.findViewById<TextView>(R.id.import_eyebrow)?.text = context.getString(R.string.welcome_eyebrow)
        root.findViewById<TextView>(R.id.own_playlist_label)?.text = context.getString(R.string.own_playlist_label)
        root.findViewById<View>(R.id.btn_import_language)?.contentDescription = context.getString(R.string.language)

        listOf(R.id.btn_osd_channels to R.string.channels, R.id.btn_osd_prev to R.string.previous_channel,
            R.id.btn_osd_play_pause to R.string.pause, R.id.btn_osd_next to R.string.next_channel,
            R.id.btn_osd_aspect to R.string.choose_aspect, R.id.btn_osd_settings to R.string.settings,
            R.id.btn_quick_settings to R.string.settings).forEach { (id, text) -> root.findViewById<View>(id)?.contentDescription = context.getString(text) }

        root.findViewById<View>(R.id.localized_label_1)?.contentDescription = context.getString(R.string.app_name)
        root.findViewById<TextView>(R.id.localized_label_2)?.text = context.getString(R.string.app_name)
        root.findViewById<TextView>(R.id.tv_import_title)?.text = context.getString(R.string.import_title)
        root.findViewById<TextView>(R.id.tv_import_hint)?.text = context.getString(R.string.import_hint)
        root.findViewById<TextView>(R.id.btn_import)?.text = context.getString(R.string.btn_import_playlist)
        root.findViewById<TextView>(R.id.localized_label_3)?.text = context.getString(R.string.playlist_formats)
        root.findViewById<TextView>(R.id.localized_label_4)?.text = context.getString(R.string.no_playlist)
        root.findViewById<TextView>(R.id.btn_public)?.text = context.getString(R.string.public_channels)
        root.findViewById<TextView>(R.id.localized_label_5)?.text = context.getString(R.string.public_credit)
        root.findViewById<TextView>(R.id.tv_import_remote)?.text = context.getString(R.string.remote_hint)
        root.findViewById<View>(R.id.settings_back)?.contentDescription = context.getString(R.string.back)
        root.findViewById<TextView>(R.id.settings_title)?.text = context.getString(R.string.settings)
        root.findViewById<View>(R.id.btn_settings_close)?.contentDescription = context.getString(R.string.close)
        root.findViewById<TextView>(R.id.localized_label_6)?.text = context.getString(R.string.playback_section)
        root.findViewById<TextView>(R.id.localized_label_7)?.text = context.getString(R.string.choose_aspect)
        root.findViewById<TextView>(R.id.tv_settings_aspect_value)?.text = context.getString(R.string.aspect_fit)
        root.findViewById<TextView>(R.id.btn_settings_tracks)?.text = context.getString(R.string.audio_subtitles)
        root.findViewById<TextView>(R.id.localized_label_8)?.text = context.getString(R.string.sleep_timer)
        root.findViewById<TextView>(R.id.tv_settings_sleep_value)?.text = context.getString(R.string.off)
        root.findViewById<TextView>(R.id.localized_label_9)?.text = context.getString(R.string.playlist_section)
        root.findViewById<TextView>(R.id.btn_settings_reimport)?.text = context.getString(R.string.playlist_sources)
        root.findViewById<TextView>(R.id.tv_settings_source)?.text = context.getString(R.string.playlist_not_added)
        root.findViewById<TextView>(R.id.btn_settings_guide)?.text = context.getString(R.string.programme_guide)
        root.findViewById<TextView>(R.id.localized_label_10)?.text = context.getString(R.string.app_section)
        root.findViewById<TextView>(R.id.localized_label_11)?.text = context.getString(R.string.interface_mode)
        root.findViewById<TextView>(R.id.tv_settings_mode_value)?.text = context.getString(R.string.automatic)
        root.findViewById<TextView>(R.id.btn_settings_language)?.text = context.getString(R.string.language)
        root.findViewById<TextView>(R.id.btn_settings_update)?.text = context.getString(R.string.check_updates)
        root.findViewById<TextView>(R.id.btn_settings_logs)?.text = context.getString(R.string.diagnostic_log)
        root.findViewById<TextView>(R.id.btn_settings_about)?.text = context.getString(R.string.about)
        root.findViewById<TextView>(R.id.btn_settings_exit)?.text = context.getString(R.string.settings_exit)
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
        root.findViewById<TextView>(R.id.localized_label_13)?.text = context.getString(R.string.favourite_hint)
        root.findViewById<View>(R.id.btn_osd_favourite)?.contentDescription = context.getString(R.string.favourites)
        root.findViewById<TextView>(R.id.localized_label_14)?.text = context.getString(R.string.buffering)
        root.findViewById<TextView>(R.id.tv_osd_action_label)?.text = context.getString(R.string.channels)
        root.findViewById<TextView>(R.id.localized_label_15)?.text = context.getString(R.string.details_hint)
        root.findViewById<TextView>(R.id.btn_osd_last_channel)?.text = context.getString(R.string.previous_watched)
    }
}
