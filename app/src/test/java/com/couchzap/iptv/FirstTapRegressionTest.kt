package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.design.ChoiceRow
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Item 7: ordinary mobile buttons must activate on the first tap.
 * Uses real MotionEvent DOWN/UP sequences, never performClick() or pre-focus,
 * so a first-tap-focus swallow would fail the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class FirstTapRegressionTest {
    private fun screen(test: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        val prefs = Prefs(context); prefs.setInterfaceMode("phone"); prefs.setLanguage("en")
        PlaylistRepository(context).clearPlaylist()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            layout(activity); test(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.uiView<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(412, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(915, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 412, 915); activity.layoutSettingsForTest(412, 915)
        shadowOf(Looper.getMainLooper()).idle()
    }
    /** Sends a real DOWN/UP touch sequence to the view. Returns the click count. */
    private fun tap(view: View): Int {
        var clicks = 0
        view.setOnClickListener { clicks++ }
        tapNoListener(view)
        shadowOf(Looper.getMainLooper()).idle()
        view.setOnClickListener(null)
        return clicks
    }
    /** Sends a real DOWN/UP without touching the click listener (for wrapped listeners). */
    private fun tapNoListener(view: View) {
        if (view.width == 0 || view.height == 0) {
            view.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        }
        val now = SystemClock.uptimeMillis()
        val cx = view.width / 2f; val cy = view.height / 2f
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cx, cy, 0)
        val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, cx, cy, 0)
        try {
            view.dispatchTouchEvent(down)
            view.dispatchTouchEvent(up)
        } finally { down.recycle(); up.recycle() }
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun openSettings(activity: MainActivity) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
        layout(activity)
        assertEquals(View.VISIBLE, activity.uiView<View>(R.id.layout_settings).visibility)
    }

    @Test fun `unfocused SettingRow fires on first tap`() = screen { activity ->
        val row = SettingRow(activity).apply {
            id = View.generateViewId(); isClickable = true
            // Attach so focus/touch dispatch behaves like a real window.
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        // Sanity: the policy must be applied.
        assertFalse(row.isFocusableInTouchMode)
        assertTrue(row.isFocusable)
        assertEquals("one DOWN/UP must fire exactly one click", 1, tap(row))
    }

    @Test fun `unfocused ChoiceRow fires on first tap`() = screen { activity ->
        val row = ChoiceRow(activity).apply {
            id = View.generateViewId()
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        assertFalse(row.isFocusableInTouchMode)
        assertEquals("one DOWN/UP must fire exactly one click", 1, tap(row))
    }

    @Test fun `settings back button returns one page on first tap`() = screen { activity ->
        openSettings(activity)
        val panel = activity.uiView<View>(R.id.settings_pages)
        // Open a child page so Back is visible (setup via performClick; the real-touch
        // assertion below is on the Back button itself).
        val rows = descendants(panel).filterIsInstance<SettingRow>()
        assertTrue("expected settings rows", rows.isNotEmpty())
        rows.first().performClick()
        layout(activity)
        val back = activity.uiView<View>(R.id.settings_back)
        assertEquals(View.VISIBLE, back.visibility)
        assertFalse(back.isFocusableInTouchMode)
        // Wrap the original listener to count without breaking navigation.
        var clicks = 0
        val liField = View::class.java.getDeclaredField("mListenerInfo").apply { isAccessible = true }
        val li = liField.get(back)
        val ocField = Class.forName("android.view.View\$ListenerInfo").getDeclaredField("mOnClickListener").apply { isAccessible = true }
        val orig = ocField.get(li) as View.OnClickListener?
        back.setOnClickListener { clicks++; orig?.onClick(back) }
        // One real DOWN/UP gesture must fire exactly one click and return to root.
        tapNoListener(back)
        layout(activity)
        assertEquals("one DOWN/UP on Back must fire exactly one click", 1, clicks)
        assertEquals("Back must return to the root page", View.GONE, activity.uiView<View>(R.id.settings_back).visibility)
    }

    @Test fun `touch-focusable view swallows first tap proving the mechanism`() = screen { activity ->
        // Simulates the pre-fix configuration: an unfocused touch-focusable view
        // takes focus on ACTION_DOWN (View#focusTaken), so ACTION_UP skips performClick.
        val oldStyle = View(activity).apply {
            isFocusable = true; isFocusableInTouchMode = true; isClickable = true
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        assertFalse(oldStyle.isFocused)
        assertEquals("old touch-focusable config must swallow the first tap", 0, tap(oldStyle))
        // With the item-7 policy the same gesture fires.
        val fixed = View(activity).apply {
            isFocusable = true; isClickable = true
            com.couchzap.iptv.ui.design.TouchActivationPolicy.apply(this)
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        assertEquals("policy config must fire on the first tap", 1, tap(fixed))
    }

    @Test fun `settings close button fires on first tap`() = screen { activity ->
        openSettings(activity)
        val close = activity.uiView<View>(R.id.btn_settings_close)
        assertFalse(close.isFocusableInTouchMode)
        // Tapping Close must fire its action once (we only assert the click, not the close, to avoid lifecycle coupling).
        assertEquals("one DOWN/UP must fire exactly one click", 1, tap(close))
    }

    @Test fun `dpad focus still works when touch focus is disabled`() = screen { activity ->
        openSettings(activity)
        val row = descendants(activity.uiView<View>(R.id.settings_pages)).filterIsInstance<SettingRow>().first()
        // Keyboard/D-pad focus must still be grantable via requestFocus (non-touch path).
        assertTrue(row.requestFocus())
        assertTrue(row.isFocused)
    }

    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
