package com.couchzap.iptv.ui.design

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class RetargetableUiSpring(initial: Float, stiffness: Float, damping: Float, minimumChange: Float = .001f, apply: (Float) -> Unit) {
    private val holder = FloatValueHolder(initial)
    private val animation = SpringAnimation(holder).apply {
        spring = SpringForce(initial).apply { this.stiffness = stiffness; dampingRatio = damping }
        setMinimumVisibleChange(minimumChange)
        addUpdateListener { _, value, _ -> apply(value) }
    }
    private val render = apply
    var onEnd: ((Boolean) -> Unit)? = null
    init { animation.addEndListener { _, canceled, _, _ -> onEnd?.invoke(canceled) } }
    val value get() = holder.value
    fun target(value: Float, enabled: Boolean, stiffness: Float = animation.spring.stiffness, damping: Float = animation.spring.dampingRatio, velocity: Float? = null) {
        animation.spring.stiffness = stiffness; animation.spring.dampingRatio = damping
        if (enabled) { if (velocity != null) animation.setStartVelocity(velocity); animation.animateToFinalPosition(value) }
        else settle(value)
    }
    fun settle(value: Float) { animation.cancel(); holder.value = value; animation.setStartValue(value); render(value) }
    fun cancel() = animation.cancel()
}
