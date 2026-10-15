package com.couchzap.iptv.ui.design

import android.graphics.*
import android.graphics.drawable.Drawable
import com.couchzap.iptv.R
import android.content.Context

/** Local cached strokes; no bitmap, saveLayer, blur or allocations during drawing. */
class FocusChromeDrawable(context: Context, private val channel: Boolean = false) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val haloWidths = floatArrayOf(6f,4f)
    private val fill = context.getColor(R.color.cz_surface_focus)
    private val selectedFill = context.getColor(R.color.cz_surface_selected)
    private val ring = context.getColor(R.color.cz_sage_bright)
    private val sage = context.getColor(R.color.cz_sage)
    var focusAlpha = 0f; set(value) { field=value; invalidateSelf() }
    var full = false
    var keyboard = true
    var rtl = false
    var onPressChanged: (Boolean) -> Unit = {}
    private var selected = false
    private var pressed = false
    override fun isStateful() = true
    override fun onStateChange(state: IntArray): Boolean {
        selected = android.R.attr.state_selected in state
        val nextPressed = android.R.attr.state_pressed in state && android.R.attr.state_enabled in state
        if(nextPressed != pressed) { pressed=nextPressed; onPressChanged(pressed) }
        invalidateSelf(); return true
    }
    override fun draw(canvas: Canvas) {
        val inset = density
        rect.set(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset)
        val radius = 10*density
        paint.style=Paint.Style.FILL; paint.alpha=255
        if (focusAlpha > 0 || pressed || (selected && !channel)) {
            paint.color=if(focusAlpha>0 || pressed) fill else selectedFill
            canvas.drawRoundRect(rect,radius,radius,paint)
        }
        if(selected) {
            paint.color=sage
            val x=if(rtl) rect.right-3*density else rect.left
            canvas.drawRoundRect(x,rect.top+12*density,x+3*density,rect.bottom-12*density,1.5f*density,1.5f*density,paint)
        }
        if(keyboard && focusAlpha>0) {
            paint.style=Paint.Style.STROKE
            if(full) {
                for(width in haloWidths) { paint.strokeWidth=width*density; paint.color=sage; paint.alpha=(focusAlpha*if(width==6f)13 else 26).toInt(); canvas.drawRoundRect(rect,radius,radius,paint) }
            }
            paint.strokeWidth=2*density; paint.color=ring; paint.alpha=(255*focusAlpha).toInt()
            canvas.drawRoundRect(rect,radius,radius,paint)
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: ColorFilter?) {}
    override fun getOutline(outline: Outline) { outline.setRoundRect(bounds,10*density) }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
