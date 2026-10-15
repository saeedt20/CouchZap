package com.couchzap.iptv

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.ui.ChannelListAdapter
import com.couchzap.iptv.util.GroupLabels
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36], qualifiers = "w960dp-h540dp-land-mdpi")
class PersianLocalizationRegressionTest {
    private fun screen(phone: Boolean, language: String, check: (MainActivity) -> Unit) {
        RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs", 0).edit().clear().commit()
        Prefs(context).apply { setLanguage(language); setInterfaceMode(if (phone) "Phone" else "TV"); markIntroSeen() }
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val repo = PlaylistRepository(context); repo.clearPlaylist()
        val groups = listOf("Animation", "Auto", "Documentary", "My custom group", "Undefined", "Public")
        runBlocking { repo.importPlaylistFromStream(("#EXTM3U\n" + groups.mapIndexed { i, group ->
            "#EXTINF:-1 group-title=\"$group\",Station $i\nfile:///translation-$i.ts"
        }.joinToString("\n")).byteInputStream()) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get(); val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            val deadline = System.nanoTime() + 5_000_000_000L
            while (list.adapter!!.itemCount != 6 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
            assertEquals(6, list.adapter!!.itemCount); layout(activity); check(activity)
        } finally { controller.destroy() }
    }
    private fun layout(activity: MainActivity) {
        val root = activity.findViewById<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenWidthDp, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(activity.resources.configuration.screenHeightDp, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity)
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun choices(activity: MainActivity): List<Button> = descendants(activity.findViewById(R.id.settings_pages)).filterIsInstance<Button>()

    @Test fun `translated group picker and row labels preserve original filtering and custom playlist data`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(phone, language) { activity ->
            val repo = PlaylistRepository(activity); val original = runBlocking { repo.loadCachedChannels() }
            assertEquals(if (language == "fa") "خودرو" else "Auto", GroupLabels.display(activity, "Auto"))
            assertEquals(if (language == "fa") "نامشخص" else "Undefined", GroupLabels.display(activity, "Undefined"))
            assertEquals("My custom group", GroupLabels.display(activity, "My custom group"))
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            val list = activity.findViewById<RecyclerView>(R.id.rv_channels)
            assertEquals(if (language == "fa") "انیمیشن" else "Animation", list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<TextView>(R.id.tv_item_channel_group).text.toString())
            activity.findViewById<View>(R.id.btn_filter_groups).performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val names = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
            assertTrue(names.contains(if (language == "fa") "مستند" else "Documentary"))
            assertTrue(names.contains("My custom group"))
            val selected = names.indexOf(if (language == "fa") "مستند" else "Documentary")
            dialog.listView.performItemClick(View(activity), selected, dialog.listView.adapter.getItemId(selected)); layout(activity)
            assertEquals(1, list.adapter!!.itemCount)
            assertEquals("Documentary", (list.adapter as ChannelListAdapter).channelAt(0)!!.group)
            assertEquals(if (language == "fa") "مستند" else "Documentary", list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<TextView>(R.id.tv_item_channel_group).text.toString())
            assertEquals(original, runBlocking { repo.loadCachedChannels() })
        }
    }
    @Test fun `TV and ratio choices localize without changing saved mode or aspect and survive language changes`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) screen(phone, language) { activity ->
            press(activity, KeyEvent.KEYCODE_MENU)
            activity.findViewById<View>(R.id.btn_settings_mode).performClick(); layout(activity)
            val tv = if (language == "fa") "تلویزیون" else "TV"
            choices(activity).first { it.text.toString().removePrefix("✓  ") == tv }.performClick(); layout(activity)
            assertEquals("TV", Prefs(activity).getInterfaceMode())
            assertEquals(tv, activity.findViewById<TextView>(R.id.tv_settings_mode_value).text.toString())
            for ((ratio, mode) in listOf((if (language == "fa") "۱۶:۹" else "16:9") to Prefs.ASPECT_16_9,
                (if (language == "fa") "۴:۳" else "4:3") to Prefs.ASPECT_4_3)) {
                activity.findViewById<View>(R.id.btn_settings_aspect).performClick(); layout(activity)
                choices(activity).first { it.text.toString().removePrefix("✓  ") == ratio }.performClick(); layout(activity)
                assertEquals(mode, Prefs(activity).getAspectRatioMode())
                assertEquals(ratio, activity.findViewById<TextView>(R.id.tv_settings_aspect_value).text.toString())
            }
            activity.findViewById<View>(R.id.btn_settings_language).performClick(); layout(activity)
            choices(activity).first { it.text.toString().removePrefix("✓  ") == if (language == "fa") "English" else "فارسی" }.performClick(); layout(activity)
            assertEquals(if (language == "fa") "TV" else "تلویزیون", activity.findViewById<TextView>(R.id.tv_settings_mode_value).text.toString())
            assertEquals(if (language == "fa") "4:3" else "۴:۳", activity.findViewById<TextView>(R.id.tv_settings_aspect_value).text.toString())
            assertEquals(Prefs.ASPECT_4_3, Prefs(activity).getAspectRatioMode())
        }
    }
}
