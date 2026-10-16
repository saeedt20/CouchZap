package com.couchzap.iptv.ui.design

import android.animation.ValueAnimator
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import com.couchzap.iptv.R
import com.couchzap.iptv.util.Anim

/** A component has one scale owner; semantic callbacks remain composed with it. */
class FocusMotionBinder private constructor(private val view: View, private val role: Role) : View.OnAttachStateChangeListener {
    enum class Role(val full: Float, val safe: Float, val stiffness: Float, val damping: Float) {
        CHANNEL(1.025f,1.015f,950f,.88f), ROW(1.012f,1.008f,1100f,.90f),
        RAIL(1.015f,1.008f,1100f,.92f), WELCOME(1.035f,1.020f,850f,.86f), ICON(1.08f,1.04f,1100f,.88f)
    }
    private val chrome = FocusChromeDrawable(view.context,role==Role.CHANNEL)
    private var full = false
    private var pressed = false
    private val initialMotion = Anim.enabled(view)
    private val initialKeyboard=AdaptiveUi.isTv(view.context)
    private var ring: ValueAnimator? = null
    private val spring = RetargetableUiSpring(1f,role.stiffness,role.damping,.0005f) {
        view.scaleX=it; view.scaleY=it
        val maximum=if(full) role.full else role.safe
        view.translationZ=((it-1)/(maximum-1)).coerceIn(0f,1f)*CouchZapTokens.dp(view,if(full)6f else 2f)
    }
    init {
        view.background=chrome
        if(Build.VERSION.SDK_INT>=26) view.defaultFocusHighlightEnabled=false
        view.stateListAnimator=null
        val semantic = view.onFocusChangeListener
        view.onFocusChangeListener=View.OnFocusChangeListener { v,focused -> semantic?.onFocusChange(v,focused); refresh() }
        chrome.onPressChanged={ pressed=it; refresh() }
        view.addOnAttachStateChangeListener(this)
    }
    fun refresh() {
        val keyboard=view.rootView.getTag(R.id.cz_input_keyboard) as? Boolean?:initialKeyboard
        var ancestor:View?=view
        var tier:String?=null
        var allowed:Boolean?=null
        while(ancestor!=null){
            if(tier==null)tier=ancestor.getTag(R.id.cz_motion_owner) as? String
            if(allowed==null)allowed=ancestor.getTag(R.id.cz_animations_enabled) as? Boolean
            ancestor=ancestor.parent as? View
        }
        full=Build.VERSION.SDK_INT>=31 && tier=="full"
        chrome.full=full; chrome.keyboard=keyboard; chrome.rtl=view.layoutDirection==View.LAYOUT_DIRECTION_RTL
        val focused=view.isFocused && keyboard && view.isEnabled
        val animated=allowed?:initialMotion
        spring.target(if(animated && focused && !pressed) if(full)role.full else role.safe else 1f,animated,
            if(pressed)1500f else if(focused)role.stiffness else 1150f,if(pressed)1f else if(focused)role.damping else .95f)
        ring?.cancel()
        val target=if(focused)1f else 0f
        if(!animated) chrome.focusAlpha=target
        else ring=ValueAnimator.ofFloat(chrome.focusAlpha,target).apply {
            duration=if(focused)100 else 90; interpolator=PathInterpolator(.2f,0f,0f,1f)
            addUpdateListener { chrome.focusAlpha=it.animatedValue as Float }; start()
        }
    }
    fun reset() {
        ring?.cancel(); ring=null; spring.settle(1f); pressed=false; chrome.focusAlpha=0f
        view.translationX=0f; view.translationY=0f; view.translationZ=0f; view.alpha=1f
    }
    override fun onViewDetachedFromWindow(v: View) = reset()
    override fun onViewAttachedToWindow(v: View) = refresh()
    fun setSelectedMarkerEnabled(enabled: Boolean) { chrome.drawSelectedMarker = enabled }
    companion object {
        fun bind(view: View,role: Role=Role.ROW,selectedMarker: Boolean=true): FocusMotionBinder =
            ((view.getTag(R.id.cz_focus_binder) as? FocusMotionBinder)
                ?: FocusMotionBinder(view,role).also { view.setTag(R.id.cz_focus_binder,it) })
                .also { it.setSelectedMarkerEnabled(selectedMarker) }
        fun configure(root: View, tier: String? = null) {
            root.setTag(R.id.cz_animations_enabled, Anim.enabled(root))
            if(tier!=null)root.setTag(R.id.cz_motion_owner,tier)
            fun update(v: View) { (v.getTag(R.id.cz_focus_binder) as? FocusMotionBinder)?.refresh(); if(v is ViewGroup)for(i in 0 until v.childCount)update(v.getChildAt(i)) }
            update(root)
        }
        fun reset(view: View) { (view.getTag(R.id.cz_focus_binder) as? FocusMotionBinder)?.reset() }
        fun keyboard(root: View): Boolean = root.getTag(R.id.cz_input_keyboard) as? Boolean ?: AdaptiveUi.isTv(root.context)
        fun input(root: View,keyboard: Boolean) {
            val owner=root.rootView
            if(owner.getTag(R.id.cz_input_keyboard)==keyboard)return
            owner.setTag(R.id.cz_input_keyboard,keyboard)
            root.setTag(R.id.cz_input_keyboard,keyboard)
            fun update(v: View) { (v.getTag(R.id.cz_focus_binder) as? FocusMotionBinder)?.refresh(); if(v is ViewGroup) for(i in 0 until v.childCount)update(v.getChildAt(i)) }
            update(root)
        }
    }
}
