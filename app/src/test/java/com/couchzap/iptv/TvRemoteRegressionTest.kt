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
import com.couchzap.iptv.player.ChannelPlaybackStatus
import com.couchzap.iptv.ui.ChannelListAdapter
import android.widget.EditText
import android.view.ViewGroup
import android.view.ViewConfiguration
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
    private fun withPlayback(count: Int = 2, test: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Prefs(context).setLanguage("en")
        Prefs(context).setInterfaceMode("TV")
        val repository = PlaylistRepository(context)
        repository.clearPlaylist()
        if (count > 0) runBlocking {
            repository.importPlaylistFromStream((0 until count).joinToString("\n") {
                "${when (it) { 0 -> "One"; 1 -> "Two"; else -> "Station $it" }},file:///test-$it.ts"
            }.byteInputStream())
        }
        // Use the real screen without starting a hardware decoder or network stream.
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != count && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertEquals(count, list.adapter!!.itemCount)
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

    private fun settle(activity: MainActivity) {
        repeat(4) {
            layout(activity)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
        }
    }

    private fun focusedIndex(activity: MainActivity): Int {
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        return list.findFocus()?.let(list::findContainingItemView)?.let(list::getChildAdapterPosition) ?: -1
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

    @Test fun `long list survives repeated arrows and failures without selecting a channel`() = withPlayback(240) { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        settle(activity)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        val adapter = list.adapter as ChannelListAdapter
        val current = adapter.channelAt(0)!!
        // Bursts deliberately outrun RecyclerView layout, including destinations outside the viewport.
        var expected = 0
        repeat(12) { burst ->
            repeat(57) { repeatIndex ->
                activity.dispatchKeyEvent(KeyEvent(0L, repeatIndex.toLong(), KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_DOWN, repeatIndex))
                expected = (expected + 1) % 240
                if (repeatIndex == 20) activity.onChannelFailed(current)
            }
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
            settle(activity)
            assertEquals("burst $burst", expected, focusedIndex(activity))
            assertMenuOpen(activity)
            assertFalse(activity.findViewById<View>(R.id.btn_retry).isFocusable)
            assertTrue(adapter.channelAt(0)!!.stableId == current.stableId)
            val selected = MainActivity::class.java.getDeclaredField("currentIndex").apply { isAccessible = true }.getInt(activity)
            assertEquals(0, selected)
            // Five minutes of virtual main-loop time across the twelve bursts.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
            assertEquals(expected, focusedIndex(activity))
        }
        press(activity, KeyEvent.KEYCODE_BACK)
        // Back closes one layer: the retained playback error becomes the active owner.
        assertTrue(activity.findViewById<View>(R.id.btn_retry).isFocused)
        press(activity, KeyEvent.KEYCODE_BACK)
        press(activity, KeyEvent.KEYCODE_DPAD_UP)
        settle(activity)
        assertEquals(expected, focusedIndex(activity))
    }

    @Test fun `offscreen wrap can be interrupted by close and reopened at its remembered destination`() = withPlayback(240) { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        press(activity, KeyEvent.KEYCODE_BACK)
        settle(activity)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.view_channel_list).visibility)
        assertFalse(activity.findViewById<RecyclerView>(R.id.rv_channels).hasFocus())
        press(activity, KeyEvent.KEYCODE_DPAD_UP)
        settle(activity)
        assertEquals(239, focusedIndex(activity))
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        settle(activity)
        assertEquals(0, focusedIndex(activity))
    }

    @Test fun `lost row focus recovers inside list and an empty search stays usable`() = withPlayback(240) { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        settle(activity)
        activity.findViewById<View>(R.id.root_container).requestFocus()
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        settle(activity)
        assertEquals(2, focusedIndex(activity))
        val search = activity.findViewById<EditText>(R.id.et_channel_search)
        search.requestFocusFromTouch()
        search.setText("no matches for this search")
        settle(activity)
        assertEquals(0, activity.findViewById<RecyclerView>(R.id.rv_channels).adapter!!.itemCount)
        repeat(8) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
        assertTrue(activity.findViewById<View>(R.id.view_channel_list).hasFocus())
        search.setText("Two")
        activity.findViewById<View>(R.id.btn_filter_all).performClick()
        settle(activity)
        assertEquals(0, focusedIndex(activity))
        repeat(3) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN); press(activity, KeyEvent.KEYCODE_DPAD_UP) }
        assertEquals(0, focusedIndex(activity))
    }

    @Test fun `failure cannot focus retry while settings owns the remote`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_MENU)
        val adapter = activity.findViewById<RecyclerView>(R.id.rv_channels).adapter as ChannelListAdapter
        activity.onChannelFailed(adapter.channelAt(0)!!)
        assertTrue(activity.findViewById<View>(R.id.layout_settings).hasFocus())
        val error = activity.findViewById<ViewGroup>(R.id.layout_playback_error)
        assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, error.descendantFocusability)
        repeat(15) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
        assertTrue(activity.findViewById<View>(R.id.layout_settings).hasFocus())
        press(activity, KeyEvent.KEYCODE_BACK)
        assertTrue(activity.findViewById<View>(R.id.btn_retry).isFocused)
    }

    @Test fun `status changes preserve row focus and ignore another channel failure`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
        val adapter = list.adapter as ChannelListAdapter
        val channel = adapter.channelAt(0)!!
        for ((state, label) in listOf(ChannelPlaybackStatus.CONNECTING to R.string.connecting,
            ChannelPlaybackStatus.PLAYING to R.string.playing, ChannelPlaybackStatus.BUFFERING to R.string.buffering,
            ChannelPlaybackStatus.PAUSED to R.string.paused, ChannelPlaybackStatus.UNAVAILABLE to R.string.channel_unavailable_short)) {
            activity.onChannelPlaybackChanged(channel, state)
            settle(activity)
            assertEquals(0, focusedIndex(activity))
            assertEquals(activity.getString(label), list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<TextView>(R.id.tv_item_channel_status).text.toString())
        }
        activity.onChannelFailed(adapter.channelAt(1)!!)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.layout_playback_error).visibility)
    }

    @Test fun `remote long OK toggles favourite once and its release does not select`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        val adapter = activity.findViewById<RecyclerView>(R.id.rv_channels).adapter as ChannelListAdapter
        val channel = adapter.channelAt(1)!!
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 20))
        settle(activity)
        activity.dispatchKeyEvent(KeyEvent(0, 1000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 2, 0, 0, 0, KeyEvent.FLAG_LONG_PRESS))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertMenuOpen(activity)
        assertTrue(PlaylistRepository(activity).isFavourite(channel))
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(View.GONE, activity.findViewById<View>(R.id.view_channel_list).visibility)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        settle(activity)
        assertEquals(1, focusedIndex(activity))
    }

    @Test fun `single channel wraps and welcome with no channels keeps normal focus`() {
        withPlayback(1) { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            repeat(5) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN); press(activity, KeyEvent.KEYCODE_DPAD_UP) }
            assertEquals(0, focusedIndex(activity))
        }
        withPlayback(0) { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.view_channel_list).visibility)
        }
    }

    @Test fun `window focus return restores the browsed row without reopening playback controls`() = withPlayback(240) { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        repeat(30) { activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)) }
        settle(activity)
        assertEquals(30, focusedIndex(activity))
        activity.onWindowFocusChanged(false)
        activity.findViewById<View>(R.id.root_container).requestFocus()
        activity.onWindowFocusChanged(true)
        settle(activity)
        assertEquals(30, focusedIndex(activity))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.view_osd).visibility)
    }

    @Test fun `OK commits the offscreen destination even before layout catches up`() = withPlayback(240) { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(239, MainActivity::class.java.getDeclaredField("currentIndex").apply { isAccessible = true }.getInt(activity))
        assertEquals(View.GONE, activity.findViewById<View>(R.id.view_channel_list).visibility)
    }

    @Test fun `losing window focus cancels a held OK without favouriting or selecting on return`() = withPlayback { activity ->
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        val adapter = activity.findViewById<RecyclerView>(R.id.rv_channels).adapter as ChannelListAdapter
        val channel = adapter.channelAt(1)!!
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        activity.onWindowFocusChanged(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 20))
        assertFalse(PlaylistRepository(activity).isFavourite(channel))
        activity.onWindowFocusChanged(true)
        settle(activity)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER))
        assertMenuOpen(activity)
        assertEquals(1, focusedIndex(activity))
    }
}
