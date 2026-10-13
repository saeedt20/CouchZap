package com.couchzap.iptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.util.Prefs
import com.couchzap.iptv.R
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Timeline
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.*

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val videoFrame: androidx.media3.ui.AspectRatioFrameLayout,
    private val listener: PlayerEventListener,
    private val subtitleView: SubtitleView? = null
) {

    interface PlayerEventListener {
        fun onRenderedFirstFrame()
        fun onTracksUpdated(videoFormat: Format?, audioFormat: Format?)
        fun onVideoSizeChanged(width: Int, height: Int)
        fun onPlaybackStateChanged(state: Int, isBuffering: Boolean)
        fun onError(message: String)
        fun onChannelFailed(channel: Channel)
        fun onPlaybackInfo(playWhenReady: Boolean, isLive: Boolean, status: String) {}
    }

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentChannel: Channel? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var preparationJob: Job? = null
    private var desiredPlay = false
    private var resolvedMime: String? = null
    private var currentTracks: Tracks = Tracks.EMPTY
    private var aspectMode = Prefs.ASPECT_FIT
    private var videoAspect = 16f / 9f
    private val retryRunnable = Runnable { retryCurrentChannel() }

    private fun cancelRetry() {
        mainHandler.removeCallbacks(retryRunnable)
    }

    private fun updateAspectRatio() {
        videoFrame.resizeMode = if (aspectMode == Prefs.ASPECT_FILL)
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        videoFrame.setAspectRatio(when (aspectMode) {
            Prefs.ASPECT_16_9 -> 16f / 9f
            Prefs.ASPECT_4_3 -> 4f / 3f
            else -> videoAspect
        })
    }
    private var retryCount = 0
    private var hasDecoderFallbackAttempted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeVideoFormat: Format? = null
    private var activeAudioFormat: Format? = null

    private val playerListener = object : Player.Listener {

        override fun onRenderedFirstFrame() {
            listener.onRenderedFirstFrame()
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentTracks = tracks
            var vFormat: Format? = null
            var aFormat: Format? = null

            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            vFormat = group.getTrackFormat(i)
                            break
                        }
                    }
                } else if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            aFormat = group.getTrackFormat(i)
                            break
                        }
                    }
                }
            }

            activeVideoFormat = vFormat
            activeAudioFormat = aFormat
            listener.onTracksUpdated(vFormat, aFormat)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0 && videoSize.width > 0) {
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                    videoAspect = 1f / videoAspect
                }
                updateAspectRatio()
            }
            listener.onVideoSizeChanged(videoSize.width, videoSize.height)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuffering = playbackState == Player.STATE_BUFFERING
            listener.onPlaybackStateChanged(playbackState, isBuffering)
            publishPlaybackInfo()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { publishPlaybackInfo() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { publishPlaybackInfo() }
        override fun onTimelineChanged(timeline: Timeline, reason: Int) { publishPlaybackInfo() }
        override fun onCues(cueGroup: CueGroup) { subtitleView?.setCues(cueGroup.cues) }

        override fun onPlayerError(error: PlaybackException) {
            val channel = currentChannel ?: return
            mainHandler.removeCallbacks(retryRunnable)
            if (!desiredPlay) return

            // Decoder error recovery
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && !hasDecoderFallbackAttempted) {
                hasDecoderFallbackAttempted = true
                trackSelector?.setParameters(
                    trackSelector!!.buildUponParameters()
                        .setMaxVideoSize(1280, 720)
                )
                mainHandler.post(retryRunnable)
                return
            }

            if (retryCount < 3) {
                retryCount++
                listener.onPlaybackInfo(true, false, context.getString(R.string.retrying, retryCount, 3))
                mainHandler.postDelayed(retryRunnable, 1200L * retryCount)
            } else {
                listener.onChannelFailed(channel)
            }
        }
    }

    init {
        initPlayer()
    }

    fun initPlayer() {
        if (exoPlayer != null) return

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 20000, 1500, 2500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceLowestBitrate(false)
                    .clearVideoSizeConstraints()
            )
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector!!)
            .setLoadControl(loadControl)
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
            .apply {
                setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                setVideoSurfaceView(surfaceView)
                addListener(playerListener)
            }
    }

    fun playChannel(channel: Channel) {
        val player = exoPlayer ?: return
        cancelRetry()
        currentChannel = channel
        desiredPlay = true
        preparationJob?.cancel()
        subtitleView?.setCues(emptyList())
        currentTracks = Tracks.EMPTY
        trackSelector?.setParameters(trackSelector!!.buildUponParameters().clearVideoSizeConstraints())
        activeVideoFormat = null
        activeAudioFormat = null
        listener.onTracksUpdated(null, null)
        retryCount = 0
        hasDecoderFallbackAttempted = false

        listener.onPlaybackInfo(true, false, context.getString(R.string.connecting))
        preparationJob = scope.launch {
            resolvedMime = withContext(Dispatchers.IO) { StreamTypeResolver.resolve(channel) }
            ensureActive()
            if (currentChannel == channel && desiredPlay) prepare(channel)
        }
    }

    private fun prepare(channel: Channel) {
        val player = exoPlayer ?: return
        val item = MediaItem.Builder().setUri(channel.url).setMimeType(resolvedMime ?: channel.mimeType).build()
        player.setMediaSource(createMediaSourceFactoryForChannel(channel).createMediaSource(item))
        player.prepare()
        player.playWhenReady = desiredPlay
    }

    private fun publishPlaybackInfo() {
        val player = exoPlayer ?: return
        val state = when {
            !desiredPlay -> context.getString(R.string.paused)
            player.playbackState == Player.STATE_BUFFERING -> context.getString(R.string.buffering)
            player.playbackState != Player.STATE_READY -> context.getString(R.string.connecting)
            player.isCurrentMediaItemLive -> context.getString(R.string.live)
            else -> context.getString(R.string.playing)
        }
        listener.onPlaybackInfo(desiredPlay, player.isCurrentMediaItemLive, state)
    }

    private fun createMediaSourceFactoryForChannel(channel: Channel): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setUserAgent(channel.userAgent ?: "CouchZap/2.0")

        if (!channel.referer.isNull_or_empty()) {
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to channel.referer!!))
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    fun stop() {
        preparationJob?.cancel()
        desiredPlay = false
        subtitleView?.setCues(emptyList())
        cancelRetry()
        currentChannel = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun togglePlayPause() {
        if (desiredPlay) pause() else play()
    }
    fun pause() { desiredPlay = false; cancelRetry(); preparationJob?.cancel(); exoPlayer?.pause(); publishPlaybackInfo() }
    fun play() {
        val player = exoPlayer ?: return
        desiredPlay = true
        if (player.playbackState == Player.STATE_IDLE && currentChannel != null) playChannel(currentChannel!!) else player.play()
        publishPlaybackInfo()
    }

    data class TrackChoice(val label: String, val group: androidx.media3.common.TrackGroup, val index: Int)
    fun choices(type: Int): List<TrackChoice> = currentTracks.groups.filter { it.type == type }.flatMap { group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { index ->
            val format = group.getTrackFormat(index)
            val language = format.language?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
            val label = format.label ?: language?.takeIf { it.isNotBlank() } ?: "Track ${index + 1}"
            TrackChoice(label, group.mediaTrackGroup, index)
        }
    }
    fun selectTrack(type: Int, choice: TrackChoice?, disabled: Boolean = false) {
        val player = exoPlayer ?: return
        val parameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type).setTrackTypeDisabled(type, disabled)
        if (choice != null) parameters.setOverrideForType(TrackSelectionOverride(choice.group, choice.index))
        player.trackSelectionParameters = parameters.build()
    }

    fun setAspectRatioMode(mode: Int) {
        aspectMode = mode
        updateAspectRatio()
    }

    fun release() {
        preparationJob?.cancel()
        scope.cancel()
        cancelRetry()
        currentChannel = null
        mainHandler.removeCallbacksAndMessages(null)
        exoPlayer?.apply {
            removeListener(playerListener)
            clearVideoSurface()
            stop()
            clearMediaItems()
            release()
        }
        exoPlayer = null
        trackSelector = null
    }

    private fun retryCurrentChannel() {
        val channel = currentChannel ?: return
        val player = exoPlayer ?: return
        if (desiredPlay) prepare(channel)
    }

    fun getActiveFormats(): Pair<Format?, Format?> = Pair(activeVideoFormat, activeAudioFormat)
}
