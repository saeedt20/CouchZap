package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.data.PlaylistSource
import com.couchzap.iptv.data.PublicCatalog
import com.couchzap.iptv.ui.PlaylistMenus
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], qualifiers = "w960dp-h540dp-land-mdpi")
class CatalogRemoteRegressionTest {
    private fun layout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 960, 540)
    }
    private fun settle(activity: MainActivity) { shadowOf(Looper.getMainLooper()).idle(); layout(activity) }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); settle(activity)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun choices(activity: MainActivity) = descendants(activity.findViewById(R.id.settings_pages)).filterIsInstance<Button>()
    private fun choose(activity: MainActivity, label: String) {
        choices(activity).first { it.text.toString().removePrefix("✓  ") == label }.performClick(); settle(activity)
    }
    private fun catalog(language: String = "en", check: (MainActivity, MutableList<PlaylistSource>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).apply { setLanguage(language); setInterfaceMode("TV"); markIntroSeen() }
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val repo = PlaylistRepository(context); repo.clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get(); layout(activity)
            val panel = MainActivity::class.java.getDeclaredField("settingsPanel").apply { isAccessible = true }.get(activity) as SettingsPanel
            val accepted = mutableListOf<PlaylistSource>()
            val menus = PlaylistMenus(activity, repo, activity.lifecycleScope, {}, { source, _ -> accepted.add(source) }, {}, panel,
                { if (activity.findViewById<View>(R.id.layout_settings).visibility != View.VISIBLE) press(activity, KeyEvent.KEYCODE_MENU) },
                { _, _ -> (0 until 240).map { PublicCatalog.Entry("Country %03d".format(it), "c$it") } })
            menus.show(); choose(activity, activity.getString(R.string.public_channels)); choose(activity, "iptv-org")
            choose(activity, activity.getString(R.string.by_country)); settle(activity)
            assertEquals(240, choices(activity).size)
            check(activity, accepted)
        } finally { controller.destroy() }
    }
    @Test fun `remote visits every country in both directions instead of jumping across a long list`() = catalog { activity, accepted ->
        val rows = choices(activity)
        val search = descendants(activity.findViewById(R.id.settings_pages)).filterIsInstance<EditText>().single()
        assertTrue(search.requestFocusFromTouch()); settle(activity)
        assertSame(search, activity.findViewById<View>(R.id.layout_settings).findFocus())
        for (index in 0 until 120) {
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertSame("Down must reach country $index", rows[index], activity.findViewById<View>(R.id.layout_settings).findFocus())
        }
        for (index in 118 downTo 50) {
            press(activity, KeyEvent.KEYCODE_DPAD_UP)
            assertSame("Up must reach country $index", rows[index], activity.findViewById<View>(R.id.layout_settings).findFocus())
        }
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("c50", accepted.single().code)
    }
    @Test fun `lost catalog focus recovers to search before moving to the first country`() = catalog { activity, _ ->
        val panel = activity.findViewById<View>(R.id.layout_settings)
        val root = activity.findViewById<View>(R.id.root_container)
        assertTrue(root.requestFocusFromTouch()); settle(activity)
        assertFalse(panel.hasFocus())
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(panel.findFocus() is EditText)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertSame(choices(activity).first(), panel.findFocus())
    }

    @Test fun `filtered and empty catalogs keep ordered remote focus across LTR RTL and held keys`() {
        for (language in listOf("en", "fa", "ar")) catalog(language) { activity, accepted ->
            val panel = activity.findViewById<View>(R.id.layout_settings)
            val search = descendants(activity.findViewById(R.id.settings_pages)).filterIsInstance<EditText>().single()
            search.setText("Country 1"); search.requestFocusFromTouch(); settle(activity)
            val filtered = choices(activity)
            assertEquals(100, filtered.size)
            repeat(56) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
            assertSame(filtered[55], panel.findFocus())
            press(activity, KeyEvent.KEYCODE_DPAD_UP)
            assertSame(filtered[54], panel.findFocus())
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("c154", accepted.single().code)

            search.requestFocusFromTouch(); search.setText("no matches"); settle(activity)
            assertTrue(choices(activity).isEmpty())
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertSame(search, panel.findFocus())
            search.setText("Country 2"); settle(activity)
            val shorter = choices(activity)
            assertEquals(40, shorter.size)
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN); assertSame(shorter[0], panel.findFocus())
            press(activity, KeyEvent.KEYCODE_DPAD_UP); assertSame(search, panel.findFocus())
            val time = android.os.SystemClock.uptimeMillis()
            repeat(45) { count ->
                activity.dispatchKeyEvent(KeyEvent(time, time + count * 80L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, count))
            }
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)); settle(activity)
            assertSame(shorter.last(), panel.findFocus())
            press(activity, KeyEvent.KEYCODE_DPAD_UP); assertSame(shorter[38], panel.findFocus())
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertEquals("c238", accepted.last().code)
        }
    }

}
