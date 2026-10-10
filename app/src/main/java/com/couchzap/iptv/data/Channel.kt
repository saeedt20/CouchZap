package com.couchzap.iptv.data

data class Channel(
    val number: Int,
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val userAgent: String? = null,
    val referer: String? = null
)
