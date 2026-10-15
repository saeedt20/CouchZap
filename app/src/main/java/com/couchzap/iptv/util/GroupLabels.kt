package com.couchzap.iptv.util

import android.content.Context
import com.couchzap.iptv.R
import java.util.Locale

/** Localized presentation only; playlist group identities remain unchanged. */
object GroupLabels {
    private val categories = mapOf(
        "animation" to R.string.group_animation, "auto" to R.string.group_auto,
        "business" to R.string.group_business, "classic" to R.string.group_classic,
        "comedy" to R.string.group_comedy, "cooking" to R.string.group_cooking,
        "culture" to R.string.group_culture, "documentary" to R.string.group_documentary,
        "education" to R.string.group_education, "entertainment" to R.string.group_entertainment,
        "family" to R.string.group_family, "general" to R.string.group_general,
        "interactive" to R.string.group_interactive, "kids" to R.string.group_kids,
        "legislative" to R.string.group_legislative, "lifestyle" to R.string.group_lifestyle,
        "movies" to R.string.group_movies, "music" to R.string.group_music,
        "news" to R.string.group_news, "outdoor" to R.string.group_outdoor,
        "public" to R.string.group_public, "relax" to R.string.group_relax, "religious" to R.string.group_religious,
        "science" to R.string.group_science, "series" to R.string.group_series,
        "shop" to R.string.group_shop, "sports" to R.string.group_sports,
        "travel" to R.string.group_travel, "weather" to R.string.group_weather,
        "xxx" to R.string.group_adult, "undefined" to R.string.group_undefined,
        "other" to R.string.other_group
    )

    fun display(context: Context, group: String?): String {
        if (group.isNullOrBlank()) return context.getString(R.string.other_group)
        if (context.resources.configuration.locales[0].language != "fa") return group
        return categories[group.trim().lowercase(Locale.ROOT)]?.let(context::getString) ?: group
    }
}
