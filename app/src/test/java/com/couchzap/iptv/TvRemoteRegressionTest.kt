package com.couchzap.iptv

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], qualifiers = "w960dp-h540dp-land-mdpi")
class TvRemoteRegressionTest {
    private fun withPlayback(test: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs(context).setInterfaceMode("TV")
        val repository = PlaylistRepository(context)
        repository.clearPlaylist()
        runBlocking {
            repository.importPlaylistFromStream("One,file:///test-one.ts\nTwo,file:///test-two.ts".byteInputStream())
        }
        // Use the real screen without starting a hardware decoder or network stream.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != 2 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertEquals(2, list.adapter!!.itemCount)
            layout(activity)
            test(activity)
        } finally {
            controller.destroy()
        }
    }

    private fun layout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 960, 540)
    }

    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        layout(activity)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun assertMenuOpen(activity: MainActivity) {
        val panel = activity.findViewById<View>(R.id.view_channel_list)
        assertEquals(View.VISIBLE, panel.visibility)
        assertEquals(1f, panel.alpha, 0f)
        assertEquals(0f, panel.translationX, 0f)
        assertEquals(View.LAYER_TYPE_NONE, panel.layerType)
    }

    @Test fun `channel names and numbers remain readable as remote focus moves`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        val current = list.findViewHolderForAdapterPosition(0)!!.itemView
        val next = list.findViewHolderForAdapterPosition(1)!!.itemView
        fun assertText(row: View, number: String, name: String, focused: Boolean) {
            assertEquals(focused, row.isFocused)
            val numberView = row.findViewById<TextView>(R.id.tv_item_channel_num)
            val nameView = row.findViewById<TextView>(R.id.tv_item_channel_name)
            assertEquals(number, numberView.text.toString())
            assertEquals(name, nameView.text.toString())
            if (focused || row.isPressed) {
                assertEquals(Color.parseColor("#182116"), numberView.currentTextColor)
                assertEquals(Color.parseColor("#182116"), nameView.currentTextColor)
            } else {
                assertEquals(Color.parseColor("#B7C9AC"), numberView.currentTextColor)
                assertEquals(Color.parseColor("#F2EFE7"), nameView.currentTextColor)
            }
        }
        assertTrue(current.isSelected)
        assertText(current, "001", "One", true)
        repeat(5) {
            // Activity dispatch alone does not run ViewRootImpl's default D-pad
            // focus traversal in this harness; exercise that traversal explicitly.
            assertSame(next, current.focusSearch(View.FOCUS_DOWN))
            assertTrue(next.requestFocus())
            assertText(next, "002", "Two", true)
            assertText(current, "001", "One", false)
            assertSame(current, next.focusSearch(View.FOCUS_UP))
            assertTrue(current.requestFocus())
            assertText(current, "001", "One", true)
        }
        // Touch feedback uses the same light background as remote focus.
        current.clearFocus()
        next.requestFocus()
        current.isPressed = true
        assertText(current, "001", "One", false)
        current.isPressed = false
        assertText(current, "001", "One", false)
    }

    @Test fun `OK opens channels after controls time out and extended idle playback`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.view_osd).visibility)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(20))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.view_osd).visibility)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertMenuOpen(activity)
    }

    @Test fun `OK release and rapid close reopen cannot dismiss or offset the menu`() = withPlayback { activity ->
        repeat(15) {
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            assertMenuOpen(activity)
            press(activity, KeyEvent.KEYCODE_BACK)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.view_channel_list).visibility)
        }
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertMenuOpen(activity)
    }

    @Test fun `missing OK release does not block a later fresh OK press`() = withPlayback { activity ->
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        layout(activity)
        assertMenuOpen(activity)
        press(activity, KeyEvent.KEYCODE_BACK)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertMenuOpen(activity)
    }

    @Test fun `channel panel docks left with overscan safe content and full list bounds`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val panel = activity.findViewById<View>(R.id.view_channel_list)
        val params = panel.layoutParams as FrameLayout.LayoutParams
        assertEquals(0, params.leftMargin)
        assertEquals(0, panel.left)
        assertTrue(panel.paddingLeft >= 48)
        assertTrue(panel.paddingTop >= 27)
        assertTrue(panel.paddingBottom >= 27)
        assertTrue(panel.bottom <= 540)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        assertTrue(list.bottom <= panel.height - panel.paddingBottom)
        assertNull(list.itemAnimator)
    }

    @Test fun `remote wraps first and last visible channels without changing playback`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        val first = list.findViewHolderForAdapterPosition(0)!!.itemView
        val last = list.findViewHolderForAdapterPosition(1)!!.itemView
        assertTrue(first.isSelected)
        assertTrue(first.isFocused)
        press(activity, KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(last.isFocused)
        assertTrue(first.isSelected)
        assertFalse(last.isSelected)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(first.isFocused)
        assertTrue(first.isSelected)
    }

    @Test fun `favourites filter keeps global channel numbers and empty results are usable`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        list.findViewHolderForAdapterPosition(1)!!.itemView.performLongClick()
        layout(activity)
        activity.findViewById<View>(R.id.btn_filter_favourites).performClick()
        layout(activity)
        assertEquals(1, list.adapter!!.itemCount)
        assertEquals("002", list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<TextView>(R.id.tv_item_channel_num).text.toString())
        list.findViewHolderForAdapterPosition(0)!!.itemView.performLongClick()
        layout(activity)
        assertEquals(0, list.adapter!!.itemCount)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.tv_empty_channels).visibility)
    }
}
