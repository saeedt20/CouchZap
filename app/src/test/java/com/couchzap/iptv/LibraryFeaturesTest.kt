package com.couchzap.iptv

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.*
import com.couchzap.iptv.player.StreamInfoFormatter
import com.couchzap.iptv.player.StreamTypeResolver
import com.couchzap.iptv.ui.NumberEntryController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class LibraryFeaturesTest {
    @Test fun `playlist metadata retains guide IDs and explicit stream types`() {
        val parser = PlaylistParser()
        val channel = parser.parse("#EXTM3U x-tvg-url=\"https://example.org/guide.xml\"\n#EXTINF:-1 tvg-id=\"news.example\",News\n#KODIPROP:inputstream.adaptive.manifest_type=hls\nhttps://example.org/watch".byteInputStream()).single()
        assertEquals("news.example", channel.tvgId)
        assertEquals("https://example.org/guide.xml", parser.epgUrl)
        assertEquals("application/x-mpegURL", StreamTypeResolver.known(channel))
    }
    @Test fun `public sources use official typed links and reject path injection`() {
        assertEquals("https://iptv-org.github.io/iptv/countries/th.m3u", PlaylistSource.public("countries", "TH", "Thailand").location)
        assertEquals("https://iptv-org.github.io/iptv/index.category.m3u", PlaylistSource.public().location)
        assertThrows(IllegalArgumentException::class.java) { PlaylistSource.public("countries", "../secrets") }
        assertFalse(HttpStreams.validUrl("https://"))
        assertFalse(HttpStreams.validUrl("file:///playlist.m3u"))
        assertTrue(HttpStreams.validUrl("https://example.org/list.m3u?token=a=b"))
    }
    @Test fun `1440p is QHD and never labelled 4K`() {
        assertEquals("QHD", StreamInfoFormatter.formatQualityTag(1440))
        assertEquals("4K", StreamInfoFormatter.formatQualityTag(2160))
        assertEquals("", StreamInfoFormatter.formatQualityTag(-1))
    }
    @Test fun `numeric entry can select a five digit channel`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val digits = TextView(context); val notice = TextView(context)
        var selected = 0
        val controller = NumberEntryController(digits, notice, { 12000 }, { selected = it })
        listOf(1, 0, 0, 0, 1).forEach(controller::appendDigit)
        assertEquals(10001, selected)
        assertEquals(View.GONE, digits.visibility)
        controller.cancel()
    }
    @Test fun `XMLTV timezone gzip and current next matching agree`() {
        val now = EpgRepository.timestamp("20261013143000 +0000")
        assertEquals(EpgRepository.timestamp("20261013180000 +0330"), now)
        val xml = """<tv>
            <programme channel="news" start="20261013140000 +0000" stop="20261013150000 +0000"><title>Current &amp; live</title></programme>
            <programme channel="news" start="20261013150000 +0000" stop="20261013160000 +0000"><title>Next show</title></programme>
            <programme channel="other" start="20261013140000 +0000" stop="20261013150000 +0000"><title>Other</title></programme>
        </tv>"""
        val compressed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(xml.toByteArray()) } }.toByteArray()
        val plain = EpgRepository.parse(xml.byteInputStream(), setOf("news"), now)
        val gzip = EpgRepository.parse(compressed.inputStream(), setOf("news"), now)
        assertEquals(plain, gzip)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EpgRepository(context)
        repository.clear()
        assertEquals(2, repository.import(xml.byteInputStream(), setOf("news"), now = now))
        val (current, next) = repository.nowNext(Channel(1, "News", "https://example.org", tvgId = "news"), now)
        assertEquals("Current & live", current!!.title)
        assertEquals("Next show", next!!.title)
        assertThrows(IllegalArgumentException::class.java) { repository.import("<tv/>".byteInputStream(), setOf("news"), now = now) }
        assertEquals("Current & live", repository.nowNext(Channel(1, "News", "https://example.org", tvgId = "news"), now).first!!.title)
    }
}
