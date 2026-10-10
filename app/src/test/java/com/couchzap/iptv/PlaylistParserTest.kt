package com.couchzap.iptv

import com.couchzap.iptv.data.PlaylistParser
import org.junit.Assert.*
import org.junit.Test

class PlaylistParserTest {
    private fun parse(text: String) = text.byteInputStream().use { PlaylistParser().parse(it) }

    @Test fun `BOM metadata and commas in channel names survive import`() {
        val channels = parse("\uFEFF#EXTM3U\n#EXTINF:-1 group-title=\"News, World\" tvg-logo=\"https://example.org/logo.png\",News, Live\nhttps://example.org/live.m3u8")
        assertEquals("News, Live", channels.single().name)
        assertEquals("News, World", channels.single().group)
        assertEquals("https://example.org/logo.png", channels.single().logo)
    }

    @Test fun `TXT stream headers preserve equals and decode values`() {
        val channel = parse("News,https://example.org/live|User-Agent=My%20Player&Referer=https%3A%2F%2Fexample.org%2F%3Ftoken%3Da%3Db").single()
        assertEquals("News", channel.name)
        assertEquals("https://example.org/live", channel.url)
        assertEquals("My Player", channel.userAgent)
        assertEquals("https://example.org/?token=a=b", channel.referer)
    }

    @Test fun `bare URL query commas are not treated as TXT separators`() {
        val url = "https://example.org/live?ids=1,2"
        assertEquals(url, parse(url).single().url)
    }

    @Test fun `deduplication preserves contiguous channel numbers`() {
        val channels = parse("One,https://example.org/one\nAgain,https://example.org/one\nTwo,https://example.org/two")
        assertEquals(listOf(1, 2), channels.map { it.number })
    }

    @Test fun `VLC headers do not leak into the next channel`() {
        val channels = parse("#EXTM3U\n#EXTINF:-1,One\n#EXTVLCOPT:http-user-agent=Player\nhttps://example.org/one\n#EXTINF:-1,Two\nhttps://example.org/two")
        assertEquals("Player", channels[0].userAgent)
        assertNull(channels[1].userAgent)
    }

    @Test fun `invalid playlist does not create channels`() {
        assertTrue(parse("not a playlist\n#comment").isEmpty())
    }
}
