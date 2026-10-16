package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], qualifiers = "w960dp-h540dp-land-mdpi")
class SettingsVisualRegressionTest {
    private fun screen(language: String, phone: Boolean = false, check: (MainActivity) -> Unit) {
        RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).apply { setLanguage(language); setInterfaceMode(if (phone) "Phone" else "TV"); markIntroSeen() }
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        PlaylistRepository(context).clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
            layout(activity)
            check(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.uiView<View>(R.id.root_container)
        val width = activity.resources.configuration.screenWidthDp
        val height = activity.resources.configuration.screenHeightDp
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height); activity.layoutSettingsForTest(width, height)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun `sound label and On Off value have separate bounds and one checkable remote owner`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(language, phone) { activity ->
            val row = activity.uiView<View>(R.id.btn_settings_crt_row)
            val label = activity.uiView<TextView>(R.id.tv_settings_crt_label)
            val toggle = activity.uiView<TextView>(R.id.tv_settings_crt_value)
            if (language == "en") assertTrue(label.right < toggle.left) else assertTrue(toggle.right < label.left)
            assertTrue("label must retain a useful text column", label.width >= 120)
            assertEquals(activity.getString(R.string.crt_sound), label.text.toString())
            assertFalse(toggle.isFocusable)
            // Item 7: rows are keyboard/D-pad focusable but not touch-focusable in production.
            // For this focusability check, temporarily allow touch focus.
            row.isFocusableInTouchMode = true
            assertTrue("sound focus phone=$phone language=$language shown=${row.isShown} visibility=${row.visibility} panelFocus=${activity.uiView<View>(R.id.layout_settings).findFocus()?.id}", row.requestFocusFromTouch())
            row.isFocusableInTouchMode = false
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(activity.getString(R.string.cz_on), toggle.text.toString())
            assertTrue(Prefs(activity).isCrtSoundEnabled())
            val node = row.createAccessibilityNodeInfo()
            assertEquals("android.widget.Switch", node.className.toString())
            assertTrue(node.isCheckable); assertTrue(node.isChecked)
            row.performClick()
            assertEquals(activity.getString(R.string.off), toggle.text.toString())
            assertFalse(Prefs(activity).isCrtSoundEnabled())
        }
    }
    @Test fun `language and update rows have the same visible gap as adjacent settings`() {
        for (language in listOf("en", "fa")) screen(language, phone = true) { activity ->
            val languageRow = activity.uiView<View>(R.id.btn_settings_language)
            val update = activity.uiView<View>(R.id.btn_settings_update)
            val logs = activity.uiView<View>(R.id.btn_settings_logs)
            val effects = activity.uiView<View>(R.id.btn_settings_effects)
            assertEquals(1,effects.top-languageRow.bottom)
            assertEquals(1,update.top-effects.bottom)
            assertEquals(1,logs.top-update.bottom)
        }
    }
    @Test fun `header Back repeatedly restores flat settings without changing scroll or accumulating elevation`() {
        for (language in listOf("en", "fa")) screen(language, phone = true) { activity ->
            val scroll = activity.uiView<ScrollView>(R.id.settings_root_scroll)
            for (id in listOf(R.id.btn_settings_tracks, R.id.btn_settings_mode, R.id.btn_settings_language, R.id.btn_settings_about)) {
                val origin = activity.uiView<View>(id)
                repeat(3) {
                    origin.requestFocusFromTouch(); layout(activity)
                    val position = scroll.scrollY
                    origin.performClick(); layout(activity)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
                    assertFlat(activity.uiView(R.id.settings_pages))
                    val back = activity.uiView<View>(R.id.settings_back)
                    assertEquals(View.VISIBLE, back.visibility)
                    back.isPressed = true; back.performClick(); back.isPressed = false
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500)); layout(activity)
                    assertSame(scroll, activity.uiView<ViewGroup>(R.id.settings_pages).getChildAt(0))
                    assertEquals(position, scroll.scrollY)
                    assertTrue(origin.isFocused)
                    assertFlat(scroll)
                    assertEquals(1f, scroll.alpha, 0f)
                    assertEquals(0f, scroll.translationX, 0f)
                }
            }
        }
    }
    private fun assertFlat(root: View) {
        descendants(root).filter { it.isUiAction }.forEach {
            assertEquals(0f, it.elevation, 0f)
            assertEquals(0f, it.translationZ, 0f)
            assertNull(it.stateListAnimator)
        }
    }
}
