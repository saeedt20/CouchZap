package com.couchzap.iptv

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "w360dp-h800dp-port")
class MainActivityTest {
    @Test fun `phone mode permits rotation and remote menu works before import`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs(context).setLanguage("en")
        Prefs(context).setInterfaceMode("Phone")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.layout_settings).visibility)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layout_settings).visibility)
        controller.destroy()
    }

    @Test fun `manual TV mode locks landscape even on a phone configuration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs(context).setLanguage("en")
        Prefs(context).setInterfaceMode("TV")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, activity.requestedOrientation)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.btn_quick_settings).visibility)
        val icon = requireNotNull(androidx.core.content.ContextCompat.getDrawable(activity, R.mipmap.ic_launcher))
        assertTrue(icon.intrinsicWidth > 0)
        assertTrue(icon.intrinsicHeight > 0)
        val bitmap = android.graphics.Bitmap.createBitmap(108, 108, android.graphics.Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 108, 108)
        icon.draw(android.graphics.Canvas(bitmap))
        controller.destroy()
    }
}
