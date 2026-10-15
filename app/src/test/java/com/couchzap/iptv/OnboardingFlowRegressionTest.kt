package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.*
import com.couchzap.iptv.ui.PlaylistMenus
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.util.Prefs
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "w960dp-h540dp-land-mdpi")
class OnboardingFlowRegressionTest {
    private fun screen(playlist: Boolean = false, test: (MainActivity, PlaylistRepository) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).setLanguage("en"); Prefs(context).setInterfaceMode("TV")
        val repo = PlaylistRepository(context); repo.clearPlaylist()
        if (playlist) runBlocking {
            val input = "#EXTM3U\n#EXTINF:-1 tvg-id=\"z\",Zulu\nhttps://example.org/z\n#EXTINF:-1 tvg-id=\"a\",Alpha\nhttps://example.org/a"
            val list = repo.importPlaylistFromStream(input.byteInputStream(), PlaylistSource.public())
            repo.saveLastChannelIndex(1, list[1]); repo.toggleFavourite(list[1])
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            if (playlist) await(activity) { activity.uiView<androidx.recyclerview.widget.RecyclerView>(R.id.rv_channels).adapter!!.itemCount == 2 }
            layout(activity); test(activity, repo)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.uiView<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY)); root.layout(0, 0, 960, 540); activity.layoutSettingsForTest(960, 540)
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity)
    }
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun choose(activity: MainActivity, label: String) {
        views(activity.uiView(R.id.settings_pages)).filter { it.isUiAction }.first { it.uiLabel().text.toString().removePrefix("✓  ") == label }.performClick(); layout(activity)
    }
    private fun field(activity: MainActivity, name: String): Any? = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun await(activity: MainActivity, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); layout(activity); Thread.sleep(5) }
        assertTrue("operation did not settle", condition())
    }
    private fun import(activity: MainActivity, source: PlaylistSource, refresh: Boolean = false, input: () -> InputStream) {
        val method = MainActivity::class.java.getDeclaredMethod("importPlaylist", PlaylistSource::class.java, Boolean::class.javaPrimitiveType, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
        method.invoke(activity, source, refresh, input)
    }
    private fun injectMenus(activity: MainActivity, repo: PlaylistRepository, accepted: MutableList<PlaylistSource>, loader: suspend (String, String) -> List<PublicCatalog.Entry>) {
        val panel = field(activity, "settingsPanel") as SettingsPanel
        val menus = PlaylistMenus(activity, repo, activity.lifecycleScope, {}, { source, _ -> accepted.add(source) }, {}, panel,
            { if (activity.uiView<View>(R.id.layout_settings).visibility != View.VISIBLE) press(activity, KeyEvent.KEYCODE_MENU) }, loader)
        MainActivity::class.java.getDeclaredField("playlistMenus").apply { isAccessible = true }.set(activity, menus)
    }
    @Test fun `welcome public source requires provider and scope before importing and Back returns to welcome`() = screen { activity, repo ->
        val accepted = mutableListOf<PlaylistSource>(); val calls = mutableListOf<Pair<String,String>>()
        injectMenus(activity, repo, accepted) { provider, kind -> calls.add(provider to kind); listOf(PublicCatalog.Entry("Iran", "playlist_iran.m3u8")) }
        activity.uiView<View>(R.id.btn_public).performClick(); layout(activity)
        assertTrue(accepted.isEmpty()); assertTrue(calls.isEmpty())
        choose(activity, "Free-TV"); assertTrue(accepted.isEmpty())
        val labels = views(activity.uiView(R.id.settings_pages)).filter { it.isUiAction }.map { it.uiLabel().text.toString() }
        assertFalse(labels.contains(activity.getString(R.string.by_language)))
        choose(activity, activity.getString(R.string.by_country)); shadowOf(Looper.getMainLooper()).idle(); layout(activity)
        assertEquals(listOf("free-tv" to "countries"), calls)
        choose(activity, "Iran")
        assertEquals("free-tv", accepted.single().provider); assertEquals("playlist_iran.m3u8", accepted.single().code)
        assertFalse(repo.isPlaylistReady())
        repeat(3) { press(activity, KeyEvent.KEYCODE_BACK) }
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
        assertTrue(activity.uiView<View>(R.id.btn_public).isFocused)
    }
    @Test fun `changing an existing source requires confirmation and cancellation keeps it`() = screen(true) { activity, repo ->
        val accepted = mutableListOf<PlaylistSource>(); val prior = repo.source()
        injectMenus(activity, repo, accepted) { _, _ -> emptyList() }
        press(activity, KeyEvent.KEYCODE_MENU); activity.uiView<View>(R.id.btn_settings_reimport).performClick()
        choose(activity, activity.getString(R.string.public_channels)); choose(activity, "Free-TV"); choose(activity, activity.getString(R.string.all_public))
        assertTrue(accepted.isEmpty()); assertEquals(prior, repo.source())
        assertEquals(activity.getString(R.string.cancel),activity.uiView<View>(R.id.layout_settings).findFocus()!!.uiLabel().text.toString())
        choose(activity, activity.getString(R.string.cancel)); assertTrue(accepted.isEmpty())
        choose(activity, activity.getString(R.string.all_public)); choose(activity, activity.getString(R.string.replace_playlist))
        assertEquals("free-tv", accepted.single().provider); assertEquals(prior, repo.source())
    }
    @Test fun `each public provider preserves its supported routes and source identity`() {
        for(provider in listOf("iptv-org","free-tv"))for(kind in if(provider=="iptv-org")listOf("all","countries","languages","categories")else listOf("all","countries","categories"))screen { activity,repo ->
            val calls=mutableListOf<Pair<String,String>>();val accepted=mutableListOf<PlaylistSource>()
            val code=if(provider=="free-tv")"playlist_iran.m3u8" else "ir"
            injectMenus(activity,repo,accepted){p,k->calls.add(p to k);listOf(PublicCatalog.Entry("Iran",code))}
            activity.uiView<View>(R.id.btn_public).performClick();layout(activity);choose(activity,if(provider=="free-tv")"Free-TV" else provider)
            val title=when(kind){"all"->R.string.all_public;"countries"->R.string.by_country;"languages"->R.string.by_language;else->R.string.by_category}
            choose(activity,activity.getString(title))
            if(kind!="all"){shadowOf(Looper.getMainLooper()).idle();layout(activity);choose(activity,"Iran");assertEquals(listOf(provider to kind),calls)}else assertTrue(calls.isEmpty())
            assertEquals(provider,accepted.single().publicProvider)
            if(kind!="all")assertEquals(code,accepted.single().code)
            assertFalse(repo.isPlaylistReady())
        }
    }
    @Test fun `a late catalog reply after Back cannot render or steal focus`() = screen { activity, repo ->
        val pending = CompletableDeferred<List<PublicCatalog.Entry>>()
        injectMenus(activity, repo, mutableListOf()) { _, _ -> withContext(NonCancellable) { pending.await() } }
        activity.uiView<View>(R.id.btn_public).performClick(); choose(activity, "Free-TV"); choose(activity, activity.getString(R.string.by_country))
        press(activity, KeyEvent.KEYCODE_BACK)
        val focus = activity.uiView<View>(R.id.layout_settings).findFocus()
        pending.complete(listOf(PublicCatalog.Entry("Late entry", "playlist_iran.m3u8")))
        shadowOf(Looper.getMainLooper()).idle()
        assertSame(focus, activity.uiView<View>(R.id.layout_settings).findFocus())
        assertFalse(views(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().any { it.uiLabel().text == "Late entry" })
    }
    @Test fun `cancel before commit and a newer import prevent an old response replacing the list`() = screen(true) { activity, repo ->
        val gate = CountDownLatch(1); val entered = CountDownLatch(1)
        val input = object : java.io.ByteArrayInputStream("Late,https://example.org/late".toByteArray()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int { entered.countDown(); gate.await(5, TimeUnit.SECONDS); return super.read(b, off, len) }
        }
        try {
            val old = runBlocking { repo.loadCachedChannels() }; val prior = repo.source()
            import(activity, PlaylistSource.freeTv()) { input }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            choose(activity, activity.getString(R.string.cancel))
            assertEquals(old, runBlocking { repo.loadCachedChannels() }); assertEquals(prior, repo.source())
            import(activity, PlaylistSource.freeTv("playlist_iran.m3u8", "Iran")) { "New,https://example.org/new".byteInputStream() }
            await(activity) { !(field(activity, "importInProgress") as Boolean) }
            gate.countDown(); shadowOf(Looper.getMainLooper()).idle()
            assertEquals("New", runBlocking { repo.loadCachedChannels() }.single().name)
            assertEquals("playlist_iran.m3u8", repo.source()!!.code)
        } finally { gate.countDown() }
    }
    @Test fun `empty response and network failure preserve the prior list and success timestamp`() = screen(true) { activity, repo ->
        val old = runBlocking { repo.loadCachedChannels() }; val time = repo.updatedAt(); val source = repo.source()
        import(activity, PlaylistSource.freeTv()) { "<html>error page</html>".byteInputStream() }
        await(activity) { !(field(activity, "importInProgress") as Boolean) }
        assertEquals(old, runBlocking { repo.loadCachedChannels() }); assertEquals(time, repo.updatedAt()); assertEquals(source, repo.source())
        press(activity, KeyEvent.KEYCODE_BACK)
        import(activity, PlaylistSource.freeTv()) { throw java.net.SocketTimeoutException("private token should not show") }
        await(activity) { !(field(activity, "importInProgress") as Boolean) }
        assertEquals(old, runBlocking { repo.loadCachedChannels() }); assertEquals(time, repo.updatedAt())
        val texts = views(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().map { it.uiLabel().text.toString() }
        assertFalse(texts.any { it.contains("private token") })
        assertTrue(texts.any { it.contains(activity.getString(R.string.import_error_timeout)) })
    }
    @Test fun `refresh preserves selected channel paused state and accepts its changed source position`() = screen(true) { activity, repo ->
        val prior = runBlocking { repo.loadCachedChannels() }.last()
        activity.onChannelPlaybackChanged(prior, com.couchzap.iptv.player.ChannelPlaybackStatus.PAUSED)
        val text = "#EXTM3U\n#EXTINF:-1 tvg-id=\"new\",Beta\nhttps://example.org/b\n#EXTINF:-1 tvg-id=\"a\",Alpha\nhttps://example.org/a\n#EXTINF:-1 tvg-id=\"z\",Zulu\nhttps://example.org/z"
        import(activity, PlaylistSource.public(), true) { text.byteInputStream() }
        await(activity) { !(field(activity, "importInProgress") as Boolean) }
        val refreshed = runBlocking { repo.loadCachedChannels() }
        assertEquals(prior.stableId, refreshed[repo.getLastChannelIndex()].stableId)
        assertTrue(repo.isFavourite(refreshed.last()))
        assertEquals(com.couchzap.iptv.player.ChannelPlaybackStatus.PAUSED, field(activity,"playbackStatus"))
        assertEquals(3, refreshed.last().number)
        activity.onChannelPlaybackChanged(prior, com.couchzap.iptv.player.ChannelPlaybackStatus.BUFFERING)
        assertEquals(com.couchzap.iptv.player.ChannelPlaybackStatus.BUFFERING, field(activity,"playbackStatus"))
    }
    @Test fun `welcome language is selectable before import and manual path offers file and URL`() = screen { activity, repo ->
        activity.uiView<View>(R.id.btn_import_language).performClick(); choose(activity, "فارسی")
        assertEquals("fa", Prefs(activity).getLanguage()); assertFalse(repo.isPlaylistReady())
        assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
        activity.uiView<View>(R.id.btn_import).performClick()
        val text = views(activity.uiView(R.id.settings_pages)).filter { it.isUiAction }.map { it.uiLabel().text.toString() }
        assertTrue(text.contains(activity.getString(R.string.import_file))); assertTrue(text.contains(activity.getString(R.string.import_link)))
    }
    @Test fun `link validation retains URI input and IME Done shares a single submit guard`() = screen { activity, repo ->
        val accepted=mutableListOf<PlaylistSource>()
        injectMenus(activity,repo,accepted){_,_->emptyList()}
        activity.uiView<View>(R.id.btn_import).performClick();layout(activity)
        choose(activity,activity.getString(R.string.import_link))
        val input=views(activity.uiView(R.id.settings_pages)).filterIsInstance<android.widget.EditText>().single()
        assertEquals(View.LAYOUT_DIRECTION_LTR,input.layoutDirection)
        assertTrue(input.height>=56)
        input.setText("not a link")
        choose(activity,activity.getString(R.string.import_action))
        assertEquals("not a link",input.text.toString());assertTrue(accepted.isEmpty())
        assertTrue(views(activity.uiView(R.id.settings_pages)).filterIsInstance<TextView>().any{it.isShown&&it.text==activity.getString(R.string.invalid_link)})
        input.setText("https://example.org/channels.m3u8")
        input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE)
        choose(activity,activity.getString(R.string.import_action))
        assertEquals(1,accepted.size)
        assertEquals("https://example.org/channels.m3u8",accepted.single().location)
    }
    @Test fun `numeric selection uses preserved source numbers when skipped streams leave gaps`() = screen { activity, repo ->
        val text = "#EXTM3U\n#EXTINF:-1,Web\nhttps://www.youtube.com/live\n#EXTINF:-1,Beta\nhttps://example.org/b\n#EXTINF:-1,Alpha\nhttps://example.org/a"
        import(activity, PlaylistSource.freeTv()) { text.byteInputStream() }
        await(activity) { !(field(activity, "importInProgress") as Boolean) }
        val list = runBlocking { repo.loadCachedChannels() }
        assertEquals(listOf(3, 2), list.map { it.number })
        press(activity, KeyEvent.KEYCODE_BACK)
        press(activity, KeyEvent.KEYCODE_2)
        assertEquals(2, list[field(activity,"currentIndex") as Int].number)
    }

}
