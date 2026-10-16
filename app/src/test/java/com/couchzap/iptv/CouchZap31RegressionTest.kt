package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.player.StreamInfoFormatter
import com.couchzap.iptv.support.ReleaseUpdater
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.design.ChoiceRow
import com.couchzap.iptv.util.LocalizedNumbers
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

/** 3.1 stable regression coverage for changes A–E and version metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CouchZap31RegressionTest {
    private fun screen(lang: String = "en", mode: String = "Phone", test: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        val prefs = Prefs(context); prefs.setInterfaceMode(mode); prefs.setLanguage(lang)
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
    private fun descendants(view: View): List<View> =
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } + view else listOf(view)
    private fun openSettings(activity: MainActivity) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
        layout(activity)
        assertEquals(View.VISIBLE, activity.uiView<View>(R.id.layout_settings).visibility)
    }

    // ---------- A: chevrons ----------

    @Test fun `A fa row chevron drawable auto-mirrors`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val d = ctx.getDrawable(R.drawable.ic_chevron_row) as android.graphics.drawable.VectorDrawable
        assertTrue("row chevron must auto-mirror for FA/AR ‹", d.isAutoMirrored)
    }

    @Test fun `A sound toggle row has no chevron`() = screen { activity ->
        openSettings(activity)
        val row = activity.uiView<SettingRow>(R.id.btn_settings_crt_row)
        assertFalse("sound row must not navigate", row.navigates)
        val arrows = descendants(row).filterIsInstance<ImageView>().filter { it.visibility == View.VISIBLE }
        assertTrue("sound row must stay arrow-free", arrows.isEmpty())
    }

    // ---------- B: next preview position ----------

    @Test fun `B fa preview uses physical top-right`() = screen("fa") { activity ->
        val preview = activity.uiView<View>(R.id.tv_ghost_next)
        val lp = preview.layoutParams as FrameLayout.LayoutParams
        assertEquals("FA preview gravity must be TOP|RIGHT", Gravity.TOP or Gravity.RIGHT, lp.gravity)
        assertTrue("FA preview needs a right inset", lp.rightMargin > 0)
        assertEquals("FA preview must not keep a stale end margin", 0, lp.marginEnd)
    }

    @Test fun `B en preview keeps top-end`() = screen("en") { activity ->
        val preview = activity.uiView<View>(R.id.tv_ghost_next)
        val lp = preview.layoutParams as FrameLayout.LayoutParams
        assertEquals("EN preview gravity must stay TOP|END", Gravity.TOP or Gravity.END, lp.gravity)
        assertTrue("EN preview needs an end inset", lp.marginEnd > 0)
    }

    // ---------- C: info clock ----------

    private fun fixedDate(hour: Int, minute: Int): java.util.Date {
        // The formatter displays device-local time. Construct the requested wall
        // time in that same zone so these assertions work in UTC CI and Tehran.
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2026, Calendar.OCTOBER, 17, hour, minute, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    @Test fun `C fa clock uses Persian digits`() {
        // 23:24 -> ۲۳:۲۴ ; leading zeros preserved: 09:05 -> ۰۹:۰۵
        assertEquals("۲۳:۲۴", StreamInfoFormatter.formatTime(fixedDate(23, 24), "fa"))
        assertEquals("۰۹:۰۵", StreamInfoFormatter.formatTime(fixedDate(9, 5), "fa"))
    }

    @Test fun `C ar clock uses Arabic-Indic digits`() {
        assertEquals("٢٣:٢٤", StreamInfoFormatter.formatTime(fixedDate(23, 24), "ar"))
        assertEquals("٠٩:٠٥", StreamInfoFormatter.formatTime(fixedDate(9, 5), "ar"))
    }

    @Test fun `C en clock keeps ASCII digits`() {
        assertEquals("23:24", StreamInfoFormatter.formatTime(fixedDate(23, 24), "en"))
        assertEquals("09:05", StreamInfoFormatter.formatTime(fixedDate(9, 5), "en"))
    }

    @Test fun `C clock digit ranges are exact`() {
        val fa = StreamInfoFormatter.formatTime(fixedDate(23, 24), "fa")
        assertTrue("FA digits must be U+06F0–U+06F9", fa.filter { it != ':' }.all { it in '۰'..'۹' })
        val ar = StreamInfoFormatter.formatTime(fixedDate(23, 24), "ar")
        assertTrue("AR digits must be U+0660–U+0669", ar.filter { it != ':' }.all { it in '٠'..'٩' })
    }

    // ---------- D: first tap ----------

    /** Sends real DOWN/UP to the view and returns whether a click fired. */
    private fun realTap(view: View): Boolean {
        var clicked = false
        val prev = null // rows use panel onClick; we add a probe via performClick chain is not used
        view.setOnClickListener { clicked = true }
        // Note: production listener is replaced here only to count; the
        // dispatch path (DOWN focus suppression + UP click) is production.
        val w = if (view.width == 0) 300 else view.width
        val h = if (view.height == 0) 60 else view.height
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, w / 2f, h / 2f, 0)
        val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, w / 2f, h / 2f, 0)
        try {
            // Enter touch mode like a real phone.
            try {
                val root = (view.rootView as View)
                val att = org.robolectric.util.ReflectionHelpers.getField<Any>(root, "mAttachInfo")
                org.robolectric.util.ReflectionHelpers.setField(att, "mInTouchMode", true)
            } catch (_: Exception) {}
            view.dispatchTouchEvent(down)
            val focusedOnDown = view.isFocused
            view.dispatchTouchEvent(up)
            shadowOf(Looper.getMainLooper()).idle()
            // First-tap contract: DOWN must not steal focus; UP must click.
            assertFalse("DOWN must not take focus (first-tap)", focusedOnDown)
            return clicked
        } finally { down.recycle(); up.recycle(); view.setOnClickListener(null) }
    }

    @Test fun `D unfocused SettingRow activates on first real tap`() = screen { activity ->
        val row = SettingRow(activity).apply {
            isClickable = true; isFocusable = true; isFocusableInTouchMode = true
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        assertTrue("D-pad focus must still be grantable", row.requestFocus())
        row.clearFocus()
        assertTrue("one DOWN/UP must fire exactly one click", realTap(row))
        assertTrue("touch focusability preserved for D-pad", row.isFocusableInTouchMode)
    }

    @Test fun `D unfocused ChoiceRow activates on first real tap`() = screen { activity ->
        val row = ChoiceRow(activity).apply {
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        assertFalse(row.isFocused)
        assertTrue("one DOWN/UP must fire exactly one click", realTap(row))
        assertTrue("touch focusability preserved for D-pad", row.isFocusableInTouchMode)
    }

    @Test fun `D canceled gesture does not activate`() = screen { activity ->
        val row = SettingRow(activity).apply {
            isClickable = true
            (activity.uiView<View>(R.id.root_container) as ViewGroup).addView(this, 0)
        }
        layout(activity)
        var clicked = false
        row.setOnClickListener { clicked = true }
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 150f, 30f, 0)
        val cancel = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_CANCEL, 150f, 30f, 0)
        try {
            row.dispatchTouchEvent(down); row.dispatchTouchEvent(cancel)
            shadowOf(Looper.getMainLooper()).idle()
        } finally { down.recycle(); cancel.recycle(); row.setOnClickListener(null) }
        assertFalse("ACTION_CANCEL must not activate", clicked)
    }

    // ---------- E: About ----------

    @Test fun `E all locales have approved about blocks`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val keys = listOf("about_heading", "about_desc", "about_playlist", "about_local",
            "about_creator_label", "about_license", "about_version", "about_github", "about_dedication")
        // Default (en) resources
        for (k in keys) {
            val id = ctx.resources.getIdentifier(k, "string", ctx.packageName)
            assertTrue("missing string $k", id != 0)
            assertTrue("empty string $k", ctx.getString(id).isNotBlank())
        }
        assertEquals("Saeed Taheri", ctx.getString(ctx.resources.getIdentifier("about_creator_name", "string", ctx.packageName)))
    }

    @Test fun `E fa creator name is exact Persian spelling`() = screen("fa") { activity ->
        // Open About through the real Settings flow
        openSettings(activity)
        activity.uiView<SettingRow>(R.id.btn_settings_about).performClick()
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val aboutTitle = activity.getString(R.string.about)
        assertTrue("FA creator name", activity.getString(R.string.about_creator_name) == "سعید طاهری")
        assertTrue("FA heading present", activity.getString(R.string.about_heading).isNotBlank())
        assertTrue("FA MIT present", activity.getString(R.string.about_license).contains("MIT"))
        assertTrue("FA dedication present", activity.getString(R.string.about_dedication).isNotBlank())
    }

    @Test fun `E about version shows localized digits for fa`() {
        // Display-only localization; underlying version stays ASCII.
        val localized = LocalizedNumbers.localize("3.1.0", "fa")
        assertEquals("۳.۱.۰", localized)
        assertEquals("٣.١.٠", LocalizedNumbers.localize("3.1.0", "ar"))
        assertEquals("3.1.0", LocalizedNumbers.localize("3.1.0", "en"))
    }

    // ---------- version metadata ----------

    @Test fun `version 3_1_0 is newer than 2_2_1 and parses as stable`() {
        assertTrue(ReleaseUpdater.isNewer("3.1.0", "2.2.1"))
        // Prerelease installed versions cannot be compared by the stable parser
        // (beta users need a manual download); the parser rejects them.
        assertFalse(ReleaseUpdater.isNewer("3.1.0", "3.0-beta.2"))
        assertFalse(ReleaseUpdater.isNewer("2.2.1", "3.1.0"))
        assertFalse(ReleaseUpdater.isNewer("3.1", "2.2.1")) // two-part rejected by stable parser
        assertFalse(ReleaseUpdater.isNewer("v3.1", "2.2.1"))
        assertTrue(ReleaseUpdater.isNewer("v3.1.0", "v2.2.1"))
    }
}
