package com.couchzap.iptv.data

import org.json.JSONArray
import java.util.Locale

object PublicCatalog {
    const val FREE_TV_COUNTRIES = "https://api.github.com/repos/Free-TV/IPTV/contents/playlists?ref=master"
    data class Entry(val label: String, val code: String)
    fun url(provider: String, kind: String): String {
        require(provider in setOf("iptv-org", "free-tv"))
        if (provider == "free-tv") { require(kind in setOf("countries", "categories")); return FREE_TV_COUNTRIES }
        require(kind in setOf("countries", "languages", "categories"))
        return "https://iptv-org.github.io/api/$kind.json"
    }
    fun parse(provider: String, kind: String, json: JSONArray): List<Entry> {
        url(provider, kind) // Same validation for downloaded and fixture content.
        return (0 until json.length()).mapNotNull { i ->
            val item = json.optJSONObject(i) ?: return@mapNotNull null
            if (provider == "free-tv") {
                val name = item.optString("name")
                if (item.optString("type") != "file" || !name.matches(Regex("playlist_[a-z0-9_-]+\\.m3u8")) || item.optString("path") != "playlists/$name") null
                else if ((name.startsWith("playlist_zz_") || name.endsWith("_vod.m3u8")) != (kind == "categories")) null
                else Entry(name.removePrefix("playlist_zz_").removePrefix("playlist_").removeSuffix(".m3u8").split('_').joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.ENGLISH) } }, name)
            } else {
                val code = item.optString(if (kind == "categories") "id" else "code")
                val name = item.optString("name").trim()
                if (!code.matches(Regex("[a-zA-Z0-9_-]+")) || name.isEmpty()) null else Entry(name, code)
            }
        }.distinctBy { it.code }.sortedWith(compareBy({ it.label.lowercase(Locale.ROOT) }, { it.code }))
    }
}
