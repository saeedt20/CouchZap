package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.util.Prefs
import com.couchzap.iptv.ui.design.ChoiceRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,36],qualifiers="w960dp-h540dp-land-mdpi")
class SettingsWindowRegressionTest {
    private fun screen(language:String,check:(MainActivity)->Unit){
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(context).apply{setLanguage(language);setInterfaceMode("TV");markIntroSeen()}
        PlaylistRepository(context).clearPlaylist()
        android.provider.Settings.Global.putFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().visible()
        try{val a=controller.get();layout(a);press(a,KeyEvent.KEYCODE_MENU);check(a)}finally{controller.destroy()}
    }
    private fun layout(a:MainActivity){val root=a.uiView<View>(R.id.root_container);root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));root.layout(0,0,960,540);a.layoutSettingsForTest(960,540)}
    private fun press(a:MainActivity,key:Int){a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));layout(a)}
    private fun all(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{all(v.getChildAt(it))}else emptyList()
    @Test fun `six locales navigate the actual rail detail and nested windows with exact return focus`() {
        for(language in listOf("en","es","hi","th","fa","ar"))screen(language){a->
            val rtl=language in listOf("fa","ar")
            val into=if(rtl)KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val out=if(rtl)KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            val panel=a.uiView<View>(R.id.layout_settings)
            assertNull("Settings must be in its actual owned window",a.findViewById<View>(R.id.layout_settings))
            assertEquals(if(rtl)192 else 48,panel.left);assertEquals(27,panel.top);assertEquals(720,panel.width);assertEquals(486,panel.height)
            assertTrue(a.uiView<View>(R.id.cz_category_playback).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_UP);assertTrue(a.uiView<View>(R.id.cz_category_playback).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(a.uiView<View>(R.id.cz_category_playlist).isSelected)
            press(a,into);assertTrue(a.uiView<View>(R.id.btn_settings_reimport).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(a.uiView<View>(R.id.btn_settings_guide).isFocused)
            val scroll=a.uiView<android.widget.ScrollView>(R.id.settings_root_scroll).scrollY
            press(a,out);assertTrue(a.uiView<View>(R.id.cz_category_playlist).isFocused)
            press(a,into);assertTrue(a.uiView<View>(R.id.btn_settings_guide).isFocused)
            assertEquals(scroll,a.uiView<android.widget.ScrollView>(R.id.settings_root_scroll).scrollY)
            press(a,out);press(a,KeyEvent.KEYCODE_DPAD_DOWN);press(a,into)
            assertTrue(a.uiView<View>(R.id.btn_settings_mode).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_DOWN);assertTrue(a.uiView<View>(R.id.btn_settings_language).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_CENTER)
            val rows=all(a.uiView(R.id.settings_pages)).filterIsInstance<ChoiceRow>()
            assertEquals(6,rows.size);assertEquals(1,rows.count{it.isSelected})
            assertEquals(listOf("English","Español","हिन्दी","ไทย","فارسی","العربية"),rows.map{it.title.text.toString()})
            press(a,KeyEvent.KEYCODE_BACK);assertTrue(a.uiView<View>(R.id.btn_settings_language).isFocused)
            press(a,KeyEvent.KEYCODE_BACK);assertTrue(a.uiView<View>(R.id.cz_category_app).isFocused)
            press(a,KeyEvent.KEYCODE_BACK);assertEquals(View.GONE,panel.visibility)
            assertEquals(1f,a.uiView<View>(R.id.cz_video_scene).scaleX,0f)
            assertEquals(1f,a.uiView<View>(R.id.cz_hud_scene).scaleX,0f)
            assertFalse(a.isFinishing)
        }
    }
    @Test fun `Menu repeats and matching releases cannot reopen or activate a setting`()=screen("en"){a->
        val panel=a.uiView<View>(R.id.layout_settings)
        val before=Prefs(a).isCrtSoundEnabled()
        repeat(30){
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU))
            a.dispatchKeyEvent(KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU,4))
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MENU))
            layout(a)
        }
        assertEquals(View.VISIBLE,panel.visibility);assertEquals(before,Prefs(a).isCrtSoundEnabled());assertFalse(a.isFinishing)
    }
    @Test fun `rapid rail changes enter only the current detail while category fading is enabled`()=screen("en"){a->
        android.provider.Settings.Global.putFloat(a.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        for((category,action) in listOf(R.id.cz_category_playlist to R.id.btn_settings_reimport,R.id.cz_category_app to R.id.btn_settings_mode,R.id.cz_category_playback to R.id.btn_settings_aspect)){
            a.uiView<View>(category).requestFocusFromTouch()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(17));layout(a)
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT))
            a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT))
            assertTrue("New detail must receive input before the 100ms fade ends",a.uiView<View>(action).isFocused)
            press(a,KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }
    @Test fun `system picker stop and resume retain the exact URI page caret and focus`() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(context).apply{setLanguage("ar");setInterfaceMode("TV");markIntroSeen()};PlaylistRepository(context).clearPlaylist()
        android.provider.Settings.Global.putFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try{
            val a=controller.get();layout(a);a.uiView<View>(R.id.btn_import).performClick();layout(a)
            all(a.uiView(R.id.settings_pages)).first{it.isUiAction&&it.uiLabel().text==a.getString(R.string.import_link)}.performClick();layout(a)
            val input=all(a.uiView(R.id.settings_pages)).filterIsInstance<android.widget.EditText>().single()
            input.setText("https://example.org/list.m3u8");input.requestFocusFromTouch();input.setSelection(10)
            assertEquals(10,input.selectionStart)
            controller.pause().stop()
            assertEquals(View.GONE,a.uiView<View>(R.id.layout_settings).visibility)
            assertEquals(1f,a.uiView<View>(R.id.cz_video_scene).scaleX,0f)
            controller.start().resume().visible();layout(a)
            val returned=all(a.uiView(R.id.settings_pages)).filterIsInstance<android.widget.EditText>().single()
            assertSame(input,returned);assertEquals("https://example.org/list.m3u8",returned.text.toString());assertEquals(10,returned.selectionStart)
            assertTrue(returned.isFocused)
            press(a,KeyEvent.KEYCODE_BACK)
            assertEquals(a.getString(R.string.btn_import_playlist),a.uiView<android.widget.TextView>(R.id.settings_title).text.toString())
        }finally{controller.pause().stop().destroy()}
    }
}
