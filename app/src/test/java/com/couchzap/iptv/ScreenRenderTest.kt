package com.couchzap.iptv

import android.content.Context
import android.graphics.*
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.media3.common.Format
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.EpgRepository
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
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Screenshots come from real Android views; programme data and backdrop are fixtures. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    @Test fun `native Groups keeps temporary focus separate from the selected source group`() {
        RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
        for(language in listOf("en","fa","ar"))render(true,false,language){a->
            press(a,KeyEvent.KEYCODE_DPAD_CENTER);a.uiView<View>(R.id.btn_filter_groups).performClick()
            fun dialog():android.app.Dialog {
                val d=org.robolectric.shadows.ShadowDialog.getLatestDialog();val root=d.window!!.decorView
                root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));root.layout(0,0,960,540)
                shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));return d
            }
            var d=dialog()
            descendantsForNative(d.window!!.decorView).filterIsInstance<com.couchzap.iptv.ui.design.ChoiceRow>().first{it.getTag(R.id.cz_action_key)=="Documentary"}.performClick();layout(a,960,540)
            assertEquals(1,a.uiView<RecyclerView>(R.id.rv_channels).adapter!!.itemCount)
            a.uiView<View>(R.id.btn_filter_groups).performClick();d=dialog()
            val rows=descendantsForNative(d.window!!.decorView).filterIsInstance<com.couchzap.iptv.ui.design.ChoiceRow>()
            val selected=rows.single{it.isSelected};assertEquals("Documentary",selected.getTag(R.id.cz_action_key))
            assertTrue(rows.first{it!==selected}.requestFocusFromTouch());assertTrue(selected.isSelected)
            save(a,"native-groups-$language",960,540)
            descendantsForNative(d.window!!.decorView).filterIsInstance<Button>().single{it.text==a.getString(R.string.cancel)}.performClick()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));layout(a,960,540)
            assertTrue(a.uiView<View>(R.id.btn_filter_groups).isFocused)
        }
    }
    @Test fun `native support states retain Cancel during progress and render diagnostics and about`() {
        for(language in listOf("en","fa","ar")){
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(true,false,language){a->
                a.useStableUpdateFixtureVersion()
                press(a,KeyEvent.KEYCODE_MENU)
                val panel=MainActivity::class.java.getDeclaredField("settingsPanel").apply{isAccessible=true}.get(a) as com.couchzap.iptv.ui.SettingsPanel
                val check=kotlinx.coroutines.CompletableDeferred<com.couchzap.iptv.support.ReleaseUpdater.Release>()
                val download=kotlinx.coroutines.CompletableDeferred<File>()
                var progress:((Int)->Unit)?=null
                val backend=object:com.couchzap.iptv.support.UpdateMenu.Backend {
                    override suspend fun latest()=check.await()
                    override suspend fun download(release:com.couchzap.iptv.support.ReleaseUpdater.Release,report:(Int)->Unit):File{progress=report;return download.await()}
                }
                val updater=com.couchzap.iptv.support.UpdateMenu(a,panel,backend)
                MainActivity::class.java.getDeclaredField("updateMenu").apply{isAccessible=true}.set(a,updater)
                a.uiView<View>(R.id.btn_settings_update).performClick();save(a,"native-update-checking-$language",960,540)
                check.complete(com.couchzap.iptv.support.ReleaseUpdater.Release("99.0.0","https://example.invalid/fixture.apk","",null,12345,"Native render fixture"))
                shadowOf(Looper.getMainLooper()).idle();save(a,"native-update-available-$language",960,540)
                fun action(label:String)=descendantsForNative(a.uiView(R.id.settings_pages)).first{it.isUiAction&&it.uiLabel().text==label}
                action(a.getString(R.string.download_update)).performClick();layout(a,960,540)
                val cancel=action(a.getString(R.string.cancel));cancel.requestFocusFromTouch()
                requireNotNull(progress)(37);shadowOf(Looper.getMainLooper()).idle()
                assertSame(cancel,action(a.getString(R.string.cancel)));assertTrue(cancel.isFocused)
                save(a,"native-update-downloading-$language",960,540)
                download.completeExceptionally(java.io.IOException("fixture"));shadowOf(Looper.getMainLooper()).idle();save(a,"native-update-failed-$language",960,540)
                press(a,KeyEvent.KEYCODE_BACK)
                a.uiView<View>(R.id.btn_settings_logs).performClick();save(a,"native-diagnostics-$language",960,540)
                press(a,KeyEvent.KEYCODE_BACK)
                a.uiView<View>(R.id.btn_settings_about).performClick();save(a,"native-about-$language",960,540)
            }
        }
    }
    @Test
    @Config(sdk=[28,36])
    fun `adaptive native controls remain reachable across phone tablet short windows and large scripts`() {
        data class Window(val width:Int,val height:Int,val scale:Float,val tv:Boolean=false)
        val windows=listOf(Window(360,800,1f),Window(412,915,1.3f),Window(800,360,1f),Window(360,800,2f),Window(600,800,1.3f),Window(840,800,1f),Window(960,540,2f,true))
        try{for(size in windows)for(language in if(size.scale==2f&&!size.tv)listOf("en","es","hi","th","fa","ar")else listOf("en","fa","ar")) {
            RuntimeEnvironment.setQualifiers("w${size.width}dp-h${size.height}dp-${if(size.width>size.height)"land" else "port"}-mdpi")
            RuntimeEnvironment.setFontScale(size.scale)
            val stem="native-adaptive-${size.width}x${size.height}-${size.scale}-$language"
            fun key(activity:MainActivity,code:Int){activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code));activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code));layout(activity,size.width,size.height)}
            render(false,!size.tv,language){activity->
                layout(activity,size.width,size.height)
                assertEquals(activity.uiView<View>(R.id.btn_import).width,activity.uiView<View>(R.id.btn_public).width)
                save(activity,"$stem-welcome",size.width,size.height)
                activity.uiView<View>(R.id.btn_import_language).performClick();layout(activity,size.width,size.height)
                val choices=descendantsForNative(activity.uiView(R.id.settings_pages)).filterIsInstance<com.couchzap.iptv.ui.design.ChoiceRow>()
                assertEquals(6,choices.size)
                save(activity,"$stem-language-entry",size.width,size.height)
                for((index,row) in choices.withIndex()){
                    // Item 7: rows are keyboard/D-pad focusable but not touch-focusable in production.
                    // Temporarily allow touch focus for this focusability check.
                    row.isFocusableInTouchMode = true
                    assertTrue("$stem language $index focus",row.requestFocusFromTouch())
                    row.isFocusableInTouchMode = false
                    shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32));layout(activity,size.width,size.height)
                    val bounds=Rect();assertTrue("$stem language $index not visible: row=${row.width}x${row.height} parent=${(row.parent as View).height}",row.getGlobalVisibleRect(bounds))
                    assertTrue("$stem language $index clipped: $bounds height=${row.height}",bounds.height()>=48)
                }
                save(activity,"$stem-language",size.width,size.height)
            }
            render(true,!size.tv,language){activity->
                key(activity,KeyEvent.KEYCODE_DPAD_DOWN)
                fun assertControls(){for(id in listOf(R.id.btn_osd_prev,R.id.btn_osd_play_pause,R.id.btn_osd_next,R.id.btn_osd_favourite,R.id.btn_osd_channels,R.id.btn_osd_aspect,R.id.btn_osd_settings)){
                    val view=activity.uiView<View>(id);val rect=Rect();assertTrue("$stem control $id should be visible",view.getGlobalVisibleRect(rect));assertTrue("$stem control $id clipped: $rect",rect.width()>=48&&rect.height()>=48)
                }}
                assertControls();save(activity,"$stem-info",size.width,size.height)
                activity.onTracksUpdated(Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build(),Format.Builder().setSampleMimeType("audio/mp4a-latm").build())
                key(activity,KeyEvent.KEYCODE_INFO)
                assertEquals("$stem Info details must expand",View.VISIBLE,activity.uiView<View>(R.id.cz_info_details_scroll).visibility)
                assertControls();save(activity,"$stem-info-expanded",size.width,size.height)

            }
        }}finally{RuntimeEnvironment.setFontScale(1f)}
    }
    @Test
    @Config(sdk=[36],qualifiers="w800dp-h360dp-land-mdpi")
    fun `native IME insets retain search caret a usable row and Close on a short phone`() {
        for(language in listOf("en","fa","ar"))render(true,true,language){activity->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));layout(activity,800,360)
            val search=activity.uiView<android.widget.EditText>(R.id.et_channel_search)
            search.setText("World");search.requestFocusFromTouch();search.setSelection(3)
            val insets=androidx.core.view.WindowInsetsCompat.Builder().setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.NONE).setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,220)).setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(activity.uiView(R.id.root_container),insets);layout(activity,800,360)
            assertEquals("World",search.text.toString());assertEquals(3,search.selectionStart)
            assertEquals(140,activity.uiView<View>(R.id.view_channel_list).height)
            assertTrue(activity.uiView<View>(R.id.btn_close_channel_list).isShown)
            assertTrue(activity.uiView<RecyclerView>(R.id.rv_channels).height>=48)
            save(activity,"native-phone-short-ime-$language-api36",800,360)
        }
    }
    @Test fun `native progress fixtures show Full depth at five explicit presentation positions`() {
        RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
        render(true,false){activity->
            press(activity,KeyEvent.KEYCODE_MENU)
            val host=MainActivity::class.java.getDeclaredField("settingsHost").apply{isAccessible=true}.get(activity) as com.couchzap.iptv.ui.settings.SettingsWindowHost
            host.motion.closeImmediately();host.motion.open(com.couchzap.iptv.ui.settings.EffectsTier.FULL);host.motion.beginDrag()
            for(percent in listOf(0,25,50,75,100)){host.motion.drag(percent/100f);save(activity,"native-progress-full-$percent",960,540)}
            host.motion.closeImmediately()
            assertEquals(1f,activity.uiView<View>(R.id.cz_video_scene).scaleX,0f)
        }
    }
    @Test fun `native source checkpoint covers loading failure retry results and empty catalog`() {
        for(language in listOf("en","fa","ar")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(false,false,language){activity->
                val panel=MainActivity::class.java.getDeclaredField("settingsPanel").apply{isAccessible=true}.get(activity) as com.couchzap.iptv.ui.SettingsPanel
                val reply=kotlinx.coroutines.CompletableDeferred<List<com.couchzap.iptv.data.PublicCatalog.Entry>>()
                var attempts=0
                val menus=com.couchzap.iptv.ui.PlaylistMenus(activity,PlaylistRepository(activity),activity.lifecycleScope,{}, {_,_->},{},panel,{}, {_,_->
                    if(attempts++==0)reply.await() else (0 until 240).map{com.couchzap.iptv.data.PublicCatalog.Entry("Country %03d".format(it),"c$it")}
                })
                MainActivity::class.java.getDeclaredField("playlistMenus").apply{isAccessible=true}.set(activity,menus)
                fun choose(label:String){descendantsForNative(activity.uiView(R.id.settings_pages)).first{it.isUiAction&&it.uiLabel().text.toString()==label}.performClick();layout(activity,960,540)}
                activity.uiView<View>(R.id.btn_import).performClick();save(activity,"native-own-playlist-$language",960,540)
                choose(activity.getString(R.string.import_link));save(activity,"native-source-link-$language",960,540)
                val input=descendantsForNative(activity.uiView(R.id.settings_pages)).filterIsInstance<android.widget.EditText>().single()
                input.setText("https://");choose(activity.getString(R.string.import_action));save(activity,"native-source-link-error-$language",960,540)
                press(activity,KeyEvent.KEYCODE_BACK);press(activity,KeyEvent.KEYCODE_BACK)
                activity.uiView<View>(R.id.btn_public).performClick();save(activity,"native-providers-$language",960,540)
                choose("iptv-org");save(activity,"native-provider-choices-$language",960,540)
                choose(activity.getString(R.string.by_country));save(activity,"native-catalog-loading-$language",960,540)
                reply.completeExceptionally(java.io.IOException("fixture"));shadowOf(Looper.getMainLooper()).idle();save(activity,"native-catalog-failure-$language",960,540)
                choose(activity.getString(R.string.retry));shadowOf(Looper.getMainLooper()).idle();layout(activity,960,540)
                val options=descendantsForNative(activity.uiView(R.id.settings_pages)).filterIsInstance<com.couchzap.iptv.ui.design.CatalogOptionsView>().single()
                assertEquals(240,options.list.adapter!!.itemCount);save(activity,"native-catalog-results-$language",960,540)
                options.search.setText("no matching country");save(activity,"native-catalog-empty-$language",960,540)
            }
        }
    }
    @Test fun `native design viewing checkpoint keeps focus playing and favourite independent`() {
        for(language in listOf("en","fa","ar")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(true,false,language) { activity ->
                press(activity,KeyEvent.KEYCODE_DPAD_CENTER)
                val list=activity.uiView<RecyclerView>(R.id.rv_channels)
                val first=list.findViewHolderForAdapterPosition(0)!!.itemView
                val second=list.findViewHolderForAdapterPosition(1)!!.itemView
                assertTrue(first.isSelected)
                second.requestFocusFromTouch()
                assertTrue(second.isFocused);assertFalse(second.isSelected);assertTrue(first.isSelected)
                assertTrue(first.findViewById<View>(R.id.cz_channel_favourite).isSelected)
                save(activity,"native-channels-tv-$language",960,540)
                press(activity,KeyEvent.KEYCODE_BACK)
                press(activity,KeyEvent.KEYCODE_DPAD_DOWN)
                val dock=activity.uiView<View>(R.id.view_osd)
                assertEquals(864,dock.width)
                assertEquals(513,dock.bottom)
                assertTrue("Compact TV dock should stay near 200dp without guide data: ${dock.height}",dock.height in 176..220)
                save(activity,"native-info-tv-$language",960,540)
                val failed=runBlocking{PlaylistRepository(activity).loadCachedChannels()}.first()
                activity.onChannelFailed(failed);layout(activity,960,540)
                assertTrue(activity.uiView<View>(R.id.btn_retry).height>=56)
                save(activity,"native-playback-error-tv-$language",960,540)
            }
        }
    }
    @Test fun `native Settings checkpoint renders retained real window roots and choices`() {
        for(language in listOf("en","fa","ar")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(true,false,language) { activity ->
                press(activity,KeyEvent.KEYCODE_MENU)
                save(activity,"native-settings-playback-$language",960,540)
                activity.uiView<View>(R.id.btn_settings_crt_row).performClick()
                save(activity,"native-settings-sound-on-$language",960,540)
                activity.uiView<View>(R.id.cz_category_playlist).performClick()
                save(activity,"native-settings-playlist-$language",960,540)
                activity.uiView<View>(R.id.cz_category_app).performClick()
                save(activity,"native-settings-app-$language",960,540)
                activity.uiView<View>(R.id.btn_settings_language).performClick()
                save(activity,"native-language-tv-$language",960,540)
                activity.uiView<View>(R.id.settings_back).performClick()
                activity.uiView<View>(R.id.cz_category_playback).performClick()
                activity.uiView<View>(R.id.btn_settings_aspect).performClick()
                save(activity,"native-aspect-tv-$language",960,540)
                val choices=descendantsForNative(activity.uiView(R.id.settings_pages)).filterIsInstance<com.couchzap.iptv.ui.design.ChoiceRow>()
                assertEquals(4,choices.size);assertEquals(1,choices.count{it.isSelected})
            }
        }
    }
    private fun descendantsForNative(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{descendantsForNative(view.getChildAt(it))}else emptyList()
    @Test fun `welcome actions fit TV and phone and use a shared width`() {
        render(false, false) { activity ->
            val first = activity.uiView<View>(R.id.btn_import)
            val second = activity.uiView<View>(R.id.btn_public)
            assertEquals(first.width, second.width)
            assertTrue(first.width >= 320)
            // Item 7: btn_import is keyboard/D-pad focusable but not touch-focusable in production.
            // Temporarily allow touch focus for this focusability check.
            first.isFocusableInTouchMode = true
            assertTrue(first.requestFocusFromTouch())
            first.isFocusableInTouchMode = false
            assertTrue(first.isFocused)
            val bounds = Rect(); second.getGlobalVisibleRect(bounds)
            assertEquals(second.height, bounds.height())
            save(activity, "welcome-tv", 960, 540)
        }
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
        render(false, true) { activity ->
            val first = activity.uiView<View>(R.id.btn_import)
            assertTrue(first.width <= 360)
            save(activity, "welcome-phone", 360, 800)
        }
    }
    @Test fun `native channel settings and Info views render with demo data`() {
        render(true, false) { activity ->
            press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
            save(activity, "channels-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            press(activity, KeyEvent.KEYCODE_MENU)
            assertEquals(48, activity.uiView<View>(R.id.layout_settings).left)
            save(activity, "settings-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            activity.onTracksUpdated(Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).setFrameRate(25f).setAverageBitrate(3100000).build(),
                Format.Builder().setSampleMimeType("audio/mp4a-latm").setChannelCount(2).setSampleRate(48000).build())
            activity.onPlaybackInfo(true, true, activity.getString(R.string.live))
            press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
            save(activity, "info-tv", 960, 540)
            press(activity, KeyEvent.KEYCODE_INFO)
            assertEquals(View.VISIBLE, activity.uiView<View>(R.id.tv_osd_technical).visibility)
            save(activity, "info-details-tv", 960, 540)
        }
    }
    @Test fun `stage two English and Persian settings render from native Android views`() {
        for (language in listOf("en", "fa")) render(false, false, language) { activity ->
            press(activity, KeyEvent.KEYCODE_MENU)
            save(activity, "stage2-settings-$language", 960, 540)
            activity.uiView<View>(R.id.btn_settings_about).performClick()
            save(activity, "stage2-about-$language", 960, 540)
            press(activity, KeyEvent.KEYCODE_BACK)
            activity.uiView<View>(R.id.btn_settings_reimport).performClick()
            val pages = activity.uiView<ViewGroup>(R.id.settings_pages)
            fun findButton(view: View): View? {
                if (view.isUiAction && view.uiLabel().text == activity.getString(R.string.import_link)) return view
                if (view is ViewGroup) for (i in 0 until view.childCount) findButton(view.getChildAt(i))?.let { return it }
                return null
            }
            requireNotNull(findButton(pages)).performClick()
            save(activity, "stage2-link-$language", 960, 540)
        }
        render(false, false, null) { activity -> save(activity, "stage2-first-language", 960, 540) }
    }
    @Test fun `welcome adapts to all six languages on TV and phone`() {
        for (language in listOf("en", "es", "hi", "th", "fa", "ar")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(false, false, language) { activity ->
                val first = activity.uiView<View>(R.id.btn_import)
                val second = activity.uiView<View>(R.id.btn_public)
                val rect = Rect(); assertTrue(second.getGlobalVisibleRect(rect)); assertEquals(second.height, rect.height())
                assertEquals(first.width, second.width)
                val columns = activity.uiView<android.widget.LinearLayout>(R.id.import_columns)
                assertEquals(android.widget.LinearLayout.HORIZONTAL, columns.orientation)
                save(activity, "stage3-welcome-tv-$language", 960, 540)
                second.performClick(); save(activity, "stage3-providers-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                assertEquals(View.GONE, activity.uiView<View>(R.id.layout_settings).visibility)
            }
            RuntimeEnvironment.setQualifiers("w360dp-h800dp-port-mdpi")
            render(false, true, language) { activity ->
                assertEquals(android.widget.LinearLayout.VERTICAL, activity.uiView<android.widget.LinearLayout>(R.id.import_columns).orientation)
                assertTrue(activity.uiView<View>(R.id.btn_import).width <= 320)
                save(activity, "stage3-welcome-phone-$language", 360, 800)
            }
        }
    }
    @Test fun `all six languages share safe panel geometry`() {
        for (language in listOf("en", "es", "hi", "th", "fa", "ar")) {
            RuntimeEnvironment.setQualifiers("w960dp-h540dp-land-mdpi")
            render(false, false, language) { activity -> save(activity, "stage4-welcome-$language", 960, 540) }
            render(true, false, language) { activity ->
                press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
                val channels = activity.uiView<View>(R.id.view_channel_list)
                val settings = activity.uiView<View>(R.id.layout_settings)
                assertEquals(400,channels.width)
                assertEquals(if (language in listOf("fa", "ar")) 912 else 448, channels.right)
                assertEquals(20,channels.paddingStart)
                val list = activity.uiView<RecyclerView>(R.id.rv_channels)
                assertTrue(list.findViewHolderForAdapterPosition(0)!!.itemView.height >= 48)
                save(activity, "stage4-channels-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
                val info = activity.uiView<View>(R.id.view_osd)
                assertTrue(info.left >= 48)
                assertTrue(info.top >= 27)
                assertTrue(info.right <= 912)
                val clock = activity.uiView<View>(R.id.tv_osd_clock)
                val name = activity.uiView<View>(R.id.tv_osd_channel_name)
                val clockBounds = Rect(); clock.getGlobalVisibleRect(clockBounds)
                val nameBounds = Rect(); name.getGlobalVisibleRect(nameBounds)
                assertTrue(clockBounds.right <= nameBounds.left || nameBounds.right <= clockBounds.left)
                save(activity, "stage4-info-$language", 960, 540)
                press(activity, KeyEvent.KEYCODE_BACK)
                press(activity, KeyEvent.KEYCODE_MENU)
                assertEquals(720,settings.width)
                assertEquals(if (language in listOf("fa", "ar")) 912 else 48, if (language in listOf("fa", "ar")) settings.right else settings.left)
                val toggle = activity.uiView<View>(R.id.tv_settings_crt_value)
                assertTrue(activity.uiView<View>(R.id.btn_settings_crt_row).height >= 64)
                save(activity, "stage4-settings-$language", 960, 540)
            }
        }
    }
    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h800dp-port-mdpi")
    fun `Android 16 settings render after tapping the child page Back button`() {
        for (language in listOf("en", "fa")) render(false, true, language) { activity ->
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU))
            layout(activity, 360, 800)
            activity.uiView<View>(R.id.btn_settings_mode).performClick()
            layout(activity, 360, 800)
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            activity.uiView<View>(R.id.settings_back).performClick()
            layout(activity, 360, 800)
            val scroll = activity.uiView<android.widget.ScrollView>(R.id.settings_root_scroll)
            scroll.scrollTo(0, scroll.getChildAt(0).height)
            save(activity, "ui-fix-settings-phone-$language-api36", 360, 800)
        }
    }
    @Test
    @Config(sdk = [28, 36])
    fun `favourite stays visibly distinct while focused and unfocused on phone and TV`() {
        for (phone in listOf(false, true)) for (language in listOf("en", "fa")) {
            RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
            render(true, phone, language) { activity ->
                val width = if (phone) 360 else 960
                val height = if (phone) 800 else 540
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
                activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
                layout(activity, width, height)
                val star = activity.uiView<android.widget.ImageButton>(R.id.btn_osd_favourite)
                assertTrue(star.isSelected)
                assertTrue(star.requestFocusFromTouch())
                fun centerPixel(): Int {
                    val bitmap = Bitmap.createBitmap(star.width, star.height, Bitmap.Config.ARGB_8888)
                    star.draw(Canvas(bitmap))
                    val color = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                    bitmap.recycle(); return color
                }
                val stem = "stage7-info-$language-${if (phone) "phone" else "tv"}-api${android.os.Build.VERSION.SDK_INT}"
                assertEquals(activity.getColor(R.color.item_focus_text), centerPixel())
                save(activity, "$stem-favourite-on-focused", width, height)
                star.performClick()
                assertFalse(star.isSelected)
                assertTrue(star.isFocused)
                assertEquals(activity.getColor(R.color.item_focus), centerPixel())
                save(activity, "$stem-favourite-off-focused", width, height)
                star.clearFocus()
                assertEquals(Color.TRANSPARENT, centerPixel())
                save(activity, "$stem-favourite-off", width, height)
                star.performClick()
                assertTrue(star.isSelected)
                assertEquals(activity.getColor(R.color.accent_gold), centerPixel())
                assertEquals(star.contentDescription.toString(), activity.uiView<android.widget.TextView>(R.id.tv_osd_action_label).text.toString())
                save(activity, "$stem-favourite-on", width, height)
            }
        }
    }
    @Test
    @Config(sdk = [28, 36])
    fun `Persian mode ratios and group labels render with correctly ordered digits and continuous search`() {
        for (phone in listOf(false, true)) {
            RuntimeEnvironment.setQualifiers(if (phone) "w360dp-h800dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
            render(true, phone, "fa") { activity ->
                val width = if (phone) 360 else 960; val height = if (phone) 800 else 540
                fun key(code: Int) {
                    activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code)); layout(activity, width, height)
                }
                fun buttons(root: View): List<android.widget.TextView> = if(root.isUiAction) listOf(root.uiLabel()) else if (root is ViewGroup)
                    (0 until root.childCount).flatMap { buttons(root.getChildAt(it)) } else emptyList()
                val stem = "stage8-fa-${if (phone) "phone" else "tv"}-api${android.os.Build.VERSION.SDK_INT}"
                key(KeyEvent.KEYCODE_MENU)
                activity.uiView<View>(R.id.btn_settings_mode).performClick(); layout(activity, width, height)
                assertTrue(buttons(activity.uiView(R.id.settings_pages)).any { it.text.toString().contains("تلویزیون") })
                save(activity, "$stem-mode", width, height)
                activity.uiView<View>(R.id.settings_back).performClick()
                activity.uiView<View>(R.id.btn_settings_aspect).performClick(); layout(activity, width, height)
                for (ratio in listOf("۱۶:۹", "۴:۳")) {
                    val button = buttons(activity.uiView(R.id.settings_pages)).first { it.text.toString().contains(ratio) }
                    val text = button.text.toString(); val start = text.indexOf(ratio)
                    val first = Path(); val last = Path()
                    button.layout.getSelectionPath(start, start + 1, first)
                    button.layout.getSelectionPath(start + ratio.length - 1, start + ratio.length, last)
                    val firstBounds = RectF(); val lastBounds = RectF()
                    first.computeBounds(firstBounds, true); last.computeBounds(lastBounds, true)
                    assertTrue("ratio reversed: $ratio", firstBounds.left < lastBounds.left)
                }
                save(activity, "$stem-aspect", width, height)
                key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_BACK); if(activity.uiView<View>(R.id.layout_settings).visibility==View.VISIBLE)key(KeyEvent.KEYCODE_BACK); key(KeyEvent.KEYCODE_DPAD_CENTER)
                val search = activity.uiView<android.widget.EditText>(R.id.et_channel_search)
                assertTrue(search.requestFocusFromTouch())
                val input = search.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
                for (word in listOf("World", "World ", "World News")) {
                    input.setComposingText(word, 1); input.performEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_UNSPECIFIED)
                    layout(activity, width, height); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(80))
                    assertTrue(search.isFocused)
                }
                assertEquals("World News", search.text.toString())
                val list = activity.uiView<RecyclerView>(R.id.rv_channels)
                assertEquals("اخبار", list.findViewHolderForAdapterPosition(0)!!.itemView.findViewById<android.widget.TextView>(R.id.tv_item_channel_group).text.toString())
                save(activity, "$stem-search", width, height)
            }
        }
    }
    private fun render(playlist: Boolean, phone: Boolean, language: String? = "en", block: (MainActivity) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (language == null) context.getSharedPreferences("couchzap_prefs", 0).edit().remove("language").commit()
        else Prefs(context).setLanguage(language)
        Prefs(context).markIntroSeen()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        Prefs(context).setInterfaceMode(if (phone) "Phone" else "TV")
        val repo = PlaylistRepository(context); repo.clearPlaylist(); EpgRepository(context).clear()
        if (playlist) runBlocking {
            repo.importPlaylistFromStream("""#EXTM3U
                #EXTINF:-1 tvg-id="nature" group-title="Documentary",Nature
                file:///demo/nature.mp4
                #EXTINF:-1 tvg-id="news" group-title="News",World News
                file:///demo/news.mp4
                #EXTINF:-1 tvg-id="music" group-title="Music",Studio Sessions
                file:///demo/music.mp4
                #EXTINF:-1 tvg-id="family" group-title="Family",Family
                file:///demo/family.mp4
                #EXTINF:-1 tvg-id="sports" group-title="Sports",Live Sports
                file:///demo/sports.mp4
                #EXTINF:-1 tvg-id="cinema" group-title="Movies",Cinema
                file:///demo/cinema.mp4""".trimIndent().byteInputStream(), com.couchzap.iptv.data.PlaylistSource("file", "file:///demo/channels.m3u", "Demo playlist"))
            val channels = repo.loadCachedChannels()
            for (channel in listOf(channels[0], channels[2])) if (!repo.isFavourite(channel)) repo.toggleFavourite(channel)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try {
            val activity = controller.get()
            if (playlist) {
                val list = activity.uiView<RecyclerView>(R.id.rv_channels)
                val deadline = System.nanoTime() + 5_000_000_000L
                while (list.adapter!!.itemCount != 6 && System.nanoTime() < deadline) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(5) }
                assertEquals(6, list.adapter!!.itemCount)
                val root = activity.uiView<FrameLayout>(R.id.root_container)
                activity.uiView<FrameLayout>(R.id.cz_video_scene).addView(DemoBackdrop(context), 1, FrameLayout.LayoutParams(-1, -1))
            }
            layout(activity, activity.resources.configuration.screenWidthDp, activity.resources.configuration.screenHeightDp)
            block(activity)
        } finally { controller.destroy() }
    }
    private fun press(activity: MainActivity, key: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)); layout(activity, 960, 540)
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)); layout(activity, 960, 540)
    }
    private fun layout(activity: MainActivity, width: Int, height: Int) {
        val root = activity.uiView<View>(R.id.root_container)
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height); activity.layoutSettingsForTest(width, height)
        // A bounds listener can request a second native layout for the new composition.
        root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
        root.layout(0,0,width,height);activity.layoutSettingsForTest(width,height)
    }
    private fun save(activity: MainActivity, name: String, width: Int, height: Int) {
        val directory = System.getenv("COUCHZAP_SCREENSHOTS_DIR") ?: return
        layout(activity, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        activity.uiView<View>(R.id.root_container).draw(canvas)
        if(activity.uiView<View>(R.id.layout_settings).visibility==View.VISIBLE)activity.settingsWindowRoot().draw(canvas)
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf{it.isShowing&&it.window?.decorView!==activity.settingsWindowRoot()}?.window?.decorView?.draw(canvas)
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private class DemoBackdrop(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.parseColor("#52655D"))
            paint.color = Color.parseColor("#A8B6A5"); canvas.drawCircle(width * .77f, height * .28f, height * .07f, paint)
            listOf("#41584C", "#304638", "#203629").forEachIndexed { index, color ->
                val path = Path().apply {
                    moveTo(0f, height * (.5f + index * .1f))
                    cubicTo(width * .3f, height * (.2f + index * .1f), width * .6f, height * (.8f - index * .05f), width.toFloat(), height * (.45f + index * .16f))
                    lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close()
                }
                paint.color = Color.parseColor(color); canvas.drawPath(path, paint)
            }
        }
    }
}
