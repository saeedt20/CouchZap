package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
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
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], qualifiers = "w960dp-h540dp-land-mdpi")
class SearchTypingRegressionTest {
    private fun screen(phone: Boolean, language: String, check: (MainActivity) -> Unit) {
        RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).apply { setLanguage(language); setInterfaceMode(if (phone) "Phone" else "TV"); markIntroSeen() }
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val repo = PlaylistRepository(context); repo.clearPlaylist()
        runBlocking { repo.importPlaylistFromStream((1..160).joinToString("\n") {
            "${if (it <= 140) "World News $it" else "شبکه خبر $it"},file:///search-$it.ts"
        }.byteInputStream()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get(); val list = activity.uiView<RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != 160 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertEquals(160, list.adapter!!.itemCount)
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER); settle(activity)
            check(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.uiView<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenWidthDp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenHeightDp, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight); activity.layoutSettingsForTest(root.measuredWidth, root.measuredHeight)
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }
    private fun settle(activity: MainActivity) { repeat(3) { layout(activity); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(30)) } }

    @Test fun `typing words spaces and IME composition stays in search until explicit submission`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(phone, language) { activity ->
            val search = activity.uiView<EditText>(R.id.et_channel_search)
            val list = activity.uiView<RecyclerView>(R.id.rv_channels)
            assertTrue(search.requestFocusFromTouch())
            val input = search.onCreateInputConnection(EditorInfo())!!
            val words = if (language == "fa") listOf("ش", "شبکه", "شبکه ", "شبکه خبر") else listOf("W", "World", "World ", "World News")
            for (word in words) {
                assertTrue(input.setComposingText(word, 1)); settle(activity)
                assertTrue(search.isFocused)
                assertEquals(word, search.text.toString()); assertEquals(word.length, search.selectionEnd)
                assertEquals(0, BaseInputConnection.getComposingSpanStart(search.text))
                assertEquals(word.length, BaseInputConnection.getComposingSpanEnd(search.text))
                input.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED); settle(activity)
                assertTrue("implicit IME event interrupted $word", search.isFocused)
                assertEquals(word, search.text.toString()); assertEquals(word.length, search.selectionEnd)
            }
            input.finishComposingText()
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(search.text))
            assertEquals(if (language == "fa") 20 else 140, list.adapter!!.itemCount)
            input.performEditorAction(EditorInfo.IME_ACTION_SEARCH); settle(activity)
            assertFalse(search.isFocused); assertTrue(list.hasFocus())
            assertEquals(0, list.getChildAdapterPosition(list.findContainingItemView(list.findFocus()!!)!!))
            assertTrue(search.requestFocusFromTouch())
            search.setText("no matching station"); search.setSelection(search.length()); settle(activity)
            assertEquals(0, list.adapter!!.itemCount); assertTrue(search.isFocused)
            input.performEditorAction(EditorInfo.IME_ACTION_SEARCH); settle(activity)
            assertTrue(search.isFocused)
            search.setText(""); settle(activity)
            assertTrue(search.isFocused); assertEquals(160, list.adapter!!.itemCount)
        }
    }
    @Test fun `a delayed offscreen row request cannot reclaim focus after the user enters search`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(phone, language) { activity ->
            // These remote presses outrun layout and leave an offscreen request pending.
            repeat(80) { press(activity, KeyEvent.KEYCODE_DPAD_DOWN) }
            val search = activity.uiView<EditText>(R.id.et_channel_search)
            assertTrue(search.requestFocusFromTouch())
            val input = search.onCreateInputConnection(EditorInfo())!!
            input.commitText("World", 1); settle(activity)
            assertTrue("old row navigation stole the search", search.isFocused)
            input.commitText(" News", 1); settle(activity)
            assertTrue(search.isFocused)
            assertEquals("World News", search.text.toString()); assertEquals(10, search.selectionEnd)
        }
    }
}
