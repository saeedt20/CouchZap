package com.couchzap.iptv.data

data class Channel(
    val number: Int,
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val tvgId: String? = null,
    val mimeType: String? = null
) {
    // List positions and expiring stream URLs must not identify a favourite.
    val stableId: String get() = tvgId?.takeIf { it.isNotBlank() }?.let { "tvg:$it" }
        ?: "name:${name.trim().lowercase(java.util.Locale.ROOT)}|${group.orEmpty().trim().lowercase(java.util.Locale.ROOT)}"
}
