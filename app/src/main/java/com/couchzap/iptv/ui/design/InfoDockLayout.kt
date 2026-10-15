package com.couchzap.iptv.ui.design

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ScrollView

/** Bounds explanatory content without reducing or scrolling away the primary controls. */
class InfoDockLayout(context:Context,attrs:AttributeSet?=null):LinearLayout(context,attrs) {
    var maximumHeight=Int.MAX_VALUE;set(value){if(field!=value){field=value;requestLayout()}}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val size=MeasureSpec.getSize(heightMeasureSpec)
        val maximum=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED)maximumHeight else minOf(size,maximumHeight)
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(maximum,if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.EXACTLY)MeasureSpec.EXACTLY else MeasureSpec.AT_MOST))
    }
}

class BoundedScrollView(context:Context,attrs:AttributeSet?=null):ScrollView(context,attrs) {
    var maximumHeight=Int.MAX_VALUE;set(value){if(field!=value){field=value;requestLayout()}}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.EXACTLY){super.onMeasure(widthMeasureSpec,heightMeasureSpec);return}
        val available=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED)maximumHeight else minOf(MeasureSpec.getSize(heightMeasureSpec),maximumHeight)
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST))
    }
}
