package com.couchzap.iptv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

/** A focused full-screen fallback must never paint a pale overlay over its children. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], qualifiers = "w360dp-h800dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsFocusHaloRegressionTest {
    @Test fun `returning settings stays dark even when focus falls back to the full screen`() {
        for (language in listOf("en", "fa")) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
            Prefs(context).apply { setLanguage(language); setInterfaceMode("Phone"); markIntroSeen() }
            PlaylistRepository(context).clearPlaylist()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
            val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
            // Android 16 permits device resource overlays to enable the framework highlight.
            // Exercise the enabled variant as well as older Android's default behaviour.
            val deviceHighlight = if (android.os.Build.VERSION.SDK_INT >= 36)
                ReflectionHelpers.getStaticField<Boolean>(View::class.java, "sUseDefaultFocusHighlight") else null
            if (deviceHighlight != null) ReflectionHelpers.setStaticField(View::class.java, "sUseDefaultFocusHighlight", true)
            try {
                val activity = controller.get()
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
                val root = activity.settingsWindowRoot().findViewById<FrameLayout>(R.id.cz_settings_window_root)
                root.isFocusable = true; root.isFocusableInTouchMode = true
                root.descendantFocusability=android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
                assertFalse(activity.uiView<View>(R.id.root_container).defaultFocusHighlightEnabled)
                layout(root)
                val expected = activity.getColor(R.color.cz_surface_shell)
                for (id in listOf(R.id.btn_settings_tracks, R.id.btn_settings_mode, R.id.btn_settings_about)) {
                    activity.uiView<View>(id).performClick(); layout(root)
                    activity.uiView<View>(R.id.settings_back).performClick(); layout(root)
                    // Touch-mode changes and disappearing children may legitimately focus this
                    // keyboard fallback; its focus must not tint the entire playback/settings UI.
                    // Set the non-touch attachment state explicitly so both SDKs exercise
                    // keyboard focus independently of window-session emulation.
                    val attachment = ReflectionHelpers.getField<Any>(root, "mAttachInfo")
                    ReflectionHelpers.setField(attachment, "mInTouchMode", false)
                    assertTrue(activity.uiView<View>(id).requestFocus())
                    assertTrue(root.requestFocus())
                    assertTrue(root.isFocused)
                    root.jumpDrawablesToCurrentState()
                    val highlightDrawable = ReflectionHelpers.getField<Any?>(root, "mDefaultFocusHighlight")
                    val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)
                    try {
                        root.draw(Canvas(bitmap))
                        System.getenv("COUCHZAP_HALO_SCREENSHOTS_DIR")?.let { directory ->
                            File(directory).mkdirs()
                            File(directory, "settings-fallback-$language-api${android.os.Build.VERSION.SDK_INT}.png").outputStream().use {
                                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                            }
                        }
                        assertEquals("panel must retain its exact colour after Back", expected, bitmap.getPixel(20, 20))
                        // The software bitmap does not model every device ripple shader.
                        // Also verify that the framework never attaches an overlay drawable.
                        assertNull("full-screen native focus overlay must be absent", highlightDrawable)
                    } finally { bitmap.recycle() }
                    assertEquals(View.VISIBLE, activity.uiView<View>(R.id.layout_settings).visibility)
                }
            } finally {
                controller.destroy()
                if (deviceHighlight != null) ReflectionHelpers.setStaticField(View::class.java, "sUseDefaultFocusHighlight", deviceHighlight)
            }
        }
    }
    private fun layout(root: View) {
        shadowOf(Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 800)
    }
}
