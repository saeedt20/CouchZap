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
import com.couchzap.iptv.ui.design.*
import android.widget.LinearLayout
import android.view.ViewGroup

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
    private val hideRunnable = Runnable {
        val accessibility = binding.root.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (controlsActive && (accessibility?.isTouchExplorationEnabled == true || buttons.any { it.isPressed } || binding.tvOsdTechnical.hasSelection() || binding.czInfoTechnicalAudio.hasSelection())) resetAutoHide() else hideOsd()
    }
    private val hideGhostRunnable = Runnable { tvGhostNext.visibility = View.GONE }
    private var currentChannel: Channel? = null
    private var video: Format? = null
    private var audio: Format? = null
    private var status = binding.root.context.getString(R.string.connecting)
    private var expanded = false
    private var lastFocused = R.id.btn_osd_channels
    private val motion = OverlayMotion(binding.root, bottom = true)
    private val buttons get() = listOf(binding.btnOsdChannels,binding.btnOsdPrev,binding.btnOsdPlayPause,binding.btnOsdNext,
        binding.btnOsdFavourite,binding.btnOsdAspect,binding.btnOsdSettings,binding.btnOsdLastChannel)
    private var mobileRow: LinearLayout? = null
    private val clock = object : Runnable {
        override fun run() { updateStreamDetails(); handler.postDelayed(this, 60000) }
    }
    init {
        binding.root.isClickable=true
        binding.btnOsdFavourite.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.ToggleButton"
                info.isCheckable = true
                info.isChecked = host.isSelected
            }
        }
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
            // Item 2: the favourite star keeps selected state semantics but draws no edge marker.
            if (button === binding.btnOsdFavourite) FocusMotionBinder.bind(button,FocusMotionBinder.Role.ICON,selectedMarker=false)
            else FocusMotionBinder.bind(button,FocusMotionBinder.Role.ICON)
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
        binding.tvOsdChannelNumber.text = com.couchzap.iptv.util.LocalizedNumbers.channelNumber(channel.number, com.couchzap.iptv.util.Prefs(binding.root.context).getLanguage())
        binding.tvOsdChannelName.text = channel.name
        binding.tvOsdChannelName.contentDescription = channel.name
        updateFavourite(channel)
        if (changed) artwork?.bind(binding.ivOsdLogo, channel.logo)
        updateStreamDetails(); motion.show()
        handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, if (autoHideMs > 0) autoHideMs else 5000L)
    }
    fun toggleDetails() { expanded = !expanded; updateExpanded(); resetAutoHide() }
    private fun updateExpanded() {
        binding.czInfoDetailsScroll.visibility=if(expanded)View.VISIBLE else View.GONE
        binding.tvOsdProgramme.maxLines=if(expanded)Int.MAX_VALUE else 2
        binding.tvOsdTechnical.visibility = if (expanded&&video!=null) View.VISIBLE else View.GONE
        binding.czInfoTechnicalAudio.visibility=if(expanded&&audio!=null)View.VISIBLE else View.GONE
        binding.btnOsdLastChannel.visibility = if (expanded) View.VISIBLE else View.GONE
    }
    fun showPendingZapOsd(channel: Channel, nextChannelName: String?) {
        showOsd(channel)
        if (!nextChannelName.isNullOrBlank()) {
            tvGhostNext.text = binding.root.context.getString(R.string.next_channel_ghost, nextChannelName); tvGhostNext.visibility = View.VISIBLE
            handler.removeCallbacks(hideGhostRunnable); handler.postDelayed(hideGhostRunnable, 1200)
        }
    }
    fun hideOsd() {
        val hadFocus = binding.root.hasFocus(); controlsActive = false
        binding.root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        binding.root.clearFocus(); handler.removeCallbacks(hideRunnable); motion.hide()
        if (hadFocus) (binding.root.parent as? View)?.requestFocus()
    }
    fun isVisible() = motion.desiredOpen
    fun updateLayout(tv: Boolean,ui:AdaptiveUi=AdaptiveUi.from(binding.root.rootView,tv)) {
        val focused=binding.root.findFocus()?.id
        CouchZapTokens.text(binding.tvOsdChannelName,CouchZapTokens.Text.PANE,tv)
        binding.tvOsdChannelNumber.textDirection=View.TEXT_DIRECTION_LTR
        listOf(binding.tvOsdChannelNumber,binding.tvOsdClock,binding.tvOsdStreamDetails).forEach { CouchZapTokens.text(it,CouchZapTokens.Text.VALUE,tv) }
        CouchZapTokens.text(binding.tvOsdProgramme,CouchZapTokens.Text.BODY,tv)
        CouchZapTokens.text(binding.tvOsdTechnical,CouchZapTokens.Text.VALUE,tv)
        CouchZapTokens.text(binding.czInfoTechnicalAudio,CouchZapTokens.Text.VALUE,tv)
        CouchZapTokens.text(binding.tvOsdActionLabel,CouchZapTokens.Text.FOOTER,tv)
        CouchZapTokens.text(binding.localizedLabel15,CouchZapTokens.Text.FOOTER,tv)
        CouchZapTokens.text(binding.localizedLabel14,CouchZapTokens.Text.VALUE,tv)
        CouchZapTokens.text(binding.btnOsdLastChannel,CouchZapTokens.Text.CONTROL,tv)
        val d=binding.root.resources.displayMetrics.density
        val dockHeight=if(tv)(ui.safeHeight-ui.dp(48)).coerceAtLeast(ui.dp(200))else(ui.height-ui.dp(32)).coerceAtLeast(ui.dp(160))
        binding.root.maximumHeight=dockHeight
        val short=!tv&&ui.short
        (binding.czInfoMetadataScroll.layoutParams as LinearLayout.LayoutParams).apply{height=if(short)0 else -2;weight=if(short)1f else 0f}
        binding.czInfoMetadataScroll.maximumHeight=(dockHeight-ui.dp(if(tv)140 else 168)).coerceAtLeast(ui.dp(48))
        binding.czInfoDetailsScroll.maximumHeight=minOf(ui.dp(if(short)88 else 180),(dockHeight*.35f).toInt())
        val controls=binding.osdTouchControls
        (controls.parent as android.widget.HorizontalScrollView).isFillViewport=!tv
        controls.gravity=if(tv)android.view.Gravity.START else android.view.Gravity.CENTER_HORIZONTAL
        if(mobileRow==null) mobileRow=LinearLayout(binding.root.context).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_HORIZONTAL }
        val secondary=mobileRow!!
        if(secondary.parent==null) binding.root.addView(secondary,binding.root.indexOfChild(binding.tvOsdActionLabel.parent as View),LinearLayout.LayoutParams(-1,-2))
        secondary.visibility=if(tv)View.GONE else View.VISIBLE
        val primary=if(tv)listOf(binding.btnOsdChannels,binding.btnOsdPrev,binding.btnOsdPlayPause,binding.btnOsdNext,binding.btnOsdFavourite,binding.btnOsdAspect,binding.btnOsdSettings)
            else listOf(binding.btnOsdPrev,binding.btnOsdPlayPause,binding.btnOsdNext,binding.btnOsdFavourite)
        // Item 5: rebuild row membership only when the assignment actually changed,
        // so focus is not reset and touch streams are not interrupted.
        fun ensureRow(container:LinearLayout,desired:List<View>){
            val current=(0 until container.childCount).map{container.getChildAt(it)}
            if(current!=desired)desired.forEach{button->(button.parent as? ViewGroup)?.removeView(button);container.addView(button)}
        }
        ensureRow(controls,primary)
        if(!tv)ensureRow(secondary,listOf(binding.btnOsdChannels,binding.btnOsdAspect,binding.btnOsdSettings))
        buttons.filter { it !== binding.btnOsdLastChannel }.forEach { button ->
            val size=((if(tv)56 else 48)*d).toInt()
            val lp=LinearLayout.LayoutParams(size,size)
            if(tv)lp.marginEnd=(8*d).toInt()
            // Item 5: symmetric inter-item spacing on phone; no trailing-only offset.
            else{val gap=(6*d).toInt();lp.setMargins(gap,0,gap,0)}
            button.layoutParams=lp
            val inset=((if(tv)16 else 12)*d).toInt();button.setPadding(inset,inset,inset,inset)
        }
        // Item 5: the focus-dependent action label is stale on touch; hide it on phone.
        binding.tvOsdActionLabel.visibility=if(tv)View.VISIBLE else View.GONE
        binding.tvOsdChannelName.maxLines=if(tv)1 else 2
        if(controlsActive&&motion.desiredOpen&&focused!=null)binding.root.findViewById<View>(focused)?.requestFocusFromTouch()
    }
    fun settlePresentation()=motion.settle()
    fun updateTracks(videoFormat: Format?, audioFormat: Format?) { video = videoFormat; audio = audioFormat; updateStreamDetails() }
    fun setPlaybackInfo(playWhenReady: Boolean, isLive: Boolean, state: String) {
        status = if (isLive && state == binding.root.context.getString(R.string.playing)) binding.root.context.getString(R.string.live) else state
        binding.btnOsdPlayPause.setImageResource(if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play)
        binding.btnOsdPlayPause.contentDescription = binding.root.context.getString(if (playWhenReady) R.string.pause else R.string.play)
        updateStreamDetails()
    }
    fun setBuffering(buffering: Boolean) { binding.osdBufferingLayout.visibility = if (buffering) View.VISIBLE else View.GONE }
    fun refreshChannel(channel: Channel) {
        currentChannel = channel
        binding.tvOsdChannelName.text = channel.name
        binding.tvOsdChannelNumber.text = com.couchzap.iptv.util.LocalizedNumbers.channelNumber(channel.number, com.couchzap.iptv.util.Prefs(binding.root.context).getLanguage())
        refresh()
    }
    fun refresh() {
        currentChannel?.let {
            val selected = isFavourite(it)
            if (binding.btnOsdFavourite.isSelected != selected && isVisible()) Anim.favourite(binding.btnOsdFavourite)
            updateFavourite(it)
        }
        updateStreamDetails()
    }
    private fun updateFavourite(channel: Channel) {
        val button = binding.btnOsdFavourite
        button.isSelected = isFavourite(channel)
        button.contentDescription = binding.root.context.getString(
            if (button.isSelected) R.string.favourite_remove_action else R.string.favourite_add_action
        )
        if (button.isFocused || lastFocused == button.id) binding.tvOsdActionLabel.text = button.contentDescription
    }
    private fun updateStreamDetails() {
        // 3.1: clock digits follow the selected app language (FA/AR localized),
        // independent of the device locale. Kept as an LTR sequence in RTL UI.
        binding.tvOsdClock.text = StreamInfoFormatter.formatCurrentTime(
            com.couchzap.iptv.util.Prefs(binding.root.context).getLanguage())
        binding.tvOsdClock.textDirection = android.view.View.TEXT_DIRECTION_LTR
        val resolution = video?.takeIf { it.height > 0 }?.let { "${it.height}p" }
        binding.tvOsdStreamDetails.text = listOfNotNull(status, resolution?.let { androidx.core.text.BidiFormatter.getInstance().unicodeWrap(it,false) }).joinToString(" · ")
        binding.tvOsdTechnical.text = StreamInfoFormatter.buildStreamInfoString(video, null, binding.root.context).replace("  •  ","\n")
        binding.czInfoTechnicalAudio.text=StreamInfoFormatter.buildStreamInfoString(null,audio,binding.root.context)
        binding.tvOsdTechnical.visibility=if(expanded&&video!=null)View.VISIBLE else View.GONE
        binding.czInfoTechnicalAudio.visibility=if(expanded&&audio!=null)View.VISIBLE else View.GONE
        val (now, next) = currentChannel?.let(programmes) ?: (null to null)
        val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        val lines = listOfNotNull(now?.let { binding.root.context.getString(R.string.now_programme, "${time.format(java.util.Date(it.start))}–${time.format(java.util.Date(it.stop))} · ${it.title}") },
            next?.let { binding.root.context.getString(R.string.next_programme, "${time.format(java.util.Date(it.start))} · ${it.title}") })
        binding.tvOsdProgramme.text = lines.joinToString("\n")
        binding.tvOsdProgramme.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        binding.programmeProgress.visibility = if (now == null) View.GONE else View.VISIBLE
        if (now != null) binding.programmeProgress.progress = (((System.currentTimeMillis() - now.start) * 100 / (now.stop - now.start)).toInt()).coerceIn(0, 100)
    }
    fun pause() { handler.removeCallbacksAndMessages(null); motion.settle() }
    fun resume() { handler.removeCallbacksAndMessages(null); handler.post(clock) }
    fun destroy() { handler.removeCallbacksAndMessages(null); motion.hide();motion.settle() }
}
