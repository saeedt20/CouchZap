package com.couchzap.iptv

import android.content.Context
import android.graphics.*
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
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
            assertTrue(first.width >= 320)
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
    @Test fun `stage two English and Persian settings render from native Android views`() {
        for (language in listOf("en", "fa")) render(false, false, language) { activity ->
            press(activity, KeyEvent.KEYCODE_MENU)
            save(activity, "stage2-settings-$language", 960, 540)
            activity.findViewById<View>(R.id.btn_settings_about).performClick()
            save(activity, "stage2-about-$language", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            activity.findViewById<View>(R.id.btn_settings_reimport).performClick()
            val pages = activity.findViewById<ViewGroup>(R.id.settings_pages)
            fun findButton(view: View): Button? {
                if (view is Button && view.text == activity.getString(R.string.import_link)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) findButton(view.getChildAt(i))?.let { return it }
                return null
            }
            requireNotNull(findButton(pages)).performClick()
            save(activity, "stage2-link-$language", 960, 540)
        }
        render(false, false, null) { activity -> save(activity, "stage2-first-language", 960, 540) }
    }
    @Test fun `stage three welcome adapts to English Persian TV and phone`() {
        for (language in listOf("en", "fa")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(false, false, language) { activity ->
                val first = activity.findViewById<Button>(R.id.btn_import)
                val second = activity.findViewById<Button>(R.id.btn_public)
                val rect = Rect(); assertTrue(second.getGlobalVisibleRect(rect)); assertEquals(second.height, rect.height())
                assertEquals(first.width, second.width)
                val columns = activity.findViewById<android.widget.LinearLayout>(R.id.import_columns)
                assertEquals(android.widget.LinearLayout.HORIZONTAL, columns.orientation)
                save(activity, "stage3-welcome-tv-$language", 960, 540)
                second.performClick(); save(activity, "stage3-providers-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.layout_settings).visibility)
            }
            RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
            render(false, true, language) { activity ->
                assertEquals(android.widget.LinearLayout.VERTICAL, activity.findViewById<android.widget.LinearLayout>(R.id.import_columns).orientation)
                assertTrue(activity.findViewById<Button>(R.id.btn_import).width <= 312)
                save(activity, "stage3-welcome-phone-$language", 360, 800)
            }
        }
    }
    @Test fun `stage four English and Persian screens share safe panel geometry`() {
        for (language in listOf("en", "fa")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(false, false, language) { activity -> save(activity, "stage4-welcome-$language", 960, 540) }
            render(true, false, language) { activity ->
                press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
                val channels = activity.findViewById<View>(R.id.view_channel_list)
                val settings = activity.findViewById<View>(R.id.layout_settings)
                assertEquals(channels.layoutParams.width, settings.layoutParams.width)
                assertEquals(if (language == "fa") 960 else channels.width, channels.right)
                assertTrue(channels.paddingStart in 24..32)
                val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
                assertTrue(list.findViewHolderForAdapterPosition(0)!!.itemView.height >= 48)
                save(activity, "stage4-channels-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
                val info = activity.findViewById<View>(R.id.view_osd)
                assertTrue(info.left >= 48)
                assertTrue(info.top >= 27)
                assertTrue(info.right <= 912)
                val clock = activity.findViewById<View>(R.id.tv_osd_clock)
                val name = activity.findViewById<View>(R.id.tv_osd_channel_name)
                val clockBounds = Rect(); clock.getGlobalVisibleRect(clockBounds)
                val nameBounds = Rect(); name.getGlobalVisibleRect(nameBounds)
                assertTrue(clockBounds.top >= nameBounds.bottom)
                save(activity, "stage4-info-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                press(activity, KeyEvent.KEYCODE_MENU)
                assertEquals(channels.width, settings.width)
                assertEquals(if (language == "fa") 960 else 0, if (language == "fa") settings.right else settings.left)
                val toggle = activity.findViewById<View>(R.id.btn_settings_crt)
                assertTrue(toggle.height >= 48)
                save(activity, "stage4-settings-$language", 960, 540)
            }
        }
    }
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-port-mdpi")
    fun `Android 16 settings render after tapping the child page Back button`() {
        for (language in listOf("en", "fa")) render(false, true, language) { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
            layout(activity, 360, 800)
            activity.findViewById<View>(R.id.btn_settings_mode).performClick()
            layout(activity, 360, 800)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            activity.findViewById<View>(R.id.settings_back).performClick()
            layout(activity, 360, 800)
            val scroll = activity.findViewById<android.widget.ScrollView>(R.id.settings_root_scroll)
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            save(activity, "ui-fix-settings-phone-$language-api36", 360, 800)
        }
    }
    @Test
    @Config(sdk = [28, 36])
    fun `favourite stays visibly distinct while focused and unfocused on phone and TV`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) {
            RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
            render(true, phone, language) { activity ->
                val width = if (phone) 360 else 960
                val height = if (phone) 800 else 540
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
                layout(activity, width, height)
                val star = activity.findViewById<android.widget.ImageButton>(R.id.btn_osd_favourite)
                assertTrue(star.isSelected)
                assertTrue(star.requestFocusFromTouch())
                fun centerPixel(): Int {
                    val bitmap = Bitmap.createBitmap(star.width, star.height, Bitmap.Config.ARGB_8888)
                    star.draw(Canvas(bitmap))
                    val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    bitmap.recycle(); return color
                }
                val stem = "stage7-info-$language-${if (phone) "phone" else "tv"}-api${android.os.Build.VERSION.SDK_INT}"
                assertEquals(activity.getColor(R.color.item_focus_text), centerPixel())
                save(activity, "$stem-favourite-on-focused", width, height)
                star.performClick()
                assertFalse(star.isSelected)
                assertTrue(star.isFocused)
                assertEquals(activity.getColor(R.color.item_focus), centerPixel())
                save(activity, "$stem-favourite-off-focused", width, height)
                star.clearFocus()
                assertEquals(activity.getColor(R.color.item_normal), centerPixel())
                save(activity, "$stem-favourite-off", width, height)
                star.performClick()
                assertTrue(star.isSelected)
                assertEquals(activity.getColor(R.color.accent_gold), centerPixel())
                assertEquals(star.contentDescription.toString(), activity.findViewById<android.widget.TextView>(R.id.tv_osd_action_label).text.toString())
                save(activity, "$stem-favourite-on", width, height)
            }
        }
    }
    @Test
    @Config(sdk = [28, 36])
    fun `Persian mode ratios and group labels render with correctly ordered digits and continuous search`() {
        for (phone in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
            render(true, phone, "fa") { activity ->
                val width = if (phone) 360 else 960; val height = if (phone) 800 else 540
                fun key(code: Int) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); layout(activity, width, height)
                }
                fun buttons(root: View): List<Button> = if (root is ViewGroup)
                    (0 until root.childCount).flatMap { buttons(root.getChildAt(it)) } else if (root is Button) listOf(root) else emptyList()
                val stem = "stage8-fa-${if (phone) "phone" else "tv"}-api${android.os.Build.VERSION.SDK_INT}"
                key(KeyEvent.KEYCODE_MENU)
                activity.findViewById<View>(R.id.btn_settings_mode).performClick(); layout(activity, width, height)
                assertTrue(buttons(activity.findViewById(R.id.settings_pages)).any { it.text.toString().contains("تلویزیون") })
                save(activity, "$stem-mode", width, height)
                activity.findViewById<View>(R.id.settings_back).performClick()
                activity.findViewById<View>(R.id.btn_settings_aspect).performClick(); layout(activity, width, height)
                for (ratio in listOf("۱۶:۹", "۴:۳")) {
                    val button = buttons(activity.findViewById(R.id.settings_pages)).first { it.text.toString().contains(ratio) }
                    val text = button.text.toString(); val start = text.indexOf(ratio)
                    val first = Path(); val last = Path()
                    button.layout.getSelectionPath(start, start + 1, first)
                    button.layout.getSelectionPath(start + ratio.length - 1, start + ratio.length, last)
                    val firstBounds = RectF(); val lastBounds = RectF()
                    first.computeBounds(firstBounds, true); last.computeBounds(lastBounds, true)
                    assertTrue("ratio reversed: $ratio", firstBounds.left < lastBounds.left)
                }
                save(activity, "$stem-aspect", width, height)
                key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_DPAD_CENTER)
                val search = activity.findViewById<android.widget.EditText>(R.id.et_channel_search)
                assertTrue(search.requestFocusFromTouch())
                val input = search.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
                for (word in listOf("World", "World ", "World News")) {
                    input.setComposingText(word, 1); input.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED)
                    layout(activity, width, height); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(80))
                    assertTrue(search.isFocused)
                }
                assertEquals("World News", search.text.toString())
                val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
                assertEquals("اخبار", list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<android.widget.TextView>(R.id.tv_item_channel_group).text.toString())
                save(activity, "$stem-search", width, height)
            }
        }
    }
    private fun render(playlist: Boolean, phone: Boolean, language: String? = "en", block: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (language == null) context.getSharedPreferences("couchzap_prefs", 0).edit().remove("language").commit()
        else Prefs(context).setLanguage(language)
        Prefs(context).markIntroSeen()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
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
            val channels = repo.loadCachedChannels()
            for (channel in listOf(channels[0], channels[2])) if (!repo.isFavourite(channel)) repo.toggleFavourite(channel)
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
