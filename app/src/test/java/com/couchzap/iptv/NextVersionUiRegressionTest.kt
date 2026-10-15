package com.couchzap.iptv

import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], qualifiers = "w960dp-h540dp-land-mdpi")
class NextVersionUiRegressionTest {
    private fun screen(language: String, phone: Boolean, portrait: Boolean = false, check: (MainActivity) -> Unit) {
        RuntimeEnvironment.setQualifiers(if (portrait) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).apply { setLanguage(language); setInterfaceMode(if (phone) "Phone" else "TV"); markIntroSeen() }
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val repo = PlaylistRepository(context)
        repo.clearPlaylist()
        runBlocking { repo.importPlaylistFromStream((1..24).joinToString("\n") { "Station $it,file:///fixture-$it.ts" }.byteInputStream()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != 24 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5)
            }
            assertEquals(24, list.adapter!!.itemCount)
            layout(activity); check(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenWidthDp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenHeightDp, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun choice(activity: MainActivity, id: Int, label: String) {
        activity.findViewById<View>(id).performClick(); layout(activity)
        descendants(activity.findViewById(R.id.settings_pages)).filterIsInstance<Button>()
            .first { it.text.toString().removePrefix("✓  ") == label }.performClick()
        layout(activity)
    }
    private fun assertDock(activity: MainActivity, panel: View, language: String) {
        val root = activity.findViewById<View>(R.id.root_container)
        assertEquals(420, panel.width)
        if (language in listOf("fa", "ar")) assertEquals(root.width, panel.right) else assertEquals(0, panel.left)
        assertEquals(if (language in listOf("fa", "ar")) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, panel.layoutDirection)
        assertEquals(24, panel.paddingEnd)
    }

    @Test fun `both panels follow all six languages after startup live switching and rotation on phone and TV`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "es", "hi", "th", "fa", "ar")) screen(language, phone) { activity ->
            val channels = activity.findViewById<View>(R.id.view_channel_list)
            val settings = activity.findViewById<View>(R.id.layout_settings)
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER); assertDock(activity, channels, language)
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            press(activity, if (language in listOf("fa", "ar")) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
            assertTrue(activity.findViewById<View>(R.id.btn_filter_all).isFocused)
            press(activity, KeyEvent.KEYCODE_BACK); press(activity, KeyEvent.KEYCODE_MENU)
            assertDock(activity, settings, language)
            val next = if (language in listOf("fa", "ar")) "en" else "ar"
            choice(activity, R.id.btn_settings_language, if (next == "ar") "العربية" else "English")
            assertDock(activity, settings, next)
            assertTrue(activity.findViewById<View>(R.id.btn_settings_language).isFocused)
            assertEquals(if (phone) activity.getString(R.string.touch_mode) else activity.getString(R.string.tv_mode), activity.findViewById<TextView>(R.id.tv_settings_mode_value).text.toString())
            activity.onConfigurationChanged(Configuration(activity.resources.configuration)); layout(activity)
            assertDock(activity, settings, next)
            assertEquals(if (phone) activity.getString(R.string.touch_mode) else activity.getString(R.string.tv_mode), activity.findViewById<TextView>(R.id.tv_settings_mode_value).text.toString())
            press(activity, KeyEvent.KEYCODE_BACK); press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertDock(activity, channels, next)
            assertTrue(list.isVerticalScrollBarEnabled)
        }
    }
    @Test fun `settings keep scrolling without bars while font and mode values stay consistent in both languages`() {
        for (language in listOf("en", "fa")) screen(language, phone = true, portrait = true) { activity ->
            press(activity, KeyEvent.KEYCODE_MENU)
            val scroll = activity.findViewById<ScrollView>(R.id.settings_root_scroll)
            assertFalse(scroll.isVerticalScrollBarEnabled)
            assertFalse(scroll.isHorizontalScrollBarEnabled)
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            assertTrue(scroll.scrollY > 0)
            val lang = activity.findViewById<Button>(R.id.btn_settings_language)
            val update = activity.findViewById<Button>(R.id.btn_settings_update)
            assertEquals(update.typeface, lang.typeface)
            assertEquals(update.textSize, lang.textSize, 0f)
            val value = activity.findViewById<TextView>(R.id.tv_settings_mode_value)
            assertEquals(activity.getString(R.string.touch_mode), value.text.toString())
            for (label in listOf(activity.getString(R.string.tv_mode), activity.getString(R.string.touch_mode), activity.getString(R.string.automatic))) {
                activity.findViewById<View>(R.id.btn_settings_mode).performClick(); layout(activity)
                val child = activity.findViewById<ViewGroup>(R.id.settings_pages).getChildAt(0) as ScrollView
                assertFalse(child.isVerticalScrollBarEnabled)
                assertFalse(child.isHorizontalScrollBarEnabled)
                descendants(child).filterIsInstance<Button>().first { it.text.toString().removePrefix("✓  ") == label }.performClick()
                layout(activity); assertEquals(label, value.text.toString())
            }
            press(activity, KeyEvent.KEYCODE_BACK); press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            assertTrue(list.isVerticalScrollBarEnabled)
            list.scrollToPosition(23); layout(activity)
            assertNotNull(list.findViewHolderForAdapterPosition(23))
        }
    }
    @Test fun `favourite responds to touch and remote and stays synchronized after reopening and changing channel`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(language, phone) { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            val star = activity.findViewById<ImageButton>(R.id.btn_osd_favourite)
            val repo = PlaylistRepository(activity)
            val channel = runBlocking { repo.loadCachedChannels() }.first()
            fun state(selected: Boolean) {
                assertEquals(selected, star.isSelected)
                assertEquals(selected, repo.isFavourite(channel))
                assertEquals(activity.getString(if (selected) R.string.favourite_remove_action else R.string.favourite_add_action), star.contentDescription.toString())
                val node = star.createAccessibilityNodeInfo()
                assertTrue(node.isCheckable); assertEquals(selected, node.isChecked)
            }
            state(false)
            star.performClick(); state(true)
            assertTrue(star.requestFocusFromTouch())
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER); state(false)
            assertEquals(star.contentDescription.toString(), activity.findViewById<TextView>(R.id.tv_osd_action_label).text.toString())
            star.performClick(); state(true)
            press(activity, KeyEvent.KEYCODE_BACK); press(activity, KeyEvent.KEYCODE_DPAD_DOWN); state(true)
            activity.findViewById<View>(R.id.btn_osd_next).performClick()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300)); layout(activity)
            assertFalse(star.isSelected)
            assertTrue(repo.isFavourite(channel))
        }
    }
}
