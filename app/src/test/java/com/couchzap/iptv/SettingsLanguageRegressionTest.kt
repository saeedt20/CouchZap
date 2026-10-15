package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.player.ChannelPlaybackStatus
import com.couchzap.iptv.support.UpdateMenu
import com.couchzap.iptv.util.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "w960dp-h540dp-land-mdpi")
class SettingsLanguageRegressionTest {
    private fun screen(language: String? = "en", playlist: Boolean = false, test: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        val prefs = Prefs(context); prefs.setInterfaceMode("TV"); language?.let(prefs::setLanguage)
        val repo = PlaylistRepository(context); repo.clearPlaylist()
        if (playlist) runBlocking { repo.importPlaylistFromStream("One,file:///one.ts\nTwo,file:///two.ts".byteInputStream()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            if (playlist) {
                val deadline = System.nanoTime() + 5_000_000_000L
                val list = activity.uiView<androidx.recyclerview.widget.RecyclerView>(R.id.rv_channels)
                while (list.adapter!!.itemCount != 2 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
                assertEquals(2, list.adapter!!.itemCount)
            }
            layout(activity); test(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.uiView<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 960, 540); activity.layoutSettingsForTest(960, 540)
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity)
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun choose(activity: MainActivity, label: String) {
        val root = activity.uiView<View>(R.id.settings_pages)
        val button = descendants(root).filter { it.isUiAction }.first { it.uiLabel().text.toString().removePrefix("✓  ") == label }
        button.requestFocusFromTouch(); button.performClick(); layout(activity)
    }
    private fun open(activity: MainActivity, id: Int) { activity.uiView<View>(id).apply { requestFocusFromTouch(); performClick() }; layout(activity) }

    @Test fun `first use opens welcome immediately and language choice is optional`() = screen(language = null) { activity ->
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
        assertEquals(View.VISIBLE, activity.uiView<View>(R.id.view_import).visibility)
        assertTrue(activity.uiView<View>(R.id.btn_import).isEnabled)
        assertTrue(activity.uiView<View>(R.id.btn_public).isEnabled)
        assertNull(Prefs(activity).getLanguage())
        open(activity, R.id.btn_import_language)
        val labels = descendants(activity.uiView(R.id.settings_pages)).filter { it.isUiAction }
            .map { it.uiLabel().text.toString().removePrefix("✓  ") }
        assertEquals(listOf("English", "Español", "हिन्दी", "ไทย", "فارسی", "العربية"), labels)
        press(activity, KeyEvent.KEYCODE_BACK)
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
        assertTrue(activity.uiView<View>(R.id.btn_import_language).isFocused)
        open(activity, R.id.btn_import_language)
        choose(activity, "فارسی")
        assertEquals("fa", Prefs(activity).getLanguage())
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.uiView<View>(R.id.root_container).layoutDirection)
        assertEquals("وارد کردن پلی‌لیست", activity.uiView<View>(R.id.btn_import).uiLabel().text.toString())
        assertFalse(PlaylistRepository(activity).isPlaylistReady())
    }
    @Test fun `welcome import can start without choosing a language`() = screen(language = null) { activity ->
        open(activity, R.id.btn_import)
        assertEquals(activity.getString(R.string.btn_import_playlist), activity.uiView<TextView>(R.id.settings_title).text.toString())
        assertTrue(descendants(activity.uiView(R.id.settings_pages)).filter { it.isUiAction }
            .any { it.uiLabel().text.toString() == activity.getString(R.string.import_link) })
        assertNull(Prefs(activity).getLanguage())
    }
    @Test fun `saved Persian inflates on Android 7 and switching live to English preserves the library`() = screen(language = "fa", playlist = true) { activity ->
        val repo = PlaylistRepository(activity); val current = runBlocking { repo.loadCachedChannels() }.first(); repo.toggleFavourite(current)
        val prior = runBlocking { repo.loadCachedChannels() }.map { it.stableId }
        press(activity, KeyEvent.KEYCODE_MENU)
        open(activity, R.id.btn_settings_language); choose(activity, "English")
        assertEquals("en", Prefs(activity).getLanguage())
        assertEquals(View.LAYOUT_DIRECTION_LTR, activity.uiView<View>(R.id.root_container).layoutDirection)
        assertEquals("Settings", activity.uiView<TextView>(R.id.settings_title).text.toString())
        assertEquals("About CouchZap", activity.uiView<com.couchzap.iptv.ui.SettingRow>(R.id.btn_settings_about).uiLabel().text.toString())
        assertEquals(prior, runBlocking { repo.loadCachedChannels() }.map { it.stableId }); assertTrue(repo.isFavourite(current))
        assertEquals(48, activity.uiView<View>(R.id.layout_settings).left)
    }
    @Test fun `all settings navigate within one panel and Back restores the parent focus and scroll`() = screen { activity ->
        press(activity, KeyEvent.KEYCODE_MENU)
        val scroll = activity.uiView<ScrollView>(R.id.settings_root_scroll)
        val sections = listOf(R.id.btn_settings_aspect, R.id.btn_settings_tracks, R.id.btn_settings_sleep, R.id.btn_settings_reimport,
            R.id.btn_settings_guide, R.id.btn_settings_mode, R.id.btn_settings_language, R.id.btn_settings_about, R.id.btn_settings_logs)
        for (id in sections) {
            val row = activity.uiView<View>(id); row.requestFocusFromTouch(); layout(activity); val position = scroll.scrollY
            open(activity, id)
            assertEquals(View.VISIBLE, activity.uiView<View>(R.id.layout_settings).visibility)
            assertNotSame(scroll, activity.uiView<FrameLayout>(R.id.settings_pages).getChildAt(0))
            press(activity, KeyEvent.KEYCODE_BACK)
            assertSame(scroll, activity.uiView<FrameLayout>(R.id.settings_pages).getChildAt(0))
            assertTrue("focus restored for $id", row.isFocused)
            assertEquals(position, scroll.scrollY)
        }
        press(activity, KeyEvent.KEYCODE_BACK)
        assertTrue(activity.uiView<View>(R.id.cz_settings_rail).hasFocus())
        assertEquals(View.VISIBLE, activity.uiView<View>(R.id.layout_settings).visibility)
        press(activity, KeyEvent.KEYCODE_BACK)
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
    }
    @Test fun `track and source child pages go back exactly one level and URL remains LTR in Persian`() = screen(language = "fa") { activity ->
        press(activity, KeyEvent.KEYCODE_MENU)
        open(activity, R.id.btn_settings_reimport)
        choose(activity, activity.getString(R.string.import_link))
        val input = descendants(activity.uiView(R.id.settings_pages)).filterIsInstance<EditText>().single()
        assertEquals(View.TEXT_DIRECTION_LTR, input.textDirection)
        input.setText("not a link"); choose(activity, activity.getString(R.string.import_action))
        assertEquals("not a link",input.text.toString())
        assertTrue(descendants(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().any{it.isShown&&it.text==activity.getString(R.string.invalid_link)})
        press(activity, KeyEvent.KEYCODE_BACK)
        assertEquals(activity.getString(R.string.playlist_sources), activity.uiView<TextView>(R.id.settings_title).text.toString())
        press(activity, KeyEvent.KEYCODE_BACK)
        open(activity, R.id.btn_settings_tracks); choose(activity, activity.getString(R.string.subtitles))
        press(activity, KeyEvent.KEYCODE_BACK)
        assertEquals(activity.getString(R.string.audio_subtitles), activity.uiView<TextView>(R.id.settings_title).text.toString())
    }
    @Test fun `playback failure cannot steal input from a nested settings page`() = screen(playlist = true) { activity ->
        press(activity, KeyEvent.KEYCODE_MENU); open(activity, R.id.btn_settings_about)
        val focused = activity.uiView<View>(R.id.layout_settings).findFocus()
        activity.onChannelFailed(runBlocking { PlaylistRepository(activity).loadCachedChannels() }.first())
        assertSame(focused, activity.uiView<View>(R.id.layout_settings).findFocus())
        assertFalse(activity.uiView<View>(R.id.btn_retry).isEnabled)
        press(activity, KeyEvent.KEYCODE_BACK)
        assertTrue("returned focus ${activity.uiView<View>(R.id.layout_settings).findFocus()?.id}, expected ${R.id.btn_settings_about}", activity.uiView<View>(R.id.btn_settings_about).isFocused)
    }
    @Test fun `down navigation reveals the last setting while the header stays fixed`() = screen { activity ->
        press(activity, KeyEvent.KEYCODE_MENU)
        val header = activity.uiView<View>(R.id.settings_title); val top = header.top
        repeat(20) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN); shadowOf(Looper.getMainLooper()).idle() }
        val exit = activity.uiView<View>(R.id.btn_settings_exit)
        exit.requestFocusFromTouch(); layout(activity); shadowOf(Looper.getMainLooper()).idle()
        val rect = android.graphics.Rect(); assertTrue(exit.getGlobalVisibleRect(rect)); assertEquals(exit.height, rect.height())
        assertEquals(top, header.top)
    }
    @Test fun `update cancel survives leaving and reopening without another automatic check`() = screen { activity ->
        val fake = FakeUpdates()
        val updater = injectUpdates(activity, fake)
        press(activity, KeyEvent.KEYCODE_MENU); open(activity, R.id.btn_settings_update)
        choose(activity, activity.getString(R.string.cancel))
        assertEquals(UpdateMenu.State.CANCELLED, updater.state)
        press(activity, KeyEvent.KEYCODE_BACK); open(activity, R.id.btn_settings_update)
        assertEquals(UpdateMenu.State.CANCELLED, updater.state)
        assertTrue(descendants(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().any { it.uiLabel().text == activity.getString(R.string.update_cancelled) })
    }
    private class FakeUpdates : UpdateMenu.Backend {
        val check = kotlinx.coroutines.CompletableDeferred<com.couchzap.iptv.support.ReleaseUpdater.Release>()
        val download = kotlinx.coroutines.CompletableDeferred<java.io.File>()
        var checks = 0; var downloads = 0
        var progress: ((Int) -> Unit)? = null
        override suspend fun latest(): com.couchzap.iptv.support.ReleaseUpdater.Release { checks++; return check.await() }
        override suspend fun download(release: com.couchzap.iptv.support.ReleaseUpdater.Release, progress: (Int) -> Unit): java.io.File {
            downloads++; this.progress = progress; return download.await()
        }
    }
    private fun injectUpdates(activity: MainActivity, fake: FakeUpdates): UpdateMenu {
        val panelField = MainActivity::class.java.getDeclaredField("settingsPanel").apply { isAccessible = true }
        val updater = UpdateMenu(activity, panelField.get(activity) as com.couchzap.iptv.ui.SettingsPanel, fake)
        MainActivity::class.java.getDeclaredField("updateMenu").apply { isAccessible = true }.set(activity, updater)
        return updater
    }
    @Test fun `update check and download retain state while leaving with one active request`() = screen { activity ->
        val fake = FakeUpdates(); val updater = injectUpdates(activity, fake)
        press(activity, KeyEvent.KEYCODE_MENU); open(activity, R.id.btn_settings_update)
        assertEquals(1, fake.checks)
        press(activity, KeyEvent.KEYCODE_BACK); open(activity, R.id.btn_settings_update)
        assertEquals(1, fake.checks)
        fake.check.complete(com.couchzap.iptv.support.ReleaseUpdater.Release("99.0.0", "https://example.invalid/update.apk", "", null, 12345, "Test release notes"))
        shadowOf(Looper.getMainLooper()).idle(); layout(activity)
        assertEquals(UpdateMenu.State.AVAILABLE, updater.state)
        choose(activity, activity.getString(R.string.download_update)); assertEquals(1, fake.downloads)
        fake.progress!!(37); shadowOf(Looper.getMainLooper()).idle()
        press(activity, KeyEvent.KEYCODE_BACK)
        val focus = activity.uiView<View>(R.id.layout_settings).findFocus()
        fake.progress!!(58); shadowOf(Looper.getMainLooper()).idle()
        assertSame(focus, activity.uiView<View>(R.id.layout_settings).findFocus())
        open(activity, R.id.btn_settings_update)
        assertEquals(1, fake.downloads)
        assertTrue(descendants(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().any { it.uiLabel().text == activity.getString(R.string.update_progress, 58) })
        fake.download.complete(java.io.File(activity.cacheDir, "test.apk").apply { writeText("test fixture") })
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateMenu.State.READY, updater.state)
        press(activity, KeyEvent.KEYCODE_BACK); open(activity, R.id.btn_settings_update)
        assertEquals(UpdateMenu.State.READY, updater.state); assertEquals(1, fake.downloads)
    }
    @Test fun `update error is displayed in panel and can be retried`() = screen { activity ->
        val fake = FakeUpdates(); val updater = injectUpdates(activity, fake)
        press(activity, KeyEvent.KEYCODE_MENU); open(activity, R.id.btn_settings_update)
        fake.check.completeExceptionally(java.io.IOException("sensitive upstream text"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateMenu.State.FAILED, updater.state)
        val texts = descendants(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().map { it.uiLabel().text.toString() }
        assertTrue(texts.contains(activity.getString(R.string.update_failed)))
        assertFalse(texts.any { it.contains("sensitive upstream text") })
    }
    @Test fun `diagnostic copy stays in panel and does not launch a sharing app`() = screen { activity ->
        press(activity, KeyEvent.KEYCODE_MENU); open(activity, R.id.btn_settings_logs)
        choose(activity, activity.getString(R.string.copy_log))
        val clip = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertTrue(clip.primaryClip!!.getItemAt(0).text.contains("CouchZap diagnostic report"))
        assertEquals(activity.getString(R.string.diagnostic_log), activity.uiView<TextView>(R.id.settings_title).text.toString())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun `language choice survives activity restart and a system configuration update`() = screen(language = null) { activity ->
        open(activity, R.id.btn_import_language)
        choose(activity, "فارسی")
        val config = android.content.res.Configuration(activity.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) }
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        activity.onConfigurationChanged(config)
        assertEquals("fa", activity.resources.configuration.locales[0].language)
        assertEquals(View.LAYOUT_DIRECTION_RTL, activity.uiView<View>(R.id.root_container).layoutDirection)
        val restarted = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            assertEquals("fa", Prefs(restarted.get()).getLanguage())
            assertEquals(View.GONE, restarted.get().uiView<View>(R.id.layout_settings).visibility)
            assertEquals("وارد کردن پلی‌لیست", restarted.get().uiView<View>(R.id.btn_import).uiLabel().text.toString())
        } finally { restarted.destroy() }
    }

    @Test
    @Config(sdk = [24, 36])
    fun `every welcome language persists across restart with translated groups and controls`() {
        val languages = listOf(
            Triple("en", "English", "Import playlist"), Triple("es", "Español", "Importar lista"),
            Triple("hi", "हिन्दी", "प्लेलिस्ट आयात करें"), Triple("th", "ไทย", "นำเข้าเพลย์ลิสต์"),
            Triple("fa", "فارسی", "وارد کردن پلی‌لیست"), Triple("ar", "العربية", "استيراد قائمة تشغيل")
        )
        val news = listOf("News", "Noticias", "समाचार", "ข่าว", "اخبار", "أخبار")
        languages.forEachIndexed { index, (code, name, label) -> screen(language = null) { activity ->
            open(activity, R.id.btn_import_language); choose(activity, name)
            assertEquals(label, activity.uiView<View>(R.id.btn_import).uiLabel().text.toString())
            assertEquals(news[index], com.couchzap.iptv.util.GroupLabels.display(activity, "News"))
            assertEquals("Custom Group", com.couchzap.iptv.util.GroupLabels.display(activity, "Custom Group"))
            val restarted = Robolectric.buildActivity(MainActivity::class.java).create().visible()
            try {
                val next = restarted.get()
                assertEquals(code, Prefs(next).getLanguage())
                assertEquals(code, next.resources.configuration.locales[0].language)
                assertEquals(label, next.uiView<View>(R.id.btn_import).uiLabel().text.toString())
                assertEquals(View.GONE, next.uiView<View>(R.id.layout_settings).visibility)
                assertEquals(if (code in listOf("fa", "ar")) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    next.uiView<View>(R.id.root_container).layoutDirection)
            } finally { restarted.destroy() }
        } }
    }
}
