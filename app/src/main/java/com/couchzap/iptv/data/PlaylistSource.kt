package com.couchzap.iptv.data

import org.json.JSONObject

data class PlaylistSource(val kind: String, val location: String, val label: String) {
    fun toJson() = JSONObject().put("kind", kind).put("location", location).put("label", label)
    companion object {
        fun fromJson(json: JSONObject) = PlaylistSource(json.getString("kind"), json.getString("location"), json.getString("label"))
        fun public(kind: String = "all", code: String = "", label: String = "All channels"): PlaylistSource {
            require(kind in setOf("all", "countries", "languages", "categories"))
            require(kind == "all" || code.matches(Regex("[a-zA-Z0-9_-]+")))
            val path = if (kind == "all") "index.category.m3u" else "$kind/${code.lowercase(java.util.Locale.ROOT)}.m3u"
            return PlaylistSource("public", "https://iptv-org.github.io/iptv/$path", "iptv-org · $label")
        }
    }
}
