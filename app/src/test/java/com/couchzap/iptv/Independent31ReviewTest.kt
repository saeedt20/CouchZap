package com.couchzap.iptv

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.player.StreamInfoFormatter
import com.couchzap.iptv.util.Prefs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.Calendar
import android.graphics.Rect
import android.widget.ScrollView

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28,36], qualifiers="w960dp-h540dp-land-mdpi")
class Independent31ReviewTest {
    private fun screen(lang:String, phone:Boolean=false, block:(MainActivity)->Unit) {
        RuntimeEnvironment.setQualifiers(if(phone) "w412dp-h915dp-port-mdpi" else "w960dp-h540dp-land-mdpi")
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply { setLanguage(lang);setInterfaceMode(if(phone)"Phone" else "TV");markIntroSeen() }
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try { layout(controller.get());block(controller.get()) }
        finally { controller.pause().stop().destroy() }
    }
    private fun layout(a:MainActivity) {
        val w=a.resources.configuration.screenWidthDp
        val h=a.resources.configuration.screenHeightDp
        val root=a.uiView<View>(R.id.root_container)
        repeat(3) {
            root.forceLayout()
            root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            root.layout(0,0,w,h);a.layoutSettingsForTest(w,h)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun press(a:MainActivity,key:Int) {
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));layout(a)
    }
    private fun openSettings(a:MainActivity) { press(a,KeyEvent.KEYCODE_MENU) }
    /** Native dispatch through the actual owned window, with production listeners intact. */
    private fun touch(a:MainActivity,target:View,finish:Int=MotionEvent.ACTION_UP,drag:Boolean=false) {
        target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true);layout(a)
        val root=a.settingsWindowRoot()
        val attach=ReflectionHelpers.getField<Any>(root,"mAttachInfo")
        ReflectionHelpers.setField(attach,"mInTouchMode",true)
        assertTrue("fixture genuinely in touch mode",root.isInTouchMode)
        val p=IntArray(2);val q=IntArray(2)
        target.getLocationOnScreen(p);root.getLocationOnScreen(q)
        val x=p[0]-q[0]+target.width/2f;val y=p[1]-q[1]+target.height/2f
        assertTrue("target in actual window",x in 0f..root.width.toFloat() && y in 0f..root.height.toFloat())
        val t=SystemClock.uptimeMillis()
        fun send(action:Int,dy:Float,delay:Long) {
            val e=MotionEvent.obtain(t,t+delay,action,x,y+dy,0)
            try { root.dispatchTouchEvent(e) }finally{e.recycle()}
        }
        send(MotionEvent.ACTION_DOWN,0f,0)
        if(drag)send(MotionEvent.ACTION_MOVE,-140f,80)
        send(finish,if(drag)-140f else 0f,160)
        shadowOf(Looper.getMainLooper()).idle();layout(a)
    }
    @Test fun `B visible preview keeps physical safe right inset after native layout`() {
        for (phone in listOf(false,true)) for (lang in listOf("fa","ar","en")) screen(lang,phone) { a->
            val v=a.uiView<TextView>(R.id.tv_ghost_next)
            v.text="Next: Charoen Cable 37 TV";v.visibility=View.VISIBLE
            layout(a)
            val parent=v.parent as View
            val expected=if(phone)20 else kotlin.math.ceil(maxOf(24f,parent.width*.05f)).toInt()
            val actual=parent.width-parent.paddingRight-v.right
            println("PREVIEW lang=$lang parent=${parent.width} bounds=${v.left}..${v.right} expectedInset=$expected actualInset=$actual")
            assertEquals("$lang visible preview physical right inset",expected,actual)
        }
    }
    @Test fun `D first tap through actual Settings window opens a real option`()=screen("en",phone=true) { a->
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MENU))
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MENU));layout(a)
        val root=a.settingsWindowRoot()
        val target=a.uiView<View>(R.id.btn_settings_tracks)
        assertTrue(target.isShown);assertFalse("target starts unfocused",target.isFocused)
        val attach=ReflectionHelpers.getField<Any>(root,"mAttachInfo")
        ReflectionHelpers.setField(attach,"mInTouchMode",true)
        assertTrue("fixture genuinely in touch mode",root.isInTouchMode)
        val p=IntArray(2);val q=IntArray(2)
        target.getLocationOnScreen(p);root.getLocationOnScreen(q)
        val x=p[0]-q[0]+target.width/2f;val y=p[1]-q[1]+target.height/2f
        assertTrue("target in actual window",x in 0f..root.width.toFloat() && y in 0f..root.height.toFloat())
        val t=SystemClock.uptimeMillis()
        val d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0)
        val u=MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,x,y,0)
        try { root.dispatchTouchEvent(d);root.dispatchTouchEvent(u) }finally{d.recycle();u.recycle()}
        shadowOf(Looper.getMainLooper()).idle();layout(a)
        assertEquals("real audio/subtitle option opened",a.getString(R.string.audio_subtitles),a.uiView<TextView>(R.id.settings_title).text.toString())
        assertEquals(View.VISIBLE,a.uiView<View>(R.id.settings_back).visibility)
    }
    @Test fun `C non FA AR locale preserves legacy device digit behavior`() {
        val old=Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar"))
            val date=Date(1700000000000L)
            val expected=SimpleDateFormat("HH:mm",Locale.getDefault()).format(date)
            val actual=StreamInfoFormatter.formatTime(date,"en")
            println("CLOCK device=ar app=en legacy=$expected actual=$actual")
            assertEquals("English app retains legacy device-locale behavior",expected,actual)
        } finally { Locale.setDefault(old) }
    }
    @Test fun `C local wall time remains correct across timezones`() {
        val oldZone=TimeZone.getDefault();val oldLocale=Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            for(zone in listOf("UTC","Asia/Tehran","America/New_York")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val c=Calendar.getInstance().apply { set(2026,9,17,23,24,0);set(Calendar.MILLISECOND,0) }
                assertEquals(zone,"۲۳:۲۴",StreamInfoFormatter.formatTime(c.time,"fa"))
                assertEquals(zone,"٢٣:٢٤",StreamInfoFormatter.formatTime(c.time,"ar"))
                assertEquals(zone,"23:24",StreamInfoFormatter.formatTime(c.time,"en"))
            }
        }finally{TimeZone.setDefault(oldZone);Locale.setDefault(oldLocale)}
    }
    @Test fun `D real sound toggle activates exactly once`()=screen("en",true) { a->
        openSettings(a)
        val row=a.uiView<View>(R.id.btn_settings_crt_row)
        assertFalse(row.isFocused)
        val preferences=a.getSharedPreferences("couchzap_prefs",0)
        val old=Prefs(a).isCrtSoundEnabled();var changes=0
        val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener{_,_->changes++}
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            touch(a,row)
            assertEquals(!old,Prefs(a).isCrtSoundEnabled())
            assertEquals("one completed tap makes one preference change",1,changes)
        }finally{preferences.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    @Test fun `D window cancel scroll and disabled row do not activate`() {
        for(kind in listOf("cancel","scroll","disabled"))screen("en",true) { a->
            openSettings(a)
            val row=a.uiView<View>(R.id.btn_settings_tracks)
            if(kind=="disabled")row.isEnabled=false
            touch(a,row,if(kind=="cancel")MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,kind=="scroll")
            assertEquals(kind,a.getString(R.string.settings),a.uiView<TextView>(R.id.settings_title).text.toString())
            assertEquals(kind,View.GONE,a.uiView<View>(R.id.settings_back).visibility)
        }
    }
    @Test fun `D actual language choice taps apply once and refresh the Info clock`()=screen("en",true) { a->
        openSettings(a)
        val language=a.uiView<View>(R.id.btn_settings_language)
        assertFalse(language.isFocused);touch(a,language)
        val choice=views(a.settingsWindowRoot()).filterIsInstance<com.couchzap.iptv.ui.design.ChoiceRow>()
            .single{it.title.text.toString()=="العربية"}
        assertFalse(choice.isFocused);touch(a,choice)
        assertEquals("ar",Prefs(a).getLanguage())
        assertEquals(a.getString(R.string.settings),a.uiView<TextView>(R.id.settings_title).text.toString())
        val time=a.uiView<TextView>(R.id.tv_osd_clock).text.toString()
        assertTrue(time,time.filter{it!=':'}.all{it in '٠'..'٩'})
        assertEquals(View.TEXT_DIRECTION_LTR,a.uiView<TextView>(R.id.tv_osd_clock).textDirection)
        press(a,KeyEvent.KEYCODE_BACK)
        assertEquals(View.GONE,a.uiView<View>(R.id.layout_settings).visibility)
    }
    @Test fun `E About text can be reached from the action with remote Up`() {
        for(lang in listOf("en","es","hi","th","fa","ar"))screen(lang) { a->
            openSettings(a)
            a.uiView<View>(R.id.btn_settings_about).performClick();layout(a)
            val heading=views(a.settingsWindowRoot()).filterIsInstance<TextView>()
                .single{it.text.toString()==a.getString(R.string.about_heading)}
            val scroll=heading.parent.parent as ScrollView
            val initial=scroll.scrollY
            repeat(30){press(a,KeyEvent.KEYCODE_DPAD_UP)}
            val r=Rect();val visible=heading.getGlobalVisibleRect(r)
            println("ABOUT lang=$lang initialScroll=$initial afterUp=${scroll.scrollY} headingVisible=$visible focus=${a.settingsWindowRoot().findFocus()?.javaClass?.simpleName}")
            assertTrue("$lang About heading must be reachable with remote Up",visible&&r.height()>0)
            assertEquals("$lang can scroll back to the beginning",0,scroll.scrollY)
            val back=a.uiView<android.widget.ImageView>(R.id.settings_back)
            assertEquals("$lang scoped About back icon",lang !in listOf("fa","ar"),back.drawable.isAutoMirrored)
            // Reading the text must not make the sole action unreachable.
            press(a,KeyEvent.KEYCODE_DPAD_DOWN)
            repeat(30){press(a,KeyEvent.KEYCODE_DPAD_DOWN)}
            val github=views(a.settingsWindowRoot()).filterIsInstance<com.couchzap.iptv.ui.SettingRow>()
                .single{it.labelView?.text?.toString()==a.getString(R.string.about_github)}
            assertTrue("$lang GitHub remains reachable by remote",github.isFocused)
            repeat(30){press(a,KeyEvent.KEYCODE_DPAD_UP)}
            assertEquals(0,scroll.scrollY)
            assertFalse("generated action starts unfocused for touch",github.isFocused)
            val browserIntent=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/saeedt20/CouchZap"))
            val handler=android.content.pm.ResolveInfo().apply {
                activityInfo=android.content.pm.ActivityInfo().apply { packageName="test.browser";name="Browser";exported=true }
            }
            shadowOf(a.packageManager).addResolveInfoForIntent(browserIntent,handler)
            touch(a,github)
            val launched=shadowOf(a).nextStartedActivity
            assertEquals(android.content.Intent.ACTION_VIEW,launched.action)
            assertEquals("https://github.com/saeedt20/CouchZap",launched.dataString)
            assertNull("one tap starts one browser intent",shadowOf(a).nextStartedActivity)
            press(a,KeyEvent.KEYCODE_BACK)
            a.uiView<View>(R.id.btn_settings_language).performClick();layout(a)
            assertTrue("About header override is cleared on the next page",back.drawable.isAutoMirrored)
        }
    }
}
