package com.couchzap.iptv

import android.content.Context
import android.view.SurfaceView
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.core.app.ApplicationProvider
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.player.ChannelPlaybackStatus
import com.couchzap.iptv.player.PlayerController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerSessionRegressionTest {
    @Test fun `queued callbacks from a previous channel or stopped session cannot publish UI events`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val events = mutableListOf<String>()
        val sink = object : PlayerController.PlayerEventListener {
            override fun onRenderedFirstFrame() { events += "frame" }
            override fun onTracksUpdated(videoFormat: Format?, audioFormat: Format?) { events += "tracks" }
            override fun onVideoSizeChanged(width: Int, height: Int) { events += "size" }
            override fun onPlaybackStateChanged(state: Int, isBuffering: Boolean) { events += "state" }
            override fun onError(message: String) { events += "error" }
            override fun onChannelFailed(channel: Channel) { events += "failed" }
            override fun onPlaybackInfo(playWhenReady: Boolean, isLive: Boolean, status: String) { events += "info" }
            override fun onChannelPlaybackChanged(channel: Channel, status: ChannelPlaybackStatus) { events += "channel" }
        }
        val controller = PlayerController(context, SurfaceView(context), AspectRatioFrameLayout(context), sink)
        fun currentListener(): Player.Listener = PlayerController::class.java.getDeclaredField("playerListener")
            .apply { isAccessible = true }.get(controller) as Player.Listener
        fun sendQueuedEvents(listener: Player.Listener) {
            listener.onIsPlayingChanged(true)
            listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            listener.onPlaybackStateChanged(Player.STATE_READY)
            listener.onTracksChanged(Tracks.EMPTY)
            listener.onRenderedFirstFrame()
            listener.onPlayerError(PlaybackException("fixture", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
        }
        try {
            controller.playChannel(Channel(1, "One", "file:///one.ts"))
            val previous = currentListener()
            controller.playChannel(Channel(2, "Two", "file:///two.ts"))
            val afterSwitch = events.toList()
            sendQueuedEvents(previous)
            assertEquals(afterSwitch, events)
            val stopped = currentListener()
            controller.stop()
            val afterStop = events.toList()
            sendQueuedEvents(stopped)
            assertEquals(afterStop, events)
        } finally {
            controller.release()
        }
    }
}
