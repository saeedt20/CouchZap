package com.couchzap.iptv.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.media3.common.Format
import com.couchzap.iptv.R
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.data.Programme
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
    private val onSettingsClick: () -> Unit = {},
    private val onFavouriteClick: () -> Unit = {},
    private val onPreviousWatchedClick: () -> Unit = {},
    private val isFavourite: (Channel) -> Boolean = { false },
    private val programmes: (Channel) -> Pair<Programme?, Programme?> = { null to null },
    private val artwork: ChannelArtwork? = null
) {
    var controlsActive = false
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOsd() }
    private val hideGhostRunnable = Runnable { tvGhostNext.visibility = View.GONE }
    private var currentChannel: Channel? = null
    private var video: Format? = null
    private var audio: Format? = null
    private var status = binding.root.context.getString(R.string.connecting)
    private var expanded = false
    private var lastFocused = R.id.btn_osd_channels
    private val clock = object : Runnable {
        override fun run() { updateStreamDetails(); handler.postDelayed(this, 60000) }
    }
    init {
        listOf(binding.btnOsdChannels to onChannelsClick, binding.btnOsdPrev to onPrevClick,
            binding.btnOsdPlayPause to onPlayPauseClick, binding.btnOsdNext to onNextClick,
            binding.btnOsdAspect to onAspectClick, binding.btnOsdSettings to onSettingsClick,
            binding.btnOsdFavourite to onFavouriteClick, binding.btnOsdLastChannel to onPreviousWatchedClick).forEach { (button, action) ->
            button.setOnClickListener { resetAutoHide(); action() }
            button.setOnFocusChangeListener { _, focused -> if (focused) {
                lastFocused = button.id
                binding.tvOsdActionLabel.text = button.contentDescription ?: binding.root.context.getString(R.string.previous_watched)
                resetAutoHide()
            } }
        }
        handler.post(clock)
    }
    fun resetAutoHide() { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, 5000) }
    fun focusControls() { (binding.root.findViewById<View>(lastFocused)?.takeIf { it.visibility == View.VISIBLE } ?: binding.btnOsdChannels).requestFocusFromTouch() }
    fun showOsd(channel: Channel, autoHideMs: Long = 5000L) {
        val changed = currentChannel?.stableId != channel.stableId
        currentChannel = channel; controlsActive = autoHideMs <= 0
        if (changed || !controlsActive) { expanded = false; updateExpanded() }
        binding.root.descendantFocusability = if (controlsActive) android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS else android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.tvOsdChannelNumber.text = String.format(java.util.Locale.US, "%03d", channel.number)
        binding.tvOsdChannelName.text = channel.name
        binding.tvOsdChannelName.contentDescription = channel.name
        binding.btnOsdFavourite.isSelected = isFavourite(channel)
        if (changed) artwork?.bind(binding.ivOsdLogo, channel.logo)
        updateStreamDetails(); Anim.showOverlay(binding.osdContainer)
        handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, if (autoHideMs > 0) autoHideMs else 5000L)
    }
    fun toggleDetails() { expanded = !expanded; updateExpanded(); resetAutoHide() }
    private fun updateExpanded() {
        binding.tvOsdTechnical.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.btnOsdLastChannel.visibility = if (expanded) View.VISIBLE else View.GONE
    }
    fun showPendingZapOsd(channel: Channel, nextChannelName: String?) {
        showOsd(channel)
        if (!nextChannelName.isNullOrBlank()) {
            tvGhostNext.text = "↑ NEXT: $nextChannelName"; tvGhostNext.visibility = View.VISIBLE
            handler.removeCallbacks(hideGhostRunnable); handler.postDelayed(hideGhostRunnable, 1200)
        }
    }
    fun hideOsd() {
        val hadFocus = binding.root.hasFocus(); controlsActive = false
        binding.root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.root.clearFocus(); handler.removeCallbacks(hideRunnable); Anim.hideOverlay(binding.osdContainer)
        if (hadFocus) (binding.root.parent as? View)?.requestFocus()
    }
    fun isVisible() = binding.osdContainer.visibility == View.VISIBLE
    fun updateTracks(videoFormat: Format?, audioFormat: Format?) { video = videoFormat; audio = audioFormat; updateStreamDetails() }
    fun setPlaybackInfo(playWhenReady: Boolean, isLive: Boolean, state: String) {
        status = if (isLive && state == binding.root.context.getString(R.string.playing)) binding.root.context.getString(R.string.live) else state
        binding.btnOsdPlayPause.setImageResource(if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        binding.btnOsdPlayPause.contentDescription = if (playWhenReady) "Pause" else "Play"
        updateStreamDetails()
    }
    fun setBuffering(buffering: Boolean) { binding.osdBufferingLayout.visibility = if (buffering) View.VISIBLE else View.GONE }
    fun refresh() { currentChannel?.let { binding.btnOsdFavourite.isSelected = isFavourite(it) }; updateStreamDetails() }
    private fun updateStreamDetails() {
        binding.tvOsdClock.text = StreamInfoFormatter.formatCurrentTime()
        val resolution = video?.takeIf { it.height > 0 }?.let { "${it.height}p" }
        binding.tvOsdStreamDetails.text = listOfNotNull(status, resolution).joinToString(" · ")
        binding.tvOsdTechnical.text = StreamInfoFormatter.buildStreamInfoString(video, audio)
        val (now, next) = currentChannel?.let(programmes) ?: (null to null)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val lines = listOfNotNull(now?.let { binding.root.context.getString(R.string.now_programme, "${time.format(java.util.Date(it.start))}–${time.format(java.util.Date(it.stop))} · ${it.title}") },
            next?.let { binding.root.context.getString(R.string.next_programme, "${time.format(java.util.Date(it.start))} · ${it.title}") })
        binding.tvOsdProgramme.text = lines.joinToString("\n")
        binding.tvOsdProgramme.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        binding.programmeProgress.visibility = if (now == null) View.GONE else View.VISIBLE
        if (now != null) binding.programmeProgress.progress = (((System.currentTimeMillis() - now.start) * 100 / (now.stop - now.start)).toInt()).coerceIn(0, 100)
    }
    fun pause() { handler.removeCallbacksAndMessages(null) }
    fun resume() { handler.removeCallbacksAndMessages(null); handler.post(clock) }
    fun destroy() { handler.removeCallbacksAndMessages(null) }
}
