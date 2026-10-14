package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.player.CrtSwitchSound
import com.couchzap.iptv.ui.WelcomeIntro
import com.couchzap.iptv.util.Anim
import com.couchzap.iptv.util.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28], qualifiers = "w960dp-h540dp-land-mdpi")
class PolishRegressionTest {
    private fun prefs(): Prefs {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return Prefs(context).apply { setLanguage("en"); setInterfaceMode("TV") }
    }
    private fun intro(activity: MainActivity) = MainActivity::class.java.getDeclaredField("welcomeIntro").apply { isAccessible = true }.get(activity) as WelcomeIntro
    private fun assertSettled(activity: MainActivity) {
        for (id in listOf(R.id.localized_label_1, R.id.tv_import_title, R.id.tv_import_hint, R.id.btn_import, R.id.btn_public)) {
            val view = activity.findViewById<View>(id)
            assertEquals(1f, view.alpha); assertEquals(0f, view.translationY)
            assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        }
        assertEquals(0f, activity.findViewById<View>(R.id.intro_bolt).alpha)
    }
    @Test fun `first menu key completes intro and still opens settings immediately`() {
        val prefs = prefs(); PlaylistRepository(ApplicationProvider.getApplicationContext()).clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get(); assertTrue(intro(activity).running)
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
            assertFalse(intro(activity).running); assertSettled(activity)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_settings).visibility)
            assertTrue(activity.findViewById<View>(R.id.layout_settings).hasFocus())
            assertTrue(prefs.hasSeenIntro())
            intro(activity).start(); assertFalse(intro(activity).running)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertSettled(activity)
        } finally { controller.destroy() }
    }
    @Test fun `touch and stop cancel intro without a late flash or hidden actions`() {
        prefs(); PlaylistRepository(ApplicationProvider.getApplicationContext()).clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {
            val activity = controller.get()
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            try { activity.dispatchTouchEvent(event) } finally { event.recycle() }
            assertFalse(intro(activity).running); assertSettled(activity)
            controller.pause().stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertSettled(activity)
        } finally { controller.destroy() }
    }
    @Test fun `system animations disabled skips intro and all overlay motion`() {
        val prefs = prefs(); val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        PlaylistRepository(context).clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            assertTrue(prefs.hasSeenIntro()); assertFalse(intro(controller.get()).running); assertSettled(controller.get())
            val view = controller.get().findViewById<View>(R.id.layout_settings)
            Anim.showOverlay(view); assertEquals(1f, view.alpha); assertEquals(0f, view.translationX)
        } finally { controller.destroy() }
    }
    @Test fun `rapid overlays and shutters settle only to newest state without retained layers`() {
        prefs(); val view = FrameLayout(ApplicationProvider.getApplicationContext())
        repeat(30) {
            view.visibility = View.GONE; Anim.showOverlay(view)
            if (android.os.Build.VERSION.SDK_INT == 24) { assertEquals(1f, view.alpha); assertEquals(0f, view.translationX) }
            Anim.hideOverlay(view)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            assertEquals(View.GONE, view.visibility); assertEquals(1f, view.alpha); assertEquals(0f, view.translationX)
            Anim.fadeInShutter(view); Anim.fadeOutShutter(view); Anim.fadeInShutter(view); Anim.finish(view)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
            assertEquals(View.VISIBLE, view.visibility); assertEquals(1f, view.alpha)
            assertEquals(View.LAYER_TYPE_NONE, view.layerType)
        }
    }
    private class Audio : CrtSwitchSound.Backend {
        var completion: ((Boolean) -> Unit)? = null
        var loads = 0; var plays = 0; var stops = 0; var releases = 0
        override fun prepare(ready: (Boolean) -> Unit) { loads++; completion = ready }
        override fun play(gain: Float) { assertTrue(gain in 0f..0.4f); plays++ }
        override fun stop() { stops++ }
        override fun release() { releases++ }
        fun ready(success: Boolean = true) { completion!!(success) }
    }
    private val one = Channel(1, "One", "file:///one", tvgId = "one")
    private val two = Channel(2, "Two", "file:///two", tvgId = "two")
    @Test fun `CRT defaults off persists toggle and ignores first playback and same channel refresh`() {
        val prefs = prefs(); val audio = Audio(); val sound = CrtSwitchSound(prefs, audio) { false }
        assertFalse(prefs.isCrtSoundEnabled()); sound.committed(one, two); assertEquals(0, audio.loads); assertEquals(0, audio.plays)
        sound.setEnabled(true); assertTrue(Prefs(ApplicationProvider.getApplicationContext()).isCrtSoundEnabled()); audio.ready()
        assertEquals(1, audio.plays)
        sound.committed(null, one); sound.committed(one, one.copy(url = "file:///refreshed")); assertEquals(1, audio.plays)
        sound.committed(one, two); assertEquals(2, audio.plays); assertTrue(audio.stops >= audio.plays)
        sound.setEnabled(false); sound.committed(two, one); assertEquals(2, audio.plays); assertFalse(prefs.isCrtSoundEnabled())
        sound.close(); assertEquals(1, audio.releases)
    }
    @Test fun `CRT loading failures mute pause disabling and destruction cannot replay stale audio`() {
        val prefs = prefs(); val audio = Audio(); var muted = false
        val sound = CrtSwitchSound(prefs, audio) { muted }
        sound.setEnabled(true); sound.committed(one, two); audio.ready(false); assertEquals(0, audio.plays)
        sound.setEnabled(true); assertEquals(2, audio.loads); sound.setEnabled(false); audio.ready(); assertEquals(0, audio.plays)
        muted = true; sound.setEnabled(true); sound.committed(one, two); assertEquals(0, audio.plays)
        muted = false; sound.pause(); sound.committed(one, two); assertEquals(0, audio.plays)
        sound.resume(); assertEquals(0, audio.plays); sound.committed(one, two); assertEquals(1, audio.plays)
        sound.close(); sound.committed(two, one); assertEquals(1, audio.plays)
        val loading = Audio(); val pending = CrtSwitchSound(prefs, loading) { false }
        pending.setEnabled(true); pending.close(); loading.ready(); assertEquals(0, loading.plays)
        val stopped = Audio(); val background = CrtSwitchSound(prefs, stopped) { false }
        background.setEnabled(true); background.pause(); stopped.ready(); background.resume(); assertEquals(0, stopped.plays)
        background.close()
    }
    @Test fun `held channel keys produce one sound at final commit and browsing or retry stay silent`() {
        val prefs = prefs(); val context = ApplicationProvider.getApplicationContext<Context>(); val repo = PlaylistRepository(context)
        repo.clearPlaylist(); runBlocking { repo.importPlaylistFromStream((1..4).joinToString("\n") { "Station $it,file:///test-$it.ts" }.byteInputStream()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get(); val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != 4 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertEquals(4, list.adapter!!.itemCount)
            val field = MainActivity::class.java.getDeclaredField("crtSound").apply { isAccessible = true }
            (field.get(activity) as CrtSwitchSound).close()
            prefs.setCrtSoundEnabled(true); val audio = Audio(); field.set(activity, CrtSwitchSound(prefs, audio) { false }); audio.ready()
            repeat(3) { activity.dispatchKeyEvent(KeyEvent(0, it.toLong(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP, it)) }
            assertEquals(0, audio.plays); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(450)); assertEquals(1, audio.plays)
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
            repeat(9) { activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)) }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(1, audio.plays)
            MainActivity::class.java.getDeclaredMethod("playCurrentChannel").apply { isAccessible = true }.invoke(activity)
            assertEquals(1, audio.plays)
        } finally { controller.destroy() }
    }
    @Test fun `original local CRT sample stays short quiet and unclipped`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = context.resources.openRawResource(R.raw.crt_switch).use { it.readBytes() }
        assertEquals("RIFF", String(bytes, 0, 4)); assertEquals("WAVE", String(bytes, 8, 4))
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, data.getShort(22).toInt()); val rate = data.getInt(24); assertEquals(16, data.getShort(34).toInt())
        val frames = (bytes.size - 44) / 2; assertTrue(frames.toDouble() / rate in .1.. .18)
        val samples = (0 until frames).map { data.getShort(44 + it * 2).toInt() }
        assertTrue(samples.maxOf { kotlin.math.abs(it) } in 1000..6500)
        assertEquals(0, samples.first()); assertEquals(0, samples.last()); assertTrue(bytes.size < 10_000)
    }
}
