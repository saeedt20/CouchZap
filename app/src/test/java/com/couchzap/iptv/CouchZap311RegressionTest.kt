package com.couchzap.iptv

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.support.ReleaseUpdater
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.ui.design.ChoiceRow
import com.couchzap.iptv.util.Prefs
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 3.1.1 regression coverage for items 1-4 and version metadata. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CouchZap311RegressionTest {
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
    // ---------- Item 2: deterministic chevron direction ----------

    @Test fun `item2 fa row uses fixed left chevron`() = screen("fa") { activity ->
        openSettings(activity)
        val row = activity.uiView<SettingRow>(R.id.btn_settings_language)
        val arrow = descendants(row).filterIsInstance<ImageView>().firstOrNull { it.visibility == View.VISIBLE }
        assertNotNull("FA row must have a visible chevron", arrow)
        val drawable = arrow!!.drawable as android.graphics.drawable.VectorDrawable
        assertFalse("FA chevron must be a fixed asset, not auto-mirrored", drawable.isAutoMirrored)
    }

    @Test fun `item2 en row uses fixed right chevron`() = screen("en") { activity ->
        openSettings(activity)
        val row = activity.uiView<SettingRow>(R.id.btn_settings_language)
        val arrow = descendants(row).filterIsInstance<ImageView>().firstOrNull { it.visibility == View.VISIBLE }
        assertNotNull("EN row must have a visible chevron", arrow)
        val drawable = arrow!!.drawable as android.graphics.drawable.VectorDrawable
        assertFalse("EN chevron must be a fixed asset, not auto-mirrored", drawable.isAutoMirrored)
    }

    @Test fun `item2 sound row has no chevron`() = screen("fa") { activity ->
        openSettings(activity)
        val row = activity.uiView<SettingRow>(R.id.btn_settings_crt_row)
        assertFalse(row.navigates)
        val arrows = descendants(row).filterIsInstance<ImageView>().filter { it.visibility == View.VISIBLE }
        assertTrue("sound row must stay arrow-free", arrows.isEmpty())
    }

    @Test fun `item2 chevron drawables are not auto-mirrored`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val right = ctx.getDrawable(R.drawable.ic_chevron_row) as android.graphics.drawable.VectorDrawable
        val left = ctx.getDrawable(R.drawable.ic_chevron_row_left) as android.graphics.drawable.VectorDrawable
        assertFalse("right chevron must be fixed", right.isAutoMirrored)
        assertFalse("left chevron must be fixed", left.isAutoMirrored)
    }

    // ---------- Item 1: mobile About header ----------

    @Test fun `item1 mobile fa about header override is right`() = screen("fa", "Phone") { activity ->
        openSettings(activity)
        activity.uiView<SettingRow>(R.id.btn_settings_about).performClick()
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val panel = ReflectionHelpers.getField<SettingsPanel>(activity, "settingsPanel")
        assertEquals("about", panel.currentKey)
        assertEquals(R.drawable.ic_back_fixed_right, panel.headerBackIconOverride)
        activity.uiView<View>(R.id.settings_back).performClick()
        layout(activity)
        assertNull("About override must not leak into other pages", panel.headerBackIconOverride)
    }

    // ---------- Item 3: TV selected-value focus ----------

    @Test fun `item3 tv language page focuses selected language`() = screen("fa", "TV") { activity ->
        openSettings(activity)
        activity.uiView<SettingRow>(R.id.btn_settings_language).performClick()
        layout(activity)
        shadowOf(Looper.getMainLooper()).idle()
        val rows = descendants(activity.settingsWindowRoot()).filterIsInstance<ChoiceRow>()
        val selected = rows.single { it.isSelected }
        assertEquals("language/fa", selected.getTag(R.id.cz_action_key))
        assertTrue("configured Persian must receive initial focus", selected.isFocused)
    }

    // ---------- Item 4: rotation button ----------

    @Test fun `item4 rotate button exists on phone`() = screen("en", "Phone") { activity ->
        val btn = activity.uiView<View>(R.id.btn_rotate)
        assertNotNull("rotate button must exist", btn)
        assertEquals("phone quick rotation must be visible", View.VISIBLE, btn.visibility)
        assertTrue("phone quick rotation must be enabled", btn.isEnabled)
    }

    @Test fun `item4 rotate button hidden on tv`() = screen("en", "TV") { activity ->
        layout(activity)
        val btn = activity.uiView<View>(R.id.btn_rotate)
        // TV mode hides the button
        assertEquals("rotate button must be hidden on TV", View.GONE, btn.visibility)
    }

    @Test fun `item4 rotation strings exist in all locales`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        for (language in listOf("en", "es", "hi", "th", "fa", "ar")) {
            val config = Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val localized = ctx.createConfigurationContext(config)
            assertTrue(language, localized.getString(R.string.rotate_to_landscape).isNotBlank())
            assertTrue(language, localized.getString(R.string.rotate_to_portrait).isNotBlank())
        }
    }

    // ---------- Version ----------

    @Test fun `version 3_1_1 is newer than 3_1_0`() {
        assertTrue(ReleaseUpdater.isNewer("3.1.1", "3.1.0"))
        assertTrue(ReleaseUpdater.isNewer("3.1.1", "2.2.1"))
        assertFalse(ReleaseUpdater.isNewer("3.1.0", "3.1.1"))
    }
}
