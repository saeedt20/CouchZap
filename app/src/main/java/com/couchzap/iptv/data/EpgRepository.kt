package com.couchzap.iptv.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

data class Programme(val channelId: String, val start: Long, val stop: Long, val title: String, val description: String = "")

class EpgRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "epg.json"))
    private val prefs = context.getSharedPreferences("couchzap_prefs", Context.MODE_PRIVATE)
    @Volatile private var programmes: Map<String, List<Programme>> = emptyMap()
    fun sourceUrl(): String? = prefs.getString("epg_source", null)
    fun updatedAt(): Long = prefs.getLong("epg_updated_at", 0)
    fun nowNext(channel: Channel, now: Long = System.currentTimeMillis()): Pair<Programme?, Programme?> {
        val items = programmes[channel.tvgId] ?: return null to null
        return items.firstOrNull { now >= it.start && now < it.stop } to items.firstOrNull { it.start > now }
    }
    fun load() {
        if (!file.baseFile.exists()) return
        runCatching {
            val array = file.openRead().bufferedReader().use { JSONArray(it.readText()) }
            val items = (0 until array.length()).map { i ->
                val p = array.getJSONObject(i)
                Programme(p.getString("id"), p.getLong("start"), p.getLong("stop"), p.getString("title"), p.optString("description"))
            }
            programmes = items.groupBy { it.channelId }.mapValues { (_, values) -> values.sortedBy { it.start } }
        }
    }
    fun import(input: InputStream, channelIds: Set<String>, url: String? = null, now: Long = System.currentTimeMillis()): Int {
        val items = input.use { parse(it, channelIds, now) }
        require(items.isNotEmpty()) { "No matching programmes found. This guide must use the playlist's tvg-id values." }
        val array = JSONArray()
        items.forEach { p -> array.put(JSONObject().put("id", p.channelId).put("start", p.start).put("stop", p.stop).put("title", p.title).put("description", p.description)) }
        val output = file.startWrite()
        try { output.write(array.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        programmes = items.groupBy { it.channelId }.mapValues { (_, values) -> values.sortedBy { it.start } }
        prefs.edit().putLong("epg_updated_at", now).putString("epg_source", url).apply()
        return items.size
    }
    fun clear() { programmes = emptyMap(); file.delete(); prefs.edit().remove("epg_source").remove("epg_updated_at").apply() }

    companion object {
        fun parse(input: InputStream, channelIds: Set<String>, now: Long): List<Programme> {
            val peek = PushbackInputStream(input, 2)
            val first = peek.read(); val second = peek.read()
            if (second >= 0) peek.unread(second)
            if (first >= 0) peek.unread(first)
            val decoded = if (first == 0x1f && second == 0x8b) GZIPInputStream(peek) else peek
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(LimitedInputStream(decoded, 64L * 1024 * 1024), null)
            val items = mutableListOf<Programme>()
            var id = ""; var start = 0L; var stop = 0L; var title = ""; var description = ""; var field = ""; var keep = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.DOCDECL -> throw IllegalArgumentException("XMLTV documents with a DTD are not supported")
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "programme" -> {
                            id = parser.getAttributeValue(null, "channel").orEmpty()
                            start = timestamp(parser.getAttributeValue(null, "start")); stop = timestamp(parser.getAttributeValue(null, "stop"))
                            title = ""; description = ""; field = ""
                            keep = id in channelIds && stop > now - 3600000L && start < now + 48 * 3600000L && stop > start
                        }
                        "title", "desc" -> if (keep) field = parser.name
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (keep) when (field) {
                        "title" -> if (title.length < 500) title += parser.text
                        "desc" -> if (description.length < 1500) description += parser.text
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "title", "desc" -> field = ""
                        "programme" -> {
                            if (keep && title.isNotBlank()) items.add(Programme(id, start, stop, title.trim(), description.trim()))
                            require(items.size <= 150000) { "Too many programmes in this guide" }
                            keep = false
                        }
                    }
                }
                event = parser.nextToken()
            }
            return items
        }
        fun timestamp(value: String?): Long {
            if (value.isNullOrBlank()) return 0L
            val parts = value.trim().split(Regex("\\s+"), limit = 2)
            val date = parts[0]
            val pattern = when (date.length) { 14 -> "yyyyMMddHHmmss"; 12 -> "yyyyMMddHHmm"; else -> return 0L }
            val text = if (parts.size > 1) "$date ${parts[1]}" else date
            return runCatching {
                SimpleDateFormat(pattern + if (parts.size > 1) " Z" else "", Locale.US).apply {
                    isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
                }.parse(text)?.time ?: 0L
            }.getOrDefault(0L)
        }
    }
}
