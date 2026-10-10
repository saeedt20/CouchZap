package com.couchzap.iptv.util

import android.view.View
import android.view.animation.DecelerateInterpolator

object Anim {

    private val linearOutSlowIn = DecelerateInterpolator()

    fun fadeInShutter(shutterView: View) {
        shutterView.visibility = View.VISIBLE
        shutterView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        shutterView.animate()
            .alpha(1f)
            .setDuration(120)
            .setInterpolator(linearOutSlowIn)
            .withEndAction {
                shutterView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            .start()
    }

    fun fadeOutShutter(shutterView: View) {
        shutterView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        shutterView.animate()
            .alpha(0f)
            .setDuration(180)
            .setInterpolator(linearOutSlowIn)
            .withEndAction {
                shutterView.visibility = View.GONE
                shutterView.setLayerType(View.LAYER_TYPE_NONE, null)
            }
            .start()
    }

    // Remote focus and visibility must change together. Older TV GPUs can leave
    // animated overlay layers partially drawn or translated after interruption.
    fun showOverlay(view: View) {
        resetOverlay(view)
        view.visibility = View.VISIBLE
    }

    fun hideOverlay(view: View) {
        view.visibility = View.GONE
        resetOverlay(view)
    }

    private fun resetOverlay(view: View) {
        view.animate().withEndAction(null).cancel()
        view.clearAnimation()
        view.alpha = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.setLayerType(View.LAYER_TYPE_NONE, null)
    }
}
