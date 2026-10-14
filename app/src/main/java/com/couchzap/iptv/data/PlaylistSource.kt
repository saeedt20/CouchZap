package com.couchzap.iptv.data

import org.json.JSONObject

data class PlaylistSource(val kind: String, val location: String, val label: String,
    val provider: String? = null, val scope: String? = null, val code: String? = null) {
    val publicProvider: String? get() = provider ?: if (kind == "public") {
        when {
            location.startsWith("https://iptv-org.github.io/iptv/") -> "iptv-org"
            location.startsWith("https://raw.githubusercontent.com/Free-TV/IPTV/master/") -> "free-tv"
            else -> null
        }
    } else null
    fun toJson() = JSONObject().put("kind", kind).put("location", location).put("label", label)
        .put("provider", provider).put("scope", scope).put("code", code)
    companion object {
        fun fromJson(json: JSONObject) = PlaylistSource(json.getString("kind"), json.getString("location"), json.getString("label"),
            json.optString("provider").takeIf { it.isNotBlank() }, json.optString("scope").takeIf { it.isNotBlank() }, json.optString("code").takeIf { it.isNotBlank() })
        fun public(kind: String = "all", code: String = "", label: String = "All channels"): PlaylistSource {
            require(kind in setOf("all", "countries", "languages", "categories"))
            require(kind == "all" || code.matches(Regex("[a-zA-Z0-9_-]+")))
            val path = if (kind == "all") "index.category.m3u" else "$kind/${code.lowercase(java.util.Locale.ROOT)}.m3u"
            return PlaylistSource("public", "https://iptv-org.github.io/iptv/$path", "iptv-org · $label", "iptv-org", kind, code.takeIf { it.isNotBlank() })
        }
        fun freeTv(fileName: String? = null, label: String = "All channels"): PlaylistSource {
            require(fileName == null || fileName.matches(Regex("playlist_[a-z0-9_-]+\\.m3u8")))
            val path = if (fileName == null) "playlist.m3u8" else "playlists/$fileName"
            return PlaylistSource("public", "https://raw.githubusercontent.com/Free-TV/IPTV/master/$path", "Free-TV · $label", "free-tv", if (fileName == null) "all" else "countries", fileName)
        }
    }
}
