package com.couchzap.iptv

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Native pixel checks for physical icon direction across saved-language lifecycle paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36], qualifiers="en-rUS-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChevronLifecycle311ReviewProbeTest {
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
    private fun layout(a:MainActivity) {
        val c=a.resources.configuration
        val w=c.screenWidthDp;val h=c.screenHeightDp
        val root=a.uiView<View>(R.id.root_container)
        repeat(3){root.forceLayout();root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);a.layoutSettingsForTest(w,h)}
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun press(a:MainActivity,key:Int) {
        a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key));a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key));layout(a)
    }
    private fun imageDirection(v:ImageView,name:String):String {
        assertTrue("visible icon must be laid out",v.width>0&&v.height>0)
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
        v.draw(Canvas(b))
        val dir=File("build/reports/3.1.1-native-glyphs").apply{mkdirs()}
        File(dir,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)}
        val p=buildList<Pair<Int,Int>>{for(y in 0 until b.height)for(x in 0 until b.width)if((b.getPixel(x,y) ushr 24)>80)add(x to y)}
        assertTrue("native arrow must render actual pixels",p.isNotEmpty())
        val minX=p.minOf{it.first};val maxX=p.maxOf{it.first};val middleY=(p.minOf{it.second}+p.maxOf{it.second})/2f
        val center=p.filter{kotlin.math.abs(it.second-middleY)<=1f}
        val tipX=center.map{it.first}.average()
        return if(tipX>(minX+maxX)/2.0)"RIGHT" else "LEFT"
    }
    private fun record(a:MainActivity,lang:String,phone:Boolean,phase:String):List<String> {
        val mode=if(phone)"phone" else "tv";val bad=mutableListOf<String>()
        fun check(id:Int) {
            val row=a.uiView<SettingRow>(id)
            val image=views(row).filterIsInstance<ImageView>().single{it.visibility==View.VISIBLE}
            val label=a.resources.getResourceEntryName(id)
            val actual=imageDirection(image,"${RuntimeEnvironment.getApiLevel()}-$mode-$lang-$phase-$label")
            println("ARROW phase=$phase sdk=${RuntimeEnvironment.getApiLevel()} mode=$mode lang=$lang row=$label contextDir=${a.resources.configuration.layoutDirection} rootDir=${a.settingsWindowRoot().layoutDirection} rowDir=${row.layoutDirection} imageDir=${image.layoutDirection} drawableDir=${image.drawable.layoutDirection} auto=${image.drawable.isAutoMirrored} scaleX=${image.scaleX} pixelDirection=$actual")
            val expected=if(lang in listOf("fa","ar"))"LEFT" else "RIGHT"
            if(actual!=expected)bad.add("$phase $mode $lang $label expected=$expected actual=$actual")
        }
        check(R.id.btn_settings_aspect)
        if(!phone)check(R.id.btn_settings_exit)
        if(!phone){a.uiView<View>(R.id.cz_category_app).performClick();layout(a)}
        else {a.uiView<View>(R.id.btn_settings_language).requestRectangleOnScreen(android.graphics.Rect(0,0,100,60),true);layout(a)}
        check(R.id.btn_settings_language)
        return bad
    }
    private fun scenario(lang:String,phone:Boolean,warm:Boolean=false,deviceLang:String="en-rUS",rotate:Boolean=false) {
        RuntimeEnvironment.setQualifiers(if(phone)"$deviceLang-w412dp-h915dp-port-mdpi" else "$deviceLang-w960dp-h540dp-land-mdpi")
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply{setLanguage(if(warm)"en" else lang);setInterfaceMode(if(phone)"Phone" else "TV");markIntroSeen()}
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val ctl=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {
            val a=ctl.get();layout(a);press(a,KeyEvent.KEYCODE_MENU)
            if(warm){
                a.uiView<View>(R.id.btn_settings_language).performClick();layout(a)
                views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>().single{it.getTag(R.id.cz_action_key)=="language/$lang"}.performClick();layout(a)
                if(!phone)a.uiView<View>(R.id.cz_category_playback).performClick()
                layout(a)
            }
            val bad=record(a,lang,phone,if(warm)"live-switch" else "saved-cold-start").toMutableList()
            if(rotate){
                val changed=Configuration(a.resources.configuration).apply{orientation=Configuration.ORIENTATION_LANDSCAPE;screenWidthDp=if(phone)915 else 900;screenHeightDp=if(phone)412 else 500}
                @Suppress("DEPRECATION")
                a.resources.updateConfiguration(changed,a.resources.displayMetrics)
                a.onConfigurationChanged(changed);layout(a)
                assertEquals(changed.screenWidthDp,a.resources.configuration.screenWidthDp)
                bad.addAll(record(a,lang,phone,"configuration-change"))
            }
            assertTrue("Rendered Settings chevron mismatch: $bad",bad.isEmpty())
        } finally {ctl.pause().stop().destroy()}
    }
    @Test fun cold_fa_tv()=scenario("fa",false)
    @Test fun cold_ar_tv()=scenario("ar",false)
    @Test fun cold_fa_phone()=scenario("fa",true)
    @Test fun cold_ar_phone()=scenario("ar",true)
    @Test fun cold_en_tv()=scenario("en",false)
    @Test fun cold_en_phone()=scenario("en",true)
    @Test fun cold_es_phone()=scenario("es",true)
    @Test fun cold_hi_phone()=scenario("hi",true)
    @Test fun cold_th_phone()=scenario("th",true)
    @Test fun configuration_fa_phone()=scenario("fa",true,rotate=true)
    @Test fun configuration_ar_phone()=scenario("ar",true,rotate=true)
    @Test fun configuration_fa_tv()=scenario("fa",false,rotate=true)
    @Test fun configuration_ar_tv()=scenario("ar",false,rotate=true)
    @Test fun warm_fa_tv()=scenario("fa",false,true)
    @Test fun warm_ar_phone()=scenario("ar",true,true)

    private fun aboutHeader(lang:String,phone:Boolean) {
        RuntimeEnvironment.setQualifiers(if(phone)"en-rUS-w412dp-h915dp-port-mdpi" else "en-rUS-w960dp-h540dp-land-mdpi")
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply{setLanguage(lang);setInterfaceMode(if(phone)"Phone" else "TV");markIntroSeen()}
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val ctl=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {
            val a=ctl.get();layout(a);press(a,KeyEvent.KEYCODE_MENU)
            a.uiView<View>(R.id.btn_settings_about).performClick();layout(a)
            val back=a.uiView<ImageView>(R.id.settings_back)
            val actual=imageDirection(back,"${RuntimeEnvironment.getApiLevel()}-${if(phone)"phone" else "tv"}-$lang-about-header")
            val expected=if(phone&&lang in listOf("fa","ar"))"RIGHT" else "LEFT"
            println("ABOUT_HEADER sdk=${RuntimeEnvironment.getApiLevel()} phone=$phone lang=$lang pixels=$actual desired311=$expected auto=${back.drawable.isAutoMirrored}")
            assertEquals("3.1.1 About header physical direction",expected,actual)
        } finally {ctl.pause().stop().destroy()}
    }
    @Test fun about_fa_phone_header_requires_right()=aboutHeader("fa",true)
    @Test fun about_ar_phone_header_requires_right()=aboutHeader("ar",true)
    @Test fun about_fa_tv_header_keeps_left()=aboutHeader("fa",false)
    @Test fun about_ar_tv_header_keeps_left()=aboutHeader("ar",false)

    @Test fun device_arabic_saved_fa_tv()=scenario("fa",false,deviceLang="ar")
    @Test fun device_persian_saved_ar_phone()=scenario("ar",true,deviceLang="fa")
    @Test fun device_persian_saved_en_tv()=scenario("en",false,deviceLang="fa")
    @Test fun device_persian_saved_fa_phone()=scenario("fa",true,deviceLang="fa")

    private fun exitAndRelaunch(lang:String,phone:Boolean) {
        val qualifiers=if(phone)"en-rUS-w412dp-h915dp-port-mdpi" else "en-rUS-w960dp-h540dp-land-mdpi"
        RuntimeEnvironment.setQualifiers(qualifiers)
        val c=ApplicationProvider.getApplicationContext<Context>()
        c.getSharedPreferences("couchzap_prefs",0).edit().clear().commit()
        Prefs(c).apply{setLanguage("en");setInterfaceMode(if(phone)"Phone" else "TV");markIntroSeen()}
        PlaylistRepository(c).clearPlaylist()
        android.provider.Settings.Global.putFloat(c.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        val first=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        val bad=mutableListOf<String>()
        try {
            val a=first.get();layout(a);press(a,KeyEvent.KEYCODE_MENU)
            a.uiView<View>(R.id.btn_settings_language).performClick();layout(a)
            views(a.settingsWindowRoot()).filterIsInstance<ChoiceRow>().single{it.getTag(R.id.cz_action_key)=="language/$lang"}.performClick();layout(a)
            if(!phone){a.uiView<View>(R.id.cz_category_playback).performClick();layout(a)}
            bad.addAll(record(a,lang,phone,"before-exit"))
            a.uiView<View>(R.id.btn_settings_exit).performClick()
            assertTrue("production Exit action finished the task",a.isFinishing)
        } finally {first.pause().stop().destroy()}
        assertEquals(lang,Prefs(c).getLanguage())
        // Reset the fixture's device locale while retaining saved preferences.
        // This models a new activity startup, not an actual OS process death.
        RuntimeEnvironment.setQualifiers(qualifiers)
        val next=Robolectric.buildActivity(MainActivity::class.java).create().start().resume().visible()
        try {
            val a=next.get();layout(a);press(a,KeyEvent.KEYCODE_MENU)
            bad.addAll(record(a,lang,phone,"after-exit-and-new-activity"))
            assertTrue("FA/AR left-pointing row chevrons must survive exit/relaunch: $bad",bad.isEmpty())
        } finally {next.pause().stop().destroy()}
    }
    @Test fun actual_exit_and_relaunch_fa_tv()=exitAndRelaunch("fa",false)
    @Test fun actual_exit_and_relaunch_ar_phone()=exitAndRelaunch("ar",true)
    @Test fun actual_exit_and_relaunch_ar_tv()=exitAndRelaunch("ar",false)
    @Test fun actual_exit_and_relaunch_fa_phone()=exitAndRelaunch("fa",true)
}
