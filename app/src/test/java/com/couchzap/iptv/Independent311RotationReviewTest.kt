package com.couchzap.iptv

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28,36],qualifiers="en-rUS-w412dp-h915dp-port-mdpi")
class Independent311RotationReviewTest {
    private fun layout(a:MainActivity) {
        val root=a.uiView<View>(R.id.root_container)
        val w=a.resources.configuration.screenWidthDp;val h=a.resources.configuration.screenHeightDp
        repeat(3){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);a.layoutSettingsForTest(w,h)}
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun screen(lang:String="en",test:(MainActivity)->Unit) {
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply{setLanguage(lang);setInterfaceMode("Phone");markIntroSeen()}
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val ctl=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {layout(ctl.get());test(ctl.get())}finally{ctl.pause().stop().destroy()}
    }
    private fun press(a:MainActivity,key:Int){a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));layout(a)}
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    @Test fun rotation_and_settings_have_distinct_native_bounds()=screen { a->
        val gear=a.uiView<View>(R.id.btn_quick_settings);val rotate=a.uiView<View>(R.id.btn_rotate)
        val g=Rect(gear.left,gear.top,gear.right,gear.bottom);val r=Rect(rotate.left,rotate.top,rotate.right,rotate.bottom)
        println("QUICK_BOUNDS sdk=${RuntimeEnvironment.getApiLevel()} gear=$g rotate=$r")
        assertFalse("rotate must not cover Settings",Rect.intersects(g,r))
        assertTrue("rotate should be below the gear with spacing",rotate.top>=gear.bottom+8)
    }
    @Test fun opening_channels_hides_both_quick_actions()=screen {a->
        ReflectionHelpers.callInstanceMethod<Any>(a,"openChannelListOverlay");layout(a)
        assertEquals("gear hidden while channels own playback",View.GONE,a.uiView<View>(R.id.btn_quick_settings).visibility)
        assertEquals("rotation hidden while channels own playback",View.GONE,a.uiView<View>(R.id.btn_rotate).visibility)
    }
    @Test fun opening_settings_hides_both_quick_actions()=screen {a->
        press(a,KeyEvent.KEYCODE_MENU)
        assertEquals("gear hidden under owned Settings",View.GONE,a.uiView<View>(R.id.btn_quick_settings).visibility)
        assertEquals("rotation hidden under owned Settings",View.GONE,a.uiView<View>(R.id.btn_rotate).visibility)
    }
    @Test fun first_real_tap_hits_rotation_not_settings()=screen {a->
        a.uiView<View>(R.id.view_import).visibility=View.GONE
        val root=a.uiView<View>(R.id.root_container)
        ReflectionHelpers.getField<Any>(root.rootView,"mAttachInfo").let{ReflectionHelpers.setField(it,"mInTouchMode",true)}
        val b=a.uiView<View>(R.id.btn_rotate);layout(a)
        val now=SystemClock.uptimeMillis()
        for ((action,offset) in listOf(MotionEvent.ACTION_DOWN to 0L,MotionEvent.ACTION_UP to 100L)) {
            val e=MotionEvent.obtain(now,now+offset,action,b.left+b.width/2f,b.top+b.height/2f,0)
            try {root.dispatchTouchEvent(e)}finally{e.recycle()}
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("first completed tap rotates",ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.requestedOrientation)
        assertEquals(View.GONE,a.uiView<View>(R.id.layout_settings).visibility)
    }
    @Test fun first_real_tap_hits_settings_not_rotation()=screen{a->
        a.uiView<View>(R.id.view_import).visibility=View.GONE
        val root=a.uiView<View>(R.id.root_container)
        ReflectionHelpers.getField<Any>(root.rootView,"mAttachInfo").let{ReflectionHelpers.setField(it,"mInTouchMode",true)}
        val b=a.uiView<View>(R.id.btn_quick_settings);layout(a)
        val now=SystemClock.uptimeMillis()
        for((action,offset) in listOf(MotionEvent.ACTION_DOWN to 0L,MotionEvent.ACTION_UP to 100L)){
            val e=MotionEvent.obtain(now,now+offset,action,b.left+b.width/2f,b.top+b.height/2f,0)
            try{root.dispatchTouchEvent(e)}finally{e.recycle()}
        }
        layout(a)
        assertEquals(View.VISIBLE,a.uiView<View>(R.id.layout_settings).visibility)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,a.requestedOrientation)
    }
    @Test fun rotation_override_survives_saved_instance_restore()=screen {a->
        a.uiView<View>(R.id.btn_rotate).performClick()
        val state=Bundle()
        ReflectionHelpers.callInstanceMethod<Any>(a,"onSaveInstanceState",ReflectionHelpers.ClassParameter.from(Bundle::class.java,state))
        val next=Robolectric.buildActivity(MainActivity::class.java).create(state).start().resume().visible()
        try {assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,next.get().requestedOrientation)}finally{next.pause().stop().destroy()}
    }
    @Test fun quick_actions_remain_separate_in_all_six_languages() {
        for(lang in listOf("en","es","hi","th","fa","ar"))screen(lang){a->
            val gear=a.uiView<View>(R.id.btn_quick_settings);val rotate=a.uiView<View>(R.id.btn_rotate)
            assertEquals("$lang visible",View.VISIBLE,rotate.visibility)
            assertEquals("$lang same side",gear.left,rotate.left)
            assertTrue("$lang vertical gap",rotate.top>=gear.bottom+8)
        }
    }
    @Test fun completed_rotation_can_return_to_portrait()=screen{a->
        val rotate=a.uiView<View>(R.id.btn_rotate)
        rotate.performClick()
        // The framework updates this Activity's scoped Resources before the callback.
        // RuntimeEnvironment.setQualifiers alone only changes the application context.
        val land=Configuration(a.resources.configuration).apply{orientation=Configuration.ORIENTATION_LANDSCAPE;screenWidthDp=915;screenHeightDp=412}
        @Suppress("DEPRECATION")
        a.resources.updateConfiguration(land,a.resources.displayMetrics)
        a.onConfigurationChanged(land);layout(a)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE,a.resources.configuration.orientation)
        assertEquals(a.getString(R.string.rotate_to_portrait),rotate.contentDescription)
        rotate.performClick()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,a.requestedOrientation)
        val port=Configuration(a.resources.configuration).apply{orientation=Configuration.ORIENTATION_PORTRAIT;screenWidthDp=412;screenHeightDp=915}
        @Suppress("DEPRECATION")
        a.resources.updateConfiguration(port,a.resources.displayMetrics)
        a.onConfigurationChanged(port);layout(a)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,a.requestedOrientation)
        assertEquals(a.getString(R.string.rotate_to_landscape),rotate.contentDescription)
    }
    @Test fun settings_language_switch_preserves_override_and_hides_actions_until_close()=screen{a->
        a.uiView<View>(R.id.btn_rotate).performClick()
        press(a,KeyEvent.KEYCODE_MENU)
        a.uiView<View>(R.id.btn_settings_language).performClick();layout(a)
        views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>().single{it.getTag(R.id.cz_action_key)=="language/ar"}.performClick();layout(a)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.requestedOrientation)
        assertEquals(View.GONE,a.uiView<View>(R.id.btn_rotate).visibility)
        a.uiView<View>(R.id.btn_settings_close).performClick();layout(a)
        assertEquals(View.VISIBLE,a.uiView<View>(R.id.btn_rotate).visibility)
        ReflectionHelpers.callInstanceMethod<Any>(a,"openChannelListOverlay");layout(a)
        ReflectionHelpers.callInstanceMethod<Any>(a,"closeChannelListOverlay");layout(a)
        assertEquals(View.VISIBLE,a.uiView<View>(R.id.btn_rotate).visibility)
    }
}
