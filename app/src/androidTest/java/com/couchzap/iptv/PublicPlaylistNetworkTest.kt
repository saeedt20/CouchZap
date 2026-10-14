package com.couchzap.iptv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.couchzap.iptv.data.HttpStreams
import com.couchzap.iptv.data.PlaylistParser
import com.couchzap.iptv.data.PlaylistSource
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live integration check: use the device's TLS stack, not the host JVM. */
@RunWith(AndroidJUnit4::class)
class PublicPlaylistNetworkTest {
    @Test fun officialPublicPlaylistDownloadsAndParses() {
        val channels = HttpStreams.open(PlaylistSource.public().location).use { PlaylistParser().parse(it) }
        assertTrue("Public playlist returned no channels", channels.isNotEmpty())
    }
    @Test fun publicChoicesDownload() {
        val json = HttpStreams.open("https://iptv-org.github.io/api/countries.json", 4L * 1024 * 1024)
            .use { JSONArray(it.bufferedReader().readText()) }
        assertTrue("Country directory returned no entries", json.length() > 0)
    }
}
