package com.couchzap.iptv.player

import com.couchzap.iptv.data.Channel
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

object StreamTypeResolver {
    fun known(channel: Channel): String? {
        channel.mimeType?.let { return it }
        val path = runCatching { URI(channel.url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            else -> null
        }
    }
    fun needsProbe(channel: Channel): Boolean {
        if (known(channel) != null || !channel.url.startsWith("http", true)) return false
        val path = runCatching { URI(channel.url).path.orEmpty().lowercase(Locale.ROOT) }.getOrDefault("")
        return listOf(".ts", ".mp4", ".mkv", ".webm", ".mp3", ".aac", ".flac", ".wav").none(path::endsWith)
    }
    fun resolve(channel: Channel): String? {
        known(channel)?.let { return it }
        if (!needsProbe(channel)) return null
        val connection = URL(channel.url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 1500; connection.readTimeout = 1500
        connection.setRequestProperty("User-Agent", channel.userAgent ?: "CouchZap/2.0")
        channel.referer?.let { connection.setRequestProperty("Referer", it) }
        return try {
            if (connection.responseCode !in 200..299) null else when (connection.contentType?.substringBefore(';')?.lowercase(Locale.ROOT)) {
                "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> "application/x-mpegURL"
                "application/dash+xml" -> "application/dash+xml"
                else -> null
            }
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }
}
