package com.couchzap.iptv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.couchzap.iptv.data.HttpStreams
import com.couchzap.iptv.data.PlaylistParser
import com.couchzap.iptv.data.PlaylistSource
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.data.PublicChannelOrder
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import com.couchzap.iptv.support.ReleaseUpdater
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import android.widget.Button
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.couchzap.iptv.util.Prefs
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
    @Test fun officialFreeTvPlaylistDownloadsAndPrepares() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = PlaylistSource.freeTv("playlist_iran.m3u8", "Iran")
        val prepared = PlaylistRepository(context).preparePlaylist(HttpStreams.open(source.location), source)
        assertTrue("Free-TV returned no direct streams", prepared.channels.isNotEmpty())
        assertTrue("Public list was not sorted", prepared.channels == PublicChannelOrder.sorted(prepared.channels))
        assertTrue("Combined guide URL was not separated", prepared.epgUrls.none { it.contains(',') })
    }
    @Test fun officialReleaseDownloadsAndVerifies() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val release = ReleaseUpdater.latest()
        val file = ReleaseUpdater.download(context, release) { }
        assertTrue("Verified APK was not saved", file.exists())
        ReleaseUpdater.verifyApk(context, file, release.version)
        file.delete()
    }
    @Test fun supportSettingsOpenBeforeImport() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // This test starts after the language choice; the signed UI probe tests the first-run gate.
        instrumentation.runOnMainSync { Prefs(context).setLanguage("en"); Prefs(context).markIntroSeen() }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
                assertTrue(activity.findViewById<View>(R.id.layout_settings).visibility == View.VISIBLE)
                assertTrue(activity.findViewById<Button>(R.id.btn_settings_logs).performClick())
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                fun containsReport(view: View): Boolean {
                    if (view is TextView && view.text.contains("CouchZap diagnostic report")) return true
                    return view is ViewGroup && (0 until view.childCount).any { containsReport(view.getChildAt(it)) }
                }
                assertTrue("Diagnostic report panel was not shown", containsReport(activity.findViewById(R.id.settings_pages)))
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun publicChoicesDownload() {
        val json = HttpStreams.open("https://iptv-org.github.io/api/countries.json", 4L * 1024 * 1024)
            .use { JSONArray(it.bufferedReader().readText()) }
        assertTrue("Country directory returned no entries", json.length() > 0)
    }
}
