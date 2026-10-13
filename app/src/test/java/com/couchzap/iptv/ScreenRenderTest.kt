package com.couchzap.iptv

import android.content.Context
import android.graphics.*
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.media3.common.Format
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.EpgRepository
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.util.Prefs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Screenshots come from real Android views; programme data and backdrop are fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    @Test fun `welcome actions fit TV and phone and use a shared width`() {
        render(false, false) { activity ->
            val first = activity.findViewById<Button>(R.id.btn_import)
            val second = activity.findViewById<Button>(R.id.btn_public)
            assertEquals(first.width, second.width)
            assertTrue(first.width >= 500)
            assertTrue(first.isFocused)
            val bounds = Rect(); second.getGlobalVisibleRect(bounds)
            assertEquals(second.height, bounds.height())
            save(activity, "welcome-tv", 960, 540)
        }
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        render(false, true) { activity ->
            val first = activity.findViewById<Button>(R.id.btn_import)
            assertTrue(first.width <= 360)
            save(activity, "welcome-phone", 360, 800)
        }
    }
    @Test fun `native channel settings and Info views render with demo data`() {
        render(true, false) { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            save(activity, "channels-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            press(activity, KeyEvent.KEYCODE_MENU)
            assertEquals(0, activity.findViewById<View>(R.id.layout_settings).left)
            save(activity, "settings-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            activity.onTracksUpdated(Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).setFrameRate(25f).setAverageBitrate(3100000).build(),
                Format.Builder().setSampleMimeType("audio/mp4a-latm").setChannelCount(2).setSampleRate(48000).build())
            activity.onPlaybackInfo(true, true, activity.getString(R.string.live))
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            save(activity, "info-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_INFO)
            assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tv_osd_technical).visibility)
            save(activity, "info-details-tv", 960, 540)
        }
    }
    private fun render(playlist: Boolean, phone: Boolean, block: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs(context).setInterfaceMode(if (phone) "Phone" else "TV")
        val repo = PlaylistRepository(context); repo.clearPlaylist(); EpgRepository(context).clear()
        if (playlist) runBlocking {
            repo.importPlaylistFromStream("""#EXTM3U
                #EXTINF:-1 tvg-id="nature" group-title="Documentary",Nature
                file:///demo/nature.mp4
                #EXTINF:-1 tvg-id="news" group-title="News",World News
                file:///demo/news.mp4
                #EXTINF:-1 tvg-id="music" group-title="Music",Studio Sessions
                file:///demo/music.mp4
                #EXTINF:-1 tvg-id="family" group-title="Family",Family
                file:///demo/family.mp4
                #EXTINF:-1 tvg-id="sports" group-title="Sports",Live Sports
                file:///demo/sports.mp4
                #EXTINF:-1 tvg-id="cinema" group-title="Movies",Cinema
                file:///demo/cinema.mp4""".trimIndent().byteInputStream(), com.couchzap.iptv.data.PlaylistSource("file", "file:///demo/channels.m3u", "Demo playlist"))
            val channels = repo.loadCachedChannels(); repo.toggleFavourite(channels[0]); repo.toggleFavourite(channels[2])
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            if (playlist) {
                val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (list.adapter!!.itemCount != 6 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
                assertEquals(6, list.adapter!!.itemCount)
                val root = activity.findViewById<FrameLayout>(R.id.root_container)
                root.addView(DemoBackdrop(context), 1, FrameLayout.LayoutParams(-1, -1))
            }
            layout(activity, if (phone) 360 else 960, if (phone) 800 else 540)
            block(activity)
        } finally { controller.destroy() }
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); layout(activity, 960, 540)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity, 960, 540)
    }
    private fun layout(activity: MainActivity, width: Int, height: Int) {
        val root = activity.findViewById<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }
    private fun save(activity: MainActivity, name: String, width: Int, height: Int) {
        val directory = System.getenv("COUCHZAP_SCREENSHOTS_DIR") ?: return
        layout(activity, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        activity.findViewById<View>(R.id.root_container).draw(Canvas(bitmap))
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private class DemoBackdrop(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#52655D"))
            paint.color = Color.parseColor("#A8B6A5"); canvas.drawCircle(width * .77f, height * .28f, height * .07f, paint)
            listOf("#41584C", "#304638", "#203629").forEachIndexed { index, color ->
                val path = Path().apply {
                    moveTo(0f, height * (.5f + index * .1f))
                    cubicTo(width * .3f, height * (.2f + index * .1f), width * .6f, height * (.8f - index * .05f), width.toFloat(), height * (.45f + index * .16f))
                    lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
                }
                paint.color = Color.parseColor(color); canvas.drawPath(path, paint)
            }
        }
    }
}
