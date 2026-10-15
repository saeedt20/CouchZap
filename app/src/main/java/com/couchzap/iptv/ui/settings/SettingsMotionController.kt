package com.couchzap.iptv.ui.settings

import android.os.Handler
import android.os.Looper
import android.view.View
import com.couchzap.iptv.ui.design.*

/** One retained progress owns every depth property. Safe never calls the video adapter. */
class SettingsMotionController(
    private val shell:View,private val scrim:View,private val hud:View,private val video:VideoDepthTarget,
    private val blur:(Int)->Unit,private val onClosed:()->Unit
) {
    enum class State { CLOSED, ENTERING, OPEN, DRAGGING, EXITING, DISPOSED }
    var state=State.CLOSED;private set
    var desiredOpen=false;private set
    var tier=EffectsTier.SAFE;private set
    private var ui=AdaptiveUi.from(shell)
    private var touchedVideo=false
    private var generation=0
    var onSettled: () -> Unit = {}
    var onSurfaceFailure:()->Unit={}
    private val handler=Handler(Looper.getMainLooper())
    private var watchdog:Runnable?=null
    private val spring=RetargetableUiSpring(0f,650f,1f){render(it)}
    val progress get()=spring.value
    val ownsInput get()=state !in listOf(State.CLOSED,State.DISPOSED)
    init{spring.onEnd={canceled->if(!canceled&&state!=State.DISPOSED&&state!=State.DRAGGING)finishTarget()}}
    fun configure(layout:AdaptiveUi){ui=layout;if(ownsInput)settleDesired()}
    fun open(effective:EffectsTier){
        if(state==State.DISPOSED)return
        if(!ownsInput)tier=effective
        if(desiredOpen&&state!=State.DRAGGING)return
        desiredOpen=true;state=State.ENTERING;animate()
    }
    fun close(){if(!ownsInput||state==State.DISPOSED)return;desiredOpen=false;state=State.EXITING;animate()}
    private fun animate(velocity:Float?=null){
        val token=++generation;watchdog?.let(handler::removeCallbacks)
        if(tier==EffectsTier.OFF){spring.settle(if(desiredOpen)1f else 0f);finishTarget();return}
        val full=tier==EffectsTier.FULL
        spring.target(if(desiredOpen)1f else 0f,true,if(full)if(desiredOpen)420f else 520f else if(desiredOpen)650f else 800f,
            if(full)if(desiredOpen).90f else .95f else 1f,velocity)
        watchdog=Runnable{if(token==generation&&state!=State.DISPOSED)settleDesired()}.also{handler.postDelayed(it,if(full)700 else 550)}
    }
    private fun render(raw:Float){
        if(state==State.DISPOSED)return
        val p=raw.coerceIn(0f,1f);val s=raw.coerceIn(-.02f,1.015f)
        if(tier==EffectsTier.FULL){
            touchedVideo=true
            try{video.depth(1f-.06f*p,ui.dp(if(ui.tv)18 else 16)*p)}catch(_:RuntimeException){
                runCatching{video.normalize()};touchedVideo=false;tier=EffectsTier.SAFE;blur(0);onSurfaceFailure()
            }
        }
        hud.scaleX=if(tier==EffectsTier.FULL)1f-.06f*p else if(tier==EffectsTier.SAFE)1f-.03f*p else 1f
        hud.scaleY=hud.scaleX
        val rtl=shell.layoutDirection==View.LAYOUT_DIRECTION_RTL
        val sign=if(ui.tv)if(rtl)1 else -1 else if(rtl)-1 else 1
        shell.translationX=sign*ui.dp(if(ui.tv)32 else 24)*(1f-s)
        shell.translationY=ui.dp(6)*(1f-s)
        shell.scaleX=.985f+.015f*s;shell.scaleY=shell.scaleX
        shell.alpha=smooth(p/.32f)
        scrim.alpha=(if(tier==EffectsTier.FULL).50f else .60f)*smooth(p)
        blur(if(tier==EffectsTier.FULL)(minOf(24,ui.dp(8))*smooth(p)).toInt() else 0)
    }
    private fun smooth(value:Float):Float{val x=value.coerceIn(0f,1f);return x*x*(3f-2f*x)}
    private fun finishTarget(){
        watchdog?.let(handler::removeCallbacks);watchdog=null
        val target=if(desiredOpen)1f else 0f
        if(kotlin.math.abs(progress-target)>.002f)return
        spring.settle(target)
        if(desiredOpen)state=State.OPEN
        else {state=State.CLOSED;normalize();onClosed()}
        onSettled()
    }
    fun settleDesired(){if(state==State.DISPOSED)return;generation++;watchdog?.let(handler::removeCallbacks);watchdog=null;spring.settle(if(desiredOpen)1f else 0f);finishTarget()}
    fun downgrade(surfaceFailure:Boolean=false){
        if(tier!=EffectsTier.FULL)return
        blur(0)
        // Normalize once, then the Safe renderer never touches the video again.
        if(touchedVideo){video.normalize();touchedVideo=false}
        tier=EffectsTier.SAFE;render(progress)
    }
    fun reduceTier(value:EffectsTier){if(value==EffectsTier.FULL)return;if(tier==EffectsTier.FULL)downgrade();tier=value;render(progress);if(value==EffectsTier.OFF)settleDesired()}
    fun beginDrag(){if(!ownsInput||tier==EffectsTier.OFF)return;generation++;watchdog?.let(handler::removeCallbacks);spring.cancel();state=State.DRAGGING}
    fun drag(value:Float){if(state==State.DRAGGING)spring.settle(value.coerceIn(0f,1f))}
    fun endDrag(commit:Boolean,velocity:Float=0f){if(state!=State.DRAGGING)return;desiredOpen=!commit;state=if(commit)State.EXITING else State.ENTERING;animate(velocity)}
    fun settleForTest(value:Float){spring.settle(value.coerceIn(0f,1f))}
    fun closeImmediately(){if(state==State.DISPOSED)return;generation++;watchdog?.let(handler::removeCallbacks);watchdog=null;spring.cancel();desiredOpen=false;state=State.CLOSED;normalize()}
    private fun normalize(){
        if(touchedVideo){video.normalize();touchedVideo=false}
        hud.scaleX=1f;hud.scaleY=1f;scrim.alpha=0f;blur(0)
        shell.scaleX=1f;shell.scaleY=1f;shell.translationX=0f;shell.translationY=0f;shell.alpha=1f
    }
    fun destroy(){closeImmediately();state=State.DISPOSED;handler.removeCallbacksAndMessages(null);spring.onEnd=null}
}
