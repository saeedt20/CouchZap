package com.couchzap.iptv.ui.design

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.couchzap.iptv.util.Anim

/** At most one outgoing and one incoming detail; the category rail and scene never animate here. */
class DetailPageMotion(private val host:FrameLayout) {
    private var generation=0
    private val springs=mutableMapOf<View,RetargetableUiSpring>()
    private val focusModes=mutableMapOf<View,Int>()
    private fun restore(v:View){
        springs.remove(v)?.cancel();v.animate().withEndAction(null).cancel();v.alpha=1f;v.translationX=0f;v.translationY=0f
        if(v is ViewGroup)focusModes.remove(v)?.let{v.descendantFocusability=it}
        v.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }
    fun show(incoming:View,returning:Boolean){
        val token=++generation
        val outgoing=host.getChildAt(host.childCount-1)?.takeIf{it!==incoming}
        for(i in host.childCount-1 downTo 0){val child=host.getChildAt(i);if(child!==outgoing){restore(child);host.removeView(child)}}
        (incoming.parent as? ViewGroup)?.removeView(incoming);restore(incoming)
        host.addView(incoming,ViewGroup.LayoutParams(-1,-1))
        val sign=if(host.layoutDirection==View.LAYOUT_DIRECTION_RTL)-1 else 1
        val density=host.resources.displayMetrics.density
        if(!Anim.enabled(host)){outgoing?.let{restore(it);host.removeView(it)};return}
        outgoing?.let{old->
            springs.remove(old)?.cancel()
            if(old is ViewGroup){focusModes[old]=old.descendantFocusability;old.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS}
            old.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            old.animate().withEndAction(null).cancel()
            old.animate().alpha(0f).translationX(sign*8*density*(if(returning)1 else -1)).setDuration(100).withEndAction{
                if(token==generation&&old.parent===host){restore(old);host.removeView(old)}
            }.start()
        }
        val distance=sign*(if(returning)-12 else 18)*density
        val spring=RetargetableUiSpring(0f,850f,1f){p->incoming.alpha=p.coerceIn(0f,1f);incoming.translationX=distance*(1f-p)}
        springs[incoming]=spring
        spring.onEnd={canceled->if(!canceled&&token==generation&&incoming.parent===host){incoming.alpha=1f;incoming.translationX=0f}}
        spring.settle(0f);spring.target(1f,true)
    }
    fun clear(){generation++;springs.values.toList().forEach{it.cancel()};springs.clear();for(i in host.childCount-1 downTo 0)restore(host.getChildAt(i));host.removeAllViews();focusModes.clear()}
    fun settle(){generation++;val current=host.getChildAt(host.childCount-1);for(i in host.childCount-1 downTo 0){val child=host.getChildAt(i);restore(child);if(child!==current)host.removeView(child)}}
}
