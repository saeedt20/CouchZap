package com.couchzap.iptv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader

class PlaylistRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("couchzap_prefs", Context.MODE_PRIVATE)

    private val playlistSrcFile: File
        get() = File(context.filesDir, "playlist.src")

    private val channelsTsvFile: File
        get() = File(context.filesDir, "channels.tsv")

    fun isPlaylistReady(): Boolean {
        return prefs.getBoolean("playlist_ready", false) && channelsTsvFile.exists()
    }

    fun getLastChannelIndex(): Int {
        return prefs.getInt("last_channel_index", 0)
    }

    fun saveLastChannelIndex(index: Int, channel: Channel? = null) {
        prefs.edit().putInt("last_channel_index", index).apply {
            if (channel != null) putString("last_channel_id", channel.stableId)
        }.apply()
    }

    fun favouriteIds(): Set<String> = prefs.getStringSet("favourite_ids", emptySet())!!.toSet()
    fun isFavourite(channel: Channel): Boolean = channel.stableId in favouriteIds()
    fun toggleFavourite(channel: Channel): Boolean {
        val ids = favouriteIds().toMutableSet()
        val added = if (channel.stableId in ids) { ids.remove(channel.stableId); false }
            else { ids.add(channel.stableId); true }
        prefs.edit().putStringSet("favourite_ids", ids).apply()
        return added
    }
    fun source(): PlaylistSource? = prefs.getString("playlist_source", null)?.let {
        runCatching { PlaylistSource.fromJson(org.json.JSONObject(it)) }.getOrNull()
    }
    fun updatedAt(): Long = prefs.getLong("playlist_updated_at", 0)
    fun playlistEpgUrl(): String? = prefs.getString("playlist_epg_url", null)

    suspend fun importPlaylistFromStream(
        inputStream: InputStream,
        source: PlaylistSource? = null,
        preserveSelection: Boolean = false
    ): List<Channel> = withContext(Dispatchers.IO) {
        val parser = PlaylistParser()
        val channels = inputStream.use { parser.parse(it) }
        if (channels.isNotEmpty()) {
            val previousId = prefs.getString("last_channel_id", null)
            val restored = if (preserveSelection) channels.indexOfFirst { it.stableId == previousId }.coerceAtLeast(0) else 0
            saveChannelsToTsv(channels)
            prefs.edit().putBoolean("playlist_ready", true).putInt("last_channel_index", restored)
                .putString("last_channel_id", channels[restored].stableId)
                .putLong("playlist_updated_at", System.currentTimeMillis())
                .putString("playlist_epg_url", parser.epgUrl)
                .apply { if (source != null) putString("playlist_source", source.toJson().toString()) }.apply()
        }

        channels
    }

    suspend fun loadCachedChannels(): List<Channel> = withContext(Dispatchers.IO) {
        if (!channelsTsvFile.exists()) return@withContext emptyList()

        val channels = mutableListOf<Channel>()
        BufferedReader(InputStreamReader(android.util.AtomicFile(channelsTsvFile).openRead(), Charsets.UTF_8)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = line.split("\t")
                if (parts.size >= 3) {
                    val number = parts[0].toIntOrNull() ?: (channels.size + 1)
                    val name = parts[1]
                    val url = parts[2]
                    val group = if (parts.size > 3 && parts[3].isNotEmpty()) parts[3] else null
                    val logo = if (parts.size > 4 && parts[4].isNotEmpty()) parts[4] else null
                    val userAgent = if (parts.size > 5 && parts[5].isNotEmpty()) parts[5] else null
                    val referer = if (parts.size > 6 && parts[6].isNotEmpty()) parts[6] else null

                    channels.add(
                        Channel(
                            number = number,
                            name = name,
                            url = url,
                            group = group,
                            logo = logo,
                            userAgent = userAgent,
                            referer = referer,
                            tvgId = parts.getOrNull(7)?.takeIf { it.isNotEmpty() },
                            mimeType = parts.getOrNull(8)?.takeIf { it.isNotEmpty() }
                        )
                    )
                }
                line = reader.readLine()
            }
        }
        channels
    }

    private fun saveChannelsToTsv(channels: List<Channel>) {
        val atomicFile = android.util.AtomicFile(channelsTsvFile)
        val out = atomicFile.startWrite()
        try {
            val writer = out.bufferedWriter(Charsets.UTF_8)
            for (ch in channels) {
                val num = ch.number
                val name = ch.name.replace("\t", " ")
                val url = ch.url.replace("\t", " ")
                val group = (ch.group ?: "").replace("\t", " ")
                val logo = (ch.logo ?: "").replace("\t", " ")
                val ua = (ch.userAgent ?: "").replace("\t", " ")
                val ref = (ch.referer ?: "").replace("\t", " ")
                val id = ch.tvgId.orEmpty().replace("\t", " ")
                val mime = ch.mimeType.orEmpty().replace("\t", " ")
                writer.write(listOf("$num", name, url, group, logo, ua, ref, id, mime)
                    .joinToString("\t") { it.replace('\n', ' ').replace('\r', ' ') } + "\n")
            }
            writer.flush()
            atomicFile.finishWrite(out)
        } catch (error: Exception) {
            atomicFile.failWrite(out)
            throw error
        }
    }

    fun clearPlaylist() {
        prefs.edit().putBoolean("playlist_ready", false).putInt("last_channel_index", 0)
            .remove("last_channel_id").remove("playlist_source").remove("playlist_updated_at").apply()
        if (playlistSrcFile.exists()) playlistSrcFile.delete()
        if (channelsTsvFile.exists()) channelsTsvFile.delete()
    }
}
