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
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.design.ChoiceRow
import com.couchzap.iptv.util.Prefs
import com.couchzap.iptv.player.PlayerController
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import org.robolectric.util.ReflectionHelpers
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Selected-value focus, read-only track pages and orientation lifecycle regression tests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28,36],qualifiers="en-rUS-w960dp-h540dp-land-mdpi")
class SelectionAndRotation311ReviewProbeTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(a:MainActivity) {
        val c=a.resources.configuration;val w=c.screenWidthDp;val h=c.screenHeightDp
        val root=a.uiView<View>(R.id.root_container)
        repeat(3){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);a.layoutSettingsForTest(w,h)}
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun press(a:MainActivity,key:Int) {
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));layout(a)
    }
    private fun screen(lang:String="fa",phone:Boolean=false,prefsConfigure:(Prefs)->Unit={},run:(MainActivity)->Unit) {
        RuntimeEnvironment.setQualifiers(if(phone)"en-rUS-w412dp-h915dp-port-mdpi" else "en-rUS-w960dp-h540dp-land-mdpi")
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply{setLanguage(lang);setInterfaceMode(if(phone)"Phone" else "TV");setEffectsMode("safe");markIntroSeen();prefsConfigure(this)}
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val ctl=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {val a=ctl.get();layout(a);press(a,KeyEvent.KEYCODE_MENU);run(a)}finally{ctl.pause().stop().destroy()}
    }
    private fun checkSelectedFocus(a:MainActivity,id:Int,key:String) {
        val stored=a.getSharedPreferences("couchzap_prefs",0).all.toMap()
        a.uiView<View>(id).performClick();layout(a)
        val rows=views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>()
        val selected=rows.single{it.isSelected};val focused=rows.singleOrNull{it.isFocused}
        println("CHOICE sdk=${RuntimeEnvironment.getApiLevel()} page=$key selected=${selected.getTag(R.id.cz_action_key)} focused=${focused?.getTag(R.id.cz_action_key)} storedLang=${Prefs(a).getLanguage()} storedMode=${Prefs(a).getInterfaceMode()} storedEffects=${Prefs(a).getEffectsMode()}")
        assertTrue("selected row must get initial TV focus on $key",selected.isFocused)
        press(a,KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("moving focus must not commit a different selection",selected.isSelected)
        assertEquals(stored,a.getSharedPreferences("couchzap_prefs",0).all)
        press(a,KeyEvent.KEYCODE_BACK)
        assertTrue("Back must restore the originating row",a.uiView<View>(id).isFocused)
    }
    @Test fun selected_language_initial_focus()=screen{checkSelectedFocus(it,R.id.btn_settings_language,"language")}
    @Test fun selected_interface_mode_initial_focus()=screen{checkSelectedFocus(it,R.id.btn_settings_mode,"mode")}
    @Test fun selected_effects_initial_focus()=screen{checkSelectedFocus(it,R.id.btn_settings_effects,"effects")}
    @Test fun selected_picture_size_initial_focus()=screen(prefsConfigure={it.setAspectRatioMode(Prefs.ASPECT_4_3)}){checkSelectedFocus(it,R.id.btn_settings_aspect,"aspect")}
    @Test fun selected_sleep_initial_focus()=screen{a->
        ReflectionHelpers.setField(a,"sleepMinutes",60)
        checkSelectedFocus(a,R.id.btn_settings_sleep,"sleep")
    }
    private fun selectedTrack(type:Int)=screen{a->
        val player=PlayerController(a,a.uiView(R.id.surface_view),a.uiView(R.id.video_frame),a,a.uiView(R.id.subtitle_view))
        ReflectionHelpers.setField(a,"playerController",player)
        val mime=if(type==C.TRACK_TYPE_AUDIO)MimeTypes.AUDIO_AAC else MimeTypes.TEXT_VTT
        val group=TrackGroup("review-$type",*listOf("en","fa").map{Format.Builder().setSampleMimeType(mime).setLanguage(it).build()}.toTypedArray())
        ReflectionHelpers.setField(player,"currentTracks",Tracks(listOf(Tracks.Group(group,false,intArrayOf(C.FORMAT_HANDLED,C.FORMAT_HANDLED),booleanArrayOf(false,true)))))
        player.selectTrack(type,player.choices(type)[1])
        val exo=ReflectionHelpers.getField<ExoPlayer>(player,"exoPlayer")
        val before=exo.trackSelectionParameters
        a.uiView<View>(R.id.btn_settings_tracks).performClick();layout(a)
        views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>().single{it.getTag(R.id.cz_action_key)=="tracks/${if(type==C.TRACK_TYPE_AUDIO)0 else 1}"}.performClick();layout(a)
        val rows=views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>()
        assertEquals("track-$type/2",rows.single{it.isSelected}.getTag(R.id.cz_action_key))
        assertTrue(rows.single{it.isSelected}.isFocused)
        press(a,KeyEvent.KEYCODE_DPAD_UP)
        assertEquals("focusing another track must be read-only",before,exo.trackSelectionParameters)
        assertTrue(rows[2].isSelected)
    }
    @Test fun configured_audio_track_initial_focus_is_read_only()=selectedTrack(C.TRACK_TYPE_AUDIO)
    @Test fun configured_subtitle_track_initial_focus_is_read_only()=selectedTrack(C.TRACK_TYPE_TEXT)
    @Test fun mobile_configuration_callback_retains_a_manual_landscape_request()=screen(phone=true) {a->
        a.uiView<View>(R.id.btn_rotate).performClick()
        val before=a.requestedOrientation
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,before)
        val config=Configuration(a.resources.configuration).apply{orientation=Configuration.ORIENTATION_LANDSCAPE;screenWidthDp=915;screenHeightDp=412}
        a.onConfigurationChanged(config)
        println("ROTATION sdk=${RuntimeEnvironment.getApiLevel()} before=$before after=${a.requestedOrientation} interface=${Prefs(a).getInterfaceMode()}")
        assertEquals("manual landscape survives configuration callback",ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.requestedOrientation)
    }
    @Test fun saved_rtl_arrow_state_on_android7_and_newer()=screen {a->
        val row=a.uiView<SettingRow>(R.id.btn_settings_aspect)
        val image=views(row).filterIsInstance<ImageView>().single{it.visibility==View.VISIBLE}
        println("STATE sdk=${RuntimeEnvironment.getApiLevel()} contextDir=${a.resources.configuration.layoutDirection} row=${row.layoutDirection} image=${image.layoutDirection} drawable=${image.drawable.layoutDirection} mirrored=${image.drawable.isAutoMirrored}")
        assertEquals(View.LAYOUT_DIRECTION_RTL,image.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_RTL,image.drawable.layoutDirection)
    }
}
