package com.couchzap.iptv.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.couchzap.iptv.R

/** Ordinary opaque corner masks above the existing SurfaceView; no clipping or surface mutation. */
class VideoCornerMaskView(context:Context,attrs:AttributeSet?=null):View(context,attrs) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=context.getColor(R.color.cz_canvas)}
    private val path=Path().apply{fillType=Path.FillType.EVEN_ODD}
    var radius=0f;set(value){if(field==value)return;field=value;rebuild();invalidate()}
    init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;isFocusable=false;isClickable=false}
    private fun rebuild(){path.reset();path.fillType=Path.FillType.EVEN_ODD;path.addRect(0f,0f,width.toFloat(),height.toFloat(),Path.Direction.CW);path.addRoundRect(0f,0f,width.toFloat(),height.toFloat(),radius,radius,Path.Direction.CW)}
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int)=rebuild()
    override fun onDraw(canvas:Canvas){if(radius>0)canvas.drawPath(path,paint)}
}

interface VideoDepthTarget {fun depth(scale:Float,visibleRadius:Float);fun normalize()}
class SceneVideoDepth(private val scene:View,private val mask:VideoCornerMaskView):VideoDepthTarget {
    override fun depth(scale:Float,visibleRadius:Float){scene.scaleX=scale;scene.scaleY=scale;mask.visibility=View.VISIBLE;mask.radius=visibleRadius/scale}
    override fun normalize(){scene.scaleX=1f;scene.scaleY=1f;mask.radius=0f;mask.visibility=View.GONE}
}
