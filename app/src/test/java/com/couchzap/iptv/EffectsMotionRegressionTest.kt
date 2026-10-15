package com.couchzap.iptv

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.ui.settings.*
import com.couchzap.iptv.ui.design.AdaptiveUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,36],qualifiers="w960dp-h540dp-land-mdpi")
class EffectsMotionRegressionTest {
    private val capable=EffectsCapabilities(35,true,false,4L*1024*1024*1024,8,false,true)
    @Test fun `Full cannot bypass hard guards and reduced motion always wins`() {
        for(c in listOf(capable.copy(api=24),capable.copy(api=25),capable.copy(api=28),capable.copy(lowRam=true),
            capable.copy(totalRamBytes=2L*1024*1024*1024),capable.copy(hardwareAccelerated=false),capable.copy(powerSave=true),
            capable.copy(windowBlurEnabled=false),capable.copy(surfaceFailure=true),capable.copy(sessionDowngraded=true))) {
            assertEquals(EffectsTier.SAFE,EffectsPolicy.resolve(EffectsMode.AUTO,c))
            assertEquals(EffectsTier.SAFE,EffectsPolicy.resolve(EffectsMode.FULL,c))
        }
        assertEquals(EffectsTier.FULL,EffectsPolicy.resolve(EffectsMode.AUTO,capable))
        assertEquals(EffectsTier.FULL,EffectsPolicy.resolve(EffectsMode.AUTO,capable.copy(api=31)))
        assertEquals(EffectsTier.SAFE,EffectsPolicy.resolve(EffectsMode.FULL,capable.copy(api=31,windowBlurEnabled=false)))
        assertEquals(EffectsTier.SAFE,EffectsPolicy.resolve(EffectsMode.AUTO,capable.copy(processorCount=2)))
        assertEquals(EffectsTier.FULL,EffectsPolicy.resolve(EffectsMode.FULL,capable.copy(processorCount=2)))
        for(mode in EffectsMode.entries)assertEquals(EffectsTier.OFF,EffectsPolicy.resolve(mode,capable.copy(systemAnimationsEnabled=false)))
        assertEquals(EffectsTier.SAFE,EffectsPolicy.resolve(EffectsMode.SAFE,capable))
        assertEquals(EffectsTier.OFF,EffectsPolicy.resolve(EffectsMode.OFF,capable))
    }
    private class Video:VideoDepthTarget {
        var calls=0;var scale=1f;var radius=0f;var fail=false
        override fun depth(scale:Float,visibleRadius:Float){calls++;if(fail)throw IllegalStateException("fixture");this.scale=scale;radius=visibleRadius}
        override fun normalize(){calls++;scale=1f;radius=0f}
    }
    private fun scene(block:(SettingsMotionController,Video,View,View,()->Int,()->Int)->Unit){
        val c=ApplicationProvider.getApplicationContext<Context>()
        val shell=View(c);val scrim=View(c);val hud=View(c);val video=Video();var blur=0;var closed=0
        val motion=SettingsMotionController(shell,scrim,hud,video,{blur=it},{closed++})
        motion.configure(AdaptiveUi(true,960,540,1f))
        try{block(motion,video,shell,scrim,{blur},{closed})}finally{motion.destroy()}
    }
    @Test fun `Safe and Off never write video properties including lifecycle cleanup`()=scene{motion,video,_,scrim,blur,_->
        for(tier in listOf(EffectsTier.SAFE,EffectsTier.OFF)){
            motion.open(tier);motion.settleDesired();assertEquals(0,video.calls);assertEquals(0,blur());assertEquals(.6f,scrim.alpha,0f)
            motion.close();motion.settleDesired();motion.closeImmediately();assertEquals(0,video.calls)
        }
    }
    @Test fun `reversing at thirty percent cannot dismiss a reopened host or leave stale depth`()=scene{motion,video,shell,scrim,blur,closed->
        motion.open(EffectsTier.FULL);motion.settleForTest(.3f)
        assertEquals(.982f,video.scale,.0001f)
        motion.close();motion.open(EffectsTier.FULL);motion.settleDesired()
        assertEquals(SettingsMotionController.State.OPEN,motion.state)
        assertTrue(motion.ownsInput);assertTrue(motion.desiredOpen);assertEquals(0,closed())
        assertEquals(.94f,video.scale,0f);assertEquals(18f,video.radius,0f);assertEquals(.5f,scrim.alpha,0f)
        assertEquals(8,blur());assertEquals(1f,shell.alpha,0f);assertEquals(0f,shell.translationX,0f)
        motion.close();motion.settleDesired()
        assertEquals(1,closed());assertEquals(1f,video.scale,0f);assertEquals(0f,video.radius,0f);assertEquals(0,blur());assertFalse(motion.ownsInput)
    }
    @Test fun `a Full downgrade normalizes once and never upgrades during the same opening`()=scene{motion,video,shell,_,blur,_->
        motion.open(EffectsTier.FULL);motion.settleForTest(.5f);val position=shell.translationX
        motion.downgrade();val normalizedCalls=video.calls
        assertEquals(1f,video.scale,0f);assertEquals(0,blur());assertEquals(position,shell.translationX,0f)
        motion.open(EffectsTier.FULL);motion.settleDesired();assertEquals(EffectsTier.SAFE,motion.tier)
        assertEquals(normalizedCalls,video.calls)
        motion.close();motion.settleDesired();assertEquals(normalizedCalls,video.calls)
    }
    @Test fun `header drag cancels or commits by progress with RTL and releases its tracker`()=scene{motion,video,shell,_,_,closed->
        for(rtl in listOf(false,true)){
            shell.layout(0,0,360,800);shell.layoutDirection=if(rtl)View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            motion.configure(AdaptiveUi(false,360,800,1f));motion.open(EffectsTier.SAFE);motion.settleDesired()
            var commits=0
            val gesture=HeaderDismissGesture(shell,motion,{true},{commits++})
            fun event(action:Int,x:Float,time:Long){val e=android.view.MotionEvent.obtain(0,time,action,x,20f,0);try{gesture.onTouch(shell,e)}finally{e.recycle()}}
            val sign=if(rtl)-1 else 1
            event(android.view.MotionEvent.ACTION_DOWN,180f,0);event(android.view.MotionEvent.ACTION_MOVE,180f+sign*30,1000);event(android.view.MotionEvent.ACTION_UP,180f+sign*30,2000)
            motion.settleDesired();assertTrue(motion.desiredOpen);assertEquals(0,commits)
            event(android.view.MotionEvent.ACTION_DOWN,180f,3000);event(android.view.MotionEvent.ACTION_MOVE,180f+sign*100,4000);event(android.view.MotionEvent.ACTION_CANCEL,180f+sign*100,5000)
            motion.settleDesired();assertTrue(motion.desiredOpen);assertEquals(0,commits)
            event(android.view.MotionEvent.ACTION_DOWN,180f,6000);event(android.view.MotionEvent.ACTION_MOVE,180f+sign*120,7000);event(android.view.MotionEvent.ACTION_UP,180f+sign*120,8000)
            motion.settleDesired();assertFalse(motion.desiredOpen);assertEquals(1,commits);gesture.cancel()
            assertEquals(0,video.calls)
        }
        assertEquals(2,closed())
    }
    @Test fun `a video transform exception settles to Safe without further surface writes`()=scene{motion,video,_,scrim,blur,_->
        var failures=0;motion.onSurfaceFailure={failures++};video.fail=true
        motion.open(EffectsTier.FULL);motion.settleForTest(.5f)
        assertEquals(EffectsTier.SAFE,motion.tier);assertEquals(1,failures);assertEquals(1f,video.scale,0f);assertEquals(0,blur())
        val calls=video.calls
        motion.settleDesired();assertEquals(.6f,scrim.alpha,0f);assertEquals(calls,video.calls)
        motion.closeImmediately();assertEquals(calls,video.calls)
    }
}
