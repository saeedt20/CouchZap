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

@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val videoFrame: androidx.media3.ui.AspectRatioFrameLayout,
    private val listener: PlayerEventListener
) {

    interface PlayerEventListener {
        fun onRenderedFirstFrame()
        fun onTracksUpdated(videoFormat: Format?, audioFormat: Format?)
        fun onVideoSizeChanged(width: Int, height: Int)
        fun onPlaybackStateChanged(state: Int, isBuffering: Boolean)
        fun onError(message: String)
        fun onChannelFailed(channel: Channel)
    }

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentChannel: Channel? = null
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
        }

        override fun onPlayerError(error: PlaybackException) {
            val channel = currentChannel ?: return
            mainHandler.removeCallbacks(retryRunnable)

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
        trackSelector?.setParameters(trackSelector!!.buildUponParameters().clearVideoSizeConstraints())
        activeVideoFormat = null
        activeAudioFormat = null
        listener.onTracksUpdated(null, null)
        retryCount = 0
        hasDecoderFallbackAttempted = false

        val mediaSourceFactory = createMediaSourceFactoryForChannel(channel)
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(channel.url)))
        player.prepare()
        player.playWhenReady = true
    }

    private fun createMediaSourceFactoryForChannel(channel: Channel): DefaultMediaSourceFactory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setUserAgent(channel.userAgent ?: "CouchZap/1.0")

        if (!channel.referer.isNull_or_empty()) {
            httpFactory.setDefaultRequestProperties(mapOf("Referer" to channel.referer!!))
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    fun stop() {
        cancelRetry()
        currentChannel = null
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.playWhenReady) {
            cancelRetry()
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && currentChannel != null) retryCurrentChannel()
            else player.play()
        }
    }

    fun setAspectRatioMode(mode: Int) {
        aspectMode = mode
        updateAspectRatio()
    }

    fun release() {
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
        val mediaSourceFactory = createMediaSourceFactoryForChannel(channel)
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(channel.url)))
        player.prepare()
        player.playWhenReady = true
    }

    fun getActiveFormats(): Pair<Format?, Format?> = Pair(activeVideoFormat, activeAudioFormat)
}
