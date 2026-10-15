package com.couchzap.iptv.ui.design

import android.view.View
import android.view.animation.PathInterpolator
import com.couchzap.iptv.util.Anim

/** One desired state survives reversal. Visibility follows completion, input follows desire. */
class OverlayMotion(private val view: View, private val bottom: Boolean = false, private val scrim: View? = null) {
    var desiredOpen=false; private set
    private var generation=0
    fun show()=target(true)
    fun hide()=target(false)
    private fun target(open: Boolean) {
        val fresh=view.visibility!=View.VISIBLE
        val token=++generation; desiredOpen=open
        view.animate().withEndAction(null).cancel()
        scrim?.animate()?.withEndAction(null)?.cancel()
        if(!Anim.enabled(view)) { settle(); return }
        if(open && fresh) { view.alpha=0f; if(bottom)view.translationY=CouchZapTokens.dp(view,12f) else view.translationX=entryDistance() }
        if(open)view.visibility=View.VISIBLE
        scrim?.let { if(open) { if(it.visibility!=View.VISIBLE)it.alpha=0f;it.visibility=View.VISIBLE };it.animate().alpha(if(open).48f else 0f).setDuration(if(open)180 else 140).start() }
        view.animate().alpha(if(open)1f else 0f)
            .translationX(if(open || bottom)0f else entryDistance())
            .translationY(if(open || !bottom)0f else CouchZapTokens.dp(view,8f))
            .setDuration(if(open)180 else if(bottom)130 else 140)
            .setInterpolator(if(open)PathInterpolator(.2f,0f,0f,1f) else PathInterpolator(.4f,0f,1f,1f))
            .withEndAction { if(token==generation)settle() }.start()
    }
    private fun entryDistance()=CouchZapTokens.dp(view,24f)*if(view.layoutDirection==View.LAYOUT_DIRECTION_RTL)1 else -1
    fun settle() {
        generation++; view.animate().withEndAction(null).cancel(); view.alpha=1f;view.translationX=0f;view.translationY=0f;view.visibility=if(desiredOpen)View.VISIBLE else View.GONE
        scrim?.let { it.animate().cancel();it.alpha=if(desiredOpen).48f else 0f;it.visibility=if(desiredOpen)View.VISIBLE else View.GONE }
    }
}
