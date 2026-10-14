package com.couchzap.iptv

import com.couchzap.iptv.support.DiagnosticLog
import com.couchzap.iptv.support.ReleaseUpdater
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SupportFeaturesTest {
    private fun release(url: String = "https://github.com/saeedt20/CouchZap/releases/download/v2.0.2/CouchZap-2.0.2.apk") = JSONObject()
        .put("tag_name", "v2.0.2").put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject().put("name", "CouchZap-2.0.2.apk")
            .put("browser_download_url", url).put("size", 3000000).put("digest", "sha256:" + "a".repeat(64))))
    @Test fun `updates compare numeric versions and ignore unstable releases`() {
        assertTrue(ReleaseUpdater.isNewer("2.0.10", "2.0.2"))
        assertFalse(ReleaseUpdater.isNewer("2.0.2", "2.0.2"))
        assertFalse(ReleaseUpdater.isNewer("2.0.1", "2.0.2"))
        assertFalse(ReleaseUpdater.isNewer("2.0.3-beta", "2.0.2"))
        assertEquals("a".repeat(64), ReleaseUpdater.parse(release()).digest)
        assertThrows(IllegalArgumentException::class.java) { ReleaseUpdater.parse(release().put("prerelease", true)) }
    }
    @Test fun `updater refuses substituted APK hosts and paths`() {
        for (url in listOf("http://github.com/saeedt20/CouchZap/releases/download/v2.0.2/CouchZap-2.0.2.apk", "https://github.com.attacker.test/update.apk", "https://github.com/other/repo/update.apk")) {
            assertThrows(IllegalArgumentException::class.java) { ReleaseUpdater.parse(release(url)) }
        }
    }
    @Test fun `diagnostics exclude exception messages and nested credentials`() {
        val error = java.io.IOException("https://private.test/list?token=secret", IllegalStateException("user:password"))
        val report = DiagnosticLog.safeError(error)
        assertTrue(report.contains("java.io.IOException"))
        assertTrue(report.contains("java.lang.IllegalStateException"))
        assertFalse(report.contains("private.test"))
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("password"))
    }
}
