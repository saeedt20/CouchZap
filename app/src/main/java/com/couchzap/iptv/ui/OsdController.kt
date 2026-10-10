package com.couchzap.iptv.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.media3.common.Format
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.databinding.ViewOsdBinding
import com.couchzap.iptv.player.StreamInfoFormatter
import com.couchzap.iptv.util.Anim

class OsdController(
    private val binding: ViewOsdBinding,
    private val tvGhostNext: TextView,
    private val onChannelsClick: () -> Unit = {},
    private val onPrevClick: () -> Unit = {},
    private val onPlayPauseClick: () -> Unit = {},
    private val onNextClick: () -> Unit = {},
    private val onAspectClick: () -> Unit = {},
    private val onSettingsClick: () -> Unit = {}
) {

    var controlsActive: Boolean = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private var hideRunnable = Runnable { hideOsd() }
    private var hideGhostRunnable = Runnable { tvGhostNext.visibility = View.GONE }

    private var clockRunnable: Runnable? = null
    private var currentChannel: Channel? = null
    private var currentVideoFormat: Format? = null
    private var currentAudioFormat: Format? = null

    init {
        startClockTimer()
        setupTouchControls()
    }

    private fun setupTouchControls() {
        binding.btnOsdChannels.setOnClickListener {
            resetAutoHide()
            onChannelsClick()
        }
        binding.btnOsdPrev.setOnClickListener {
            resetAutoHide()
            onPrevClick()
        }
        binding.btnOsdPlayPause.setOnClickListener {
            resetAutoHide()
            onPlayPauseClick()
        }
        binding.btnOsdNext.setOnClickListener {
            resetAutoHide()
            onNextClick()
        }
        binding.btnOsdAspect.setOnClickListener {
            resetAutoHide()
            onAspectClick()
        }
        binding.btnOsdSettings.setOnClickListener {
            resetAutoHide()
            onSettingsClick()
        }
    }

    fun resetAutoHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 5000L)
    }

    fun showOsd(channel: Channel, autoHideMs: Long = 5000L) {
        currentChannel = channel
        controlsActive = autoHideMs <= 0
        binding.root.descendantFocusability = if (controlsActive)
            android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS else android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.tvOsdChannelNumber.text = String.format("%03d", channel.number)
        binding.tvOsdChannelName.text = channel.name

        updateStreamDetails()

        handler.removeCallbacks(hideRunnable)

        Anim.showOverlay(binding.osdContainer)
        // Explicit remote controls also expire; refresh this on remote interaction.
        handler.postDelayed(hideRunnable, if (autoHideMs > 0) autoHideMs else 5000L)
    }

    fun showPendingZapOsd(channel: Channel, nextChannelName: String?) {
        showOsd(channel, autoHideMs = 5000L)

        if (!nextChannelName.isNull_or_empty()) {
            tvGhostNext.text = "▲ NEXT: $nextChannelName"
            tvGhostNext.visibility = View.VISIBLE
            handler.removeCallbacks(hideGhostRunnable)
            handler.postDelayed(hideGhostRunnable, 1200L)
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    fun hideOsd() {
        val hadFocus = binding.root.hasFocus()
        controlsActive = false
        binding.root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.root.clearFocus()
        handler.removeCallbacks(hideRunnable)
        Anim.hideOverlay(binding.osdContainer)
        if (hadFocus) (binding.root.parent as? View)?.requestFocus()
    }

    fun isVisible(): Boolean = binding.osdContainer.visibility == View.VISIBLE

    fun updateTracks(videoFormat: Format?, audioFormat: Format?) {
        currentVideoFormat = videoFormat
        currentAudioFormat = audioFormat
        updateStreamDetails()
    }

    fun setBuffering(isBuffering: Boolean) {
        binding.osdBufferingLayout.visibility = if (isBuffering) View.VISIBLE else View.GONE
    }

    private fun updateStreamDetails() {
        val details = StreamInfoFormatter.buildStreamInfoString(currentVideoFormat, currentAudioFormat)
        binding.tvOsdStreamDetails.text = details
    }

    private fun startClockTimer() {
        clockRunnable = object : Runnable {
            override fun run() {
                updateStreamDetails()
                handler.postDelayed(this, 60000L) // 1 minute tick
            }
        }
        handler.post(clockRunnable!!)
    }

    fun pause() {
        handler.removeCallbacksAndMessages(null)
    }

    fun resume() {
        handler.removeCallbacksAndMessages(null)
        startClockTimer()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }
}
