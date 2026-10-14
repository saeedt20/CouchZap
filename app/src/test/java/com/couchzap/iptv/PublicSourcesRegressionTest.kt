package com.couchzap.iptv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.util.zip.GZIPInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class PublicSourcesRegressionTest {
    private fun fixture(name: String): InputStream = requireNotNull(javaClass.classLoader!!.getResourceAsStream("public-playlists/$name"))
    private fun repo(): PlaylistRepository = PlaylistRepository(ApplicationProvider.getApplicationContext<Context>()).also { it.clearPlaylist() }
    @Test fun `current official Iran lists parse and import both providers on API 24`() = runBlocking {
        val repo = repo()
        for ((name, source) in listOf("free-iran.txt.gz" to PlaylistSource.freeTv("playlist_iran.m3u8", "Iran"), "iptv-iran.txt.gz" to PlaylistSource.public("countries", "ir", "Iran"))) {
            val prepared = repo.preparePlaylist(GZIPInputStream(fixture(name)), source)
            println("official_fixture:$name:channels:${prepared.channels.size}:skipped:${prepared.skipped}:guides:${prepared.epgUrls.size}")
            assertTrue(name, prepared.channels.size > 10)
            assertEquals(PublicChannelOrder.sorted(prepared.channels), prepared.channels)
            repo.commitPlaylist(prepared, source)
            assertEquals(source, repo.source())
            assertEquals(prepared.channels, repo.loadCachedChannels())
        }
    }
    @Test fun `actual Free-TV all list separates unsupported webpages and multiple guide URLs`() = runBlocking {
        val repo = repo()
        val prepared = repo.preparePlaylist(GZIPInputStream(fixture("free-all.txt.gz")), PlaylistSource.freeTv())
        println("official_fixture:free-all:channels:${prepared.channels.size}:skipped:${prepared.skipped}:guides:${prepared.epgUrls.size}")
        assertTrue(prepared.channels.size > 100)
        assertTrue(prepared.skipped > 0)
        assertTrue(prepared.epgUrls.size > 2)
        assertTrue(prepared.epgUrls.all { HttpStreams.validUrl(it) && !it.contains(',') })
        assertFalse(prepared.channels.any(PublicChannelOrder::isWebPage))
        repo.commitPlaylist(prepared, PlaylistSource.freeTv())
        assertEquals(prepared.epgUrls, repo.playlistEpgUrls())
        assertEquals(prepared.epgUrls.first(), repo.playlistEpgUrl())
        assertEquals(prepared.skipped, repo.unsupportedCount())
    }
    @Test fun `provider catalogs expose only real country and themed files from the official snapshot`() {
        val json = JSONArray(fixture("free-catalog.json").bufferedReader().readText())
        val countries = PublicCatalog.parse("free-tv", "countries", json)
        val themes = PublicCatalog.parse("free-tv", "categories", json)
        assertTrue(countries.any { it.code == "playlist_iran.m3u8" })
        assertFalse(countries.any { it.code.contains("playlist_zz_") || it.code.contains("_vod.") })
        assertTrue(themes.any { it.code == "playlist_zz_movies.m3u8" })
        assertTrue(themes.any { it.code == "playlist_usa_vod.m3u8" })
        val official = json.let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") }.toSet() }
        assertTrue((countries + themes).all { it.code in official })
        val iptv = PublicCatalog.parse("iptv-org", "countries", JSONArray(fixture("iptv-countries.json").bufferedReader().readText()))
        assertTrue(iptv.any { it.code == "IR" }); assertTrue(iptv.size > countries.size)
    }
    @Test fun `public sorting is natural Persian and English and does not overwrite source numbers`() {
        fun ch(n: Int, name: String) = Channel(n, name, "https://example.org/$n", tvgId = "id$n")
        val input = listOf(ch(1,"Zulu"), ch(2,"alpha"), ch(3,"۱۲ News"), ch(4,"2 News"), ch(5," Alpha 10"), ch(6,"Alpha 2"), ch(7,"پ"), ch(8,"ب"), ch(9,"آ"), ch(10," "), ch(11,"ALPHA"))
        val sorted = PublicChannelOrder.sorted(input)
        assertEquals(listOf("2 News", "۱۲ News"), sorted.take(2).map { it.name })
        assertTrue(sorted.indexOf(input[5]) < sorted.indexOf(input[4]))
        assertEquals(listOf("آ", "ب", "پ"), sorted.filter { it.name in setOf("آ","ب","پ") }.map { it.name })
        assertEquals(" ", sorted.last().name)
        assertEquals(input.associate { it.stableId to it.number }, sorted.associate { it.stableId to it.number })
        assertEquals(sorted, PublicChannelOrder.sorted(input.reversed()))
    }
    @Test fun `public refresh preserves selected identity favourites and private input order`() = runBlocking {
        val repo = repo(); val public = PlaylistSource.public()
        val old = "#EXTM3U\n#EXTINF:-1 tvg-id=\"z\",Zulu\nhttps://example.org/z\n#EXTINF:-1 tvg-id=\"a\",Alpha\nhttps://example.org/a"
        val first = repo.importPlaylistFromStream(old.byteInputStream(), public)
        assertEquals("Alpha", first[0].name)
        repo.saveLastChannelIndex(1, first[1]); repo.toggleFavourite(first[1])
        val fresh = repo.importPlaylistFromStream(("#EXTM3U\n#EXTINF:-1 tvg-id=\"new\",Beta\nhttps://example.org/b\n" + old.substringAfter('\n')).byteInputStream(), public, true)
        assertEquals("Zulu", fresh[repo.getLastChannelIndex()].name); assertTrue(repo.isFavourite(fresh.last()))
        val private = repo.importPlaylistFromStream(old.byteInputStream(), PlaylistSource("url", "https://private.invalid/list", "Own"))
        assertEquals(listOf("Zulu", "Alpha"), private.map { it.name })
    }
    @Test fun `cancelled parsing and prepared but uncommitted response preserve prior data`() = runBlocking {
        val repo = repo(); val old = repo.importPlaylistFromStream("Old,https://example.org/old".byteInputStream(), PlaylistSource.public())
        val source = repo.source(); val updated = repo.updatedAt()
        val checkJob = Job()
        val input = object : java.io.ByteArrayInputStream("New,https://example.org/new".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int { checkJob.cancel(); return super.read(b, off, len) }
        }
        try { withContext(checkJob) { repo.preparePlaylist(input, PlaylistSource.freeTv()) }; fail("cancel expected") } catch (_: CancellationException) { }
        repo.preparePlaylist("New,https://example.org/new".byteInputStream(), PlaylistSource.freeTv())
        assertEquals(old, repo.loadCachedChannels()); assertEquals(source, repo.source()); assertEquals(updated, repo.updatedAt())
        assertTrue(repo.importPlaylistFromStream("<html>not a playlist</html>".byteInputStream(), PlaylistSource.freeTv()).isEmpty())
        assertEquals(old, repo.loadCachedChannels()); assertEquals(source, repo.source())
    }
    @Test fun `Free-TV paths reject injection and legacy source metadata stays compatible`() {
        assertThrows(IllegalArgumentException::class.java) { PlaylistSource.freeTv("../playlist_iran.m3u8") }
        assertThrows(IllegalArgumentException::class.java) { PlaylistSource.freeTv("playlist_iran.m3u8?x=1") }
        val source = PlaylistSource.freeTv("playlist_iran.m3u8", "Iran")
        assertEquals(source, PlaylistSource.fromJson(source.toJson()))
        assertEquals(PlaylistSource.public(), PlaylistSource.fromJson(PlaylistSource.public().toJson()))
        val legacy = PlaylistSource.fromJson(org.json.JSONObject("{\"kind\":\"public\",\"location\":\"https://iptv-org.github.io/iptv/countries/ir.m3u\",\"label\":\"Iran\"}"))
        assertEquals("iptv-org", legacy.publicProvider)
        val malicious = JSONArray("[{\"type\":\"file\",\"name\":\"playlist_iran.m3u8\",\"path\":\"../playlist_iran.m3u8\"}]")
        assertTrue(PublicCatalog.parse("free-tv", "countries", malicious).isEmpty())
    }
    @Test fun `a DRM or web entry does not invalidate the valid public stream beside it`() = runBlocking {
        val repo = repo()
        val text = "#EXTM3U\n#EXTINF:-1,Web\nhttps://www.youtube.com/watch?v=123\n#EXTINF:-1,DRM\n#KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha\nhttps://example.org/stream.mpd\n#EXTINF:-1,Valid\nhttps://example.org/stream.m3u8"
        val prepared = repo.preparePlaylist(text.byteInputStream(), PlaylistSource.freeTv())
        assertEquals(2, prepared.skipped); assertEquals("Valid", prepared.channels.single().name)
        assertEquals(3, prepared.channels.single().number)
    }
    @Test fun `local guide metadata in private playlists is preserved but not trusted in public feeds`() = runBlocking {
        val repo = repo()
        val text = "#EXTM3U x-tvg-url=\"file:///storage/guide.xml,https://example.org/guide.xml?channels=1,2\"\nNews,https://example.org/news"
        val own = repo.preparePlaylist(text.byteInputStream(), PlaylistSource("file", "file:///storage/list.txt", "Own"))
        assertEquals(listOf("file:///storage/guide.xml", "https://example.org/guide.xml?channels=1,2"), own.epgUrls)
        val public = repo.preparePlaylist(text.byteInputStream(), PlaylistSource.freeTv())
        assertEquals(listOf("https://example.org/guide.xml?channels=1,2"), public.epgUrls)
    }

}
