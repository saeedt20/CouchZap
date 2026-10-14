package com.couchzap.iptv.data

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Stable across interface languages: numbers, Latin, Persian/Arabic, other scripts, empty. */
object PublicChannelOrder {
    private fun name(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
        .replace(Regex("\\s+"), " ").map { c -> if (c.isDigit()) Character.digit(c, 10).digitToChar() else c }.joinToString("")
        .lowercase(Locale.ROOT)
    private fun group(value: String): Int = when {
        value.isEmpty() -> 4
        value.first().isDigit() -> 0
        Character.UnicodeScript.of(value.first().code) == Character.UnicodeScript.LATIN -> 1
        Character.UnicodeScript.of(value.first().code) == Character.UnicodeScript.ARABIC -> 2
        else -> 3
    }
    fun sorted(channels: List<Channel>): List<Channel> {
        val english = Collator.getInstance(Locale.ENGLISH).apply { strength = Collator.SECONDARY }
        val persian = Collator.getInstance(Locale("fa")).apply { strength = Collator.SECONDARY }
        val tokens = Regex("[0-9]+|[^0-9]+")
        fun compare(a: String, b: String, collator: Collator): Int {
            val left = tokens.findAll(a).map { it.value }.toList(); val right = tokens.findAll(b).map { it.value }.toList()
            for (i in 0 until minOf(left.size, right.size)) {
                val x = left[i]; val y = right[i]
                val result = if (x.first().isDigit() && y.first().isDigit()) {
                    val nx = x.trimStart('0').ifEmpty { "0" }; val ny = y.trimStart('0').ifEmpty { "0" }
                    nx.length.compareTo(ny.length).takeIf { it != 0 } ?: nx.compareTo(ny)
                } else collator.compare(x, y)
                if (result != 0) return result
            }
            return left.size.compareTo(right.size)
        }
        val names = channels.associateWith { name(it.name) }
        return channels.sortedWith { a, b ->
            val x = names.getValue(a); val y = names.getValue(b); val g = group(x)
            group(x).compareTo(group(y)).takeIf { it != 0 }
                ?: compare(x, y, if (g == 2) persian else english).takeIf { it != 0 }
                ?: a.stableId.compareTo(b.stableId).takeIf { it != 0 }
                ?: a.url.compareTo(b.url).takeIf { it != 0 }
                ?: a.number.compareTo(b.number)
        }
    }
    fun isWebPage(channel: Channel): Boolean = runCatching {
        val host = java.net.URI(channel.url).host?.lowercase(Locale.ROOT).orEmpty()
        listOf("youtube.com", "youtu.be", "dailymotion.com", "dai.ly", "vimeo.com").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
}
