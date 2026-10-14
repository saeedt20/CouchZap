package com.couchzap.iptv.ui

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.couchzap.iptv.databinding.ViewImportBinding
import com.couchzap.iptv.util.Anim
import com.couchzap.iptv.util.Prefs

/** Runs once, never owns input or postpones focus. First user input completes it synchronously. */
class WelcomeIntro(private val binding: ViewImportBinding, private val prefs: Prefs) {
    private val views = listOf(binding.localizedLabel1, binding.tvImportTitle, binding.tvImportHint, binding.btnImport, binding.btnPublic)
    private val handler = Handler(Looper.getMainLooper())
    private var pulse: ValueAnimator? = null
    var running = false; private set
    private val end = Runnable { finish() }
    fun start() {
        if (prefs.hasSeenIntro()) return
        prefs.markIntroSeen()
        if (!Anim.enabled(binding.root)) { finish(); return }
        running = true
        views.forEachIndexed { index, view ->
            view.animate().cancel(); view.alpha = 0f; view.translationY = 10 * view.resources.displayMetrics.density
            view.animate().alpha(1f).translationY(0f).setStartDelay(index * 60L).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        pulse = ValueAnimator.ofFloat(0f, .5f, 0f).apply {
            startDelay = 120; duration = 440
            addUpdateListener { binding.introBolt.alpha = it.animatedValue as Float }
            start()
        }
        handler.postDelayed(end, 620)
    }
    fun finish() {
        running = false; handler.removeCallbacks(end); pulse?.cancel(); pulse = null
        views.forEach { it.animate().withEndAction(null).cancel(); it.animate().setStartDelay(0); it.alpha = 1f; it.translationY = 0f; it.translationX = 0f; it.setLayerType(View.LAYER_TYPE_NONE, null) }
        binding.introBolt.alpha = 0f
    }
}
