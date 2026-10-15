package com.couchzap.iptv.ui.settings

import android.view.*

/** Pointer ownership starts on the noneditable header, never a scrolling list or the video. */
class HeaderDismissGesture(private val shell:View,private val motion:SettingsMotionController,private val allowed:()->Boolean,private val closing:()->Unit):View.OnTouchListener {
    private var startX=0f;private var startY=0f;private var dragging=false;private var tracker:VelocityTracker?=null
    fun cancel(){if(dragging)motion.endDrag(false);dragging=false;tracker?.recycle();tracker=null}
    private fun track(event:MotionEvent){
        val dx=event.rawX-event.x;val dy=event.rawY-event.y
        event.offsetLocation(dx,dy);tracker?.addMovement(event);event.offsetLocation(-dx,-dy)
    }
    override fun onTouch(view:View,event:MotionEvent):Boolean {
        if(!allowed()){cancel();return false}
        val density=view.resources.displayMetrics.density
        val sign=if(view.layoutDirection==View.LAYOUT_DIRECTION_RTL)-1 else 1
        val travel=minOf(280*density,shell.width*.65f).coerceAtLeast(1f)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{startX=event.rawX;startY=event.rawY;dragging=false;tracker?.recycle();tracker=VelocityTracker.obtain();track(event);return true}
            MotionEvent.ACTION_MOVE->{track(event);val dx=(event.rawX-startX)*sign;val dy=kotlin.math.abs(event.rawY-startY)
                if(!dragging&&dx>ViewConfiguration.get(view.context).scaledTouchSlop&&dx>1.5f*dy){dragging=true;motion.beginDrag()}
                if(dragging)motion.drag(1f-dx/travel);return true}
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                track(event);tracker?.computeCurrentVelocity(1000)
                if(dragging){val v=(tracker?.xVelocity?:0f)*sign;val commit=event.actionMasked!=MotionEvent.ACTION_CANCEL&&(motion.progress<.65f||v/density>800);if(commit)closing();motion.endDrag(commit,-v/travel)}
                tracker?.recycle();tracker=null;dragging=false;return true
            }
        };return dragging
    }
}
