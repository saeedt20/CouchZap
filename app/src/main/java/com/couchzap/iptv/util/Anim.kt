package com.couchzap.iptv.util

import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.couchzap.iptv.R

/** Focus/visibility are synchronous; interruption always settles to the latest requested state. */
object Anim {
    private data class Target(val visible: Boolean, val alpha: Float)
    fun enabled(view: View): Boolean = runCatching {
        Settings.Global.getFloat(view.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f &&
            (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())
    }.getOrDefault(false)
    private fun cancel(view: View): Int {
        val token = ((view.getTag(R.id.motion_token) as? Int) ?: 0) + 1
        view.setTag(R.id.motion_token, token)
        view.animate().withEndAction(null).cancel(); view.animate().setStartDelay(0); view.clearAnimation()
        view.setLayerType(View.LAYER_TYPE_NONE, null)
        return token
    }
    fun finish(view: View) {
        val target = view.getTag(R.id.motion_target) as? Target ?: return
        cancel(view); view.alpha = target.alpha; view.translationX = 0f; view.translationY = 0f
        view.visibility = if (target.visible) View.VISIBLE else View.GONE
    }
    fun finishAll(view: View) {
        finish(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) finishAll(view.getChildAt(i))
    }
    private fun shutter(view: View, show: Boolean) {
        val token = cancel(view)
        val target = Target(show, if (show) 1f else 0f); view.setTag(R.id.motion_target, target)
        view.translationX = 0f; view.translationY = 0f
        if (!enabled(view)) { finish(view); return }
        if (show) { if (view.visibility != View.VISIBLE) view.alpha = 0f; view.visibility = View.VISIBLE }
        view.animate().alpha(target.alpha).setDuration(if (show) 100 else 150).setInterpolator(DecelerateInterpolator()).withEndAction {
            if (view.getTag(R.id.motion_token) == token) finish(view)
        }.start()
    }
    fun fadeInShutter(view: View) = shutter(view, true)
    fun fadeOutShutter(view: View) = shutter(view, false)
    fun showOverlay(view: View, fresh: Boolean = false) {
        val alreadyVisible = view.visibility == View.VISIBLE
        val token = cancel(view); view.setTag(R.id.motion_target, Target(true, 1f))
        view.visibility = View.VISIBLE; view.alpha = 1f; view.translationX = 0f; view.translationY = 0f
        // Keep the proven instantaneous path on API 24/25 GPUs.
        if ((!fresh && alreadyVisible) || Build.VERSION.SDK_INT < 26 || !enabled(view)) return
        view.alpha = 0f
        view.translationX = (if (view.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1 else -1) * 10 * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationX(0f).setDuration(150).setInterpolator(DecelerateInterpolator()).withEndAction {
            if (view.getTag(R.id.motion_token) == token) finish(view)
        }.start()
    }
    fun hideOverlay(view: View) {
        cancel(view); view.setTag(R.id.motion_target, Target(false, 1f))
        view.visibility = View.GONE; view.alpha = 1f; view.translationX = 0f; view.translationY = 0f
    }
    fun favourite(view: View) {
        if (!enabled(view)) return
        val token = cancel(view); view.setTag(R.id.motion_target, Target(true, 1f))
        view.alpha = .65f
        view.animate().alpha(1f).setDuration(140).withEndAction { if (view.getTag(R.id.motion_token) == token) finish(view) }.start()
    }
}
