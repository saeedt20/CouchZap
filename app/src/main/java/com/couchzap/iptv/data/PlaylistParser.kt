package com.couchzap.iptv.data

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class PlaylistParser {
    var epgUrls: List<String> = emptyList()
        private set
    val epgUrl: String? get() = epgUrls.firstOrNull()
    val drmUrls = mutableSetOf<String>()

    fun parse(inputStream: InputStream, checkActive: () -> Unit = {}): List<Channel> {
        epgUrls = emptyList(); drmUrls.clear()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val channels = mutableListOf<Channel>()
        val seenUrls = HashSet<String>()

        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingId: String? = null
        var pendingMime: String? = null
        var pendingUserAgent: String? = null
        var pendingReferer: String? = null

        var pendingDrm = false
        var currentGroupHeader: String? = null
        var lineCount = 0

        var line: String? = reader.readLine()
        while (line != null) {
            checkActive()
            lineCount++
            // Strip BOM if on first line
            var cleanLine = if (lineCount == 1 && line!!.startsWith("\uFEFF")) {
                line!!.substring(1)
            } else {
                line!!
            }.trim()

            if (cleanLine.isEmpty()) {
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#EXTM3U")) {
                epgUrls = (extractAttribute(cleanLine, "x-tvg-url") ?: extractAttribute(cleanLine, "url-tvg"))
                    ?.split(Regex("[,\\s]+(?=(?:https?|file|content)://)", RegexOption.IGNORE_CASE))?.map(String::trim)?.filter { url -> HttpStreams.validUrl(url) || runCatching {
                        val uri = java.net.URI(url); uri.scheme in listOf("file", "content") && !uri.path.isNullOrBlank()
                    }.getOrDefault(false) }?.distinct().orEmpty()
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#EXTINF:")) {
                val parsedInf = parseExtInf(cleanLine)
                pendingDrm = false; pendingUserAgent = null; pendingReferer = null
                pendingName = parsedInf.name
                pendingGroup = parsedInf.group
                pendingLogo = parsedInf.logo
                pendingId = extractAttribute(cleanLine.substringBeforeLast(','), "tvg-id")
                pendingMime = null
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#EXTVLCOPT:")) {
                val opt = cleanLine.substring(11).trim()
                if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                    pendingUserAgent = opt.substring(16).trim()
                } else if (opt.startsWith("http-referrer=", ignoreCase = true)) {
                    pendingReferer = opt.substring(14).trim()
                }
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#KODIPROP:inputstream.adaptive.manifest_type=")) {
                pendingMime = when (cleanLine.substringAfter('=').trim().lowercase()) {
                    "hls" -> "application/x-mpegURL"
                    "mpd", "dash" -> "application/dash+xml"
                    else -> null
                }
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#KODIPROP:inputstream.adaptive.license_", ignoreCase = true)) {
                pendingDrm = true; line = reader.readLine(); continue
            }

            if (cleanLine.startsWith("#EXTHTTP:")) {
                val json = cleanLine.substring(9).trim()
                val ua = extractJsonValue(json, "User-Agent") ?: extractJsonValue(json, "user-agent")
                val ref = extractJsonValue(json, "Referer") ?: extractJsonValue(json, "referer")
                if (ua != null) pendingUserAgent = ua
                if (ref != null) pendingReferer = ref
                line = reader.readLine()
                continue
            }

            if (cleanLine.startsWith("#")) {
                // Ignore other comments
                line = reader.readLine()
                continue
            }

            // Plain TXT format check or M3U URL line
            var url = cleanLine
            var userAgent = pendingUserAgent
            var referer = pendingReferer
            var channelName = pendingName
            var group = pendingGroup ?: currentGroupHeader
            var logo = pendingLogo

            // Check for Chinese TXT group header: "Group Name,#genre#"
            if (cleanLine.contains(",#genre#") || cleanLine.contains("|#genre#")) {
                val gName = cleanLine.replace(",#genre#", "").replace("|#genre#", "").trim()
                if (gName.isNotEmpty()) {
                    currentGroupHeader = gName
                }
                // Reset pending for next
                pendingName = null
                pendingGroup = null
                pendingLogo = null
                pendingId = null
                pendingMime = null
                pendingUserAgent = null
                pendingReferer = null
                line = reader.readLine()
                continue
            }

            // Parse TXT names before processing request headers on the URL.
            if (channelName == null && !isValidUrl(url)) {
                parseTxtLine(cleanLine)?.let { (name, streamUrl) ->
                    channelName = name
                    url = streamUrl
                }
            }
            val pipeIdx = url.indexOf('|')
            if (pipeIdx > 0 && isValidUrl(url)) {
                val suffix = url.substring(pipeIdx + 1)
                url = url.substring(0, pipeIdx).trim()
                for (param in suffix.split("&")) {
                    val kv = param.split("=", limit = 2)
                    if (kv.size != 2) continue
                    val key = kv[0].trim()
                    val value = try {
                        java.net.URLDecoder.decode(kv[1].trim(), "UTF-8")
                    } catch (_: IllegalArgumentException) { kv[1].trim() }
                    if (key.equals("User-Agent", ignoreCase = true)) userAgent = value
                    if (key.equals("Referer", ignoreCase = true)) referer = value
                }
            }

            // If still no channel name but line looks like a valid URL
            if (channelName == null && isValidUrl(url)) {
                channelName = "Channel ${channels.size + 1}"
            }

            if (channelName != null && isValidUrl(url)) {
                if (!seenUrls.contains(url)) {
                    seenUrls.add(url)
                    if (pendingDrm) drmUrls.add(url)
                    val channelNumber = channels.size + 1
                    channels.add(
                        Channel(
                            number = channelNumber,
                            name = requireNotNull(channelName),
                            url = url,
                            group = group?.takeIf { it.isNotBlank() },
                            logo = logo?.takeIf { it.isNotBlank() },
                            userAgent = userAgent?.takeIf { it.isNotBlank() },
                            referer = referer?.takeIf { it.isNotBlank() },
                            tvgId = pendingId?.takeIf { it.isNotBlank() },
                            mimeType = pendingMime
                        )
                    )
                }
            }

            // Reset pending channel metadata for next line
            pendingName = null
            pendingGroup = null
            pendingLogo = null
            pendingId = null
            pendingMime = null
            pendingUserAgent = null
            pendingReferer = null

            line = reader.readLine()
        }

        return channels
    }

    private data class ParsedInf(val name: String, val group: String?, val logo: String?)

    private fun parseExtInf(line: String): ParsedInf {
        var quote: Char? = null
        val commaIdx = line.indexOfFirst { char ->
            when {
                quote != null -> { if (char == quote) quote = null; false }
                char == '\"' || char == '\'' -> { quote = char; false }
                else -> char == ','
            }
        }
        val name = if (commaIdx != -1 && commaIdx < line.length - 1) {
            line.substring(commaIdx + 1).trim()
        } else {
            "Channel"
        }

        val metadata = if (commaIdx != -1) line.substring(0, commaIdx) else line
        val logo = extractAttribute(metadata, "tvg-logo")
        val group = extractAttribute(metadata, "group-title")

        return ParsedInf(name = name, group = group?.takeIf { it.isNotBlank() }, logo = logo)
    }

    private fun extractAttribute(text: String, key: String): String? {
        val search = "$key="
        val idx = text.indexOf(search, ignoreCase = true)
        if (idx == -1) return null

        val start = idx + search.length
        if (start >= text.length) return null

        val quoteChar = text[start]
        if (quoteChar == '"' || quoteChar == '\'') {
            val end = text.indexOf(quoteChar, start + 1)
            if (end != -1) {
                return text.substring(start + 1, end).trim()
            }
        } else {
            val spaceEnd = text.indexOf(' ', start)
            val end = if (spaceEnd != -1) spaceEnd else text.length
            return text.substring(start, end).trim()
        }
        return null
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val search = "\"$key\""
        val idx = json.indexOf(search, ignoreCase = true)
        if (idx == -1) return null
        val colonIdx = json.indexOf(':', idx)
        if (colonIdx == -1) return null
        val startQuote = json.indexOf('"', colonIdx)
        if (startQuote == -1) return null
        val endQuote = json.indexOf('"', startQuote + 1)
        if (endQuote == -1) return null
        return json.substring(startQuote + 1, endQuote)
    }

    private fun parseTxtLine(line: String): Pair<String, String>? {
        if (line.contains(",")) {
            val commaIdx = line.indexOf(',')
            val name = line.substring(0, commaIdx).trim()
            val url = line.substring(commaIdx + 1).trim()
            if (name.isNotEmpty() && isValidUrl(url)) return Pair(name, url)
        }
        if (line.contains("|")) {
            val pipeIdx = line.indexOf('|')
            val name = line.substring(0, pipeIdx).trim()
            val url = line.substring(pipeIdx + 1).trim()
            if (name.isNotEmpty() && isValidUrl(url)) return Pair(name, url)
        }
        if (line.contains(" http://") || line.contains(" https://") || line.contains(" rtsp://") || line.contains(" udp://")) {
            val spaceIdx = line.lastIndexOf(" ")
            if (spaceIdx > 0) {
                val name = line.substring(0, spaceIdx).trim()
                val url = line.substring(spaceIdx + 1).trim()
                if (name.isNotEmpty() && isValidUrl(url)) return Pair(name, url)
            }
        }
        return null
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url.substringBefore('|').trim())
            when (uri.scheme?.lowercase(java.util.Locale.ROOT)) {
                "http", "https", "rtsp", "udp" -> !uri.host.isNullOrBlank()
                "file" -> !uri.path.isNullOrBlank()
                else -> false
            }
        } catch (_: java.net.URISyntaxException) { false }
    }
}
