package com.couchzap.iptv.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.couchzap.iptv.R
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.util.Prefs
import com.couchzap.iptv.support.DiagnosticLog
import java.util.concurrent.Executors

/** Policy is separate from playback: reconnects/retries never call this channel-commit hook. */
class CrtSwitchSound(private val prefs: Prefs, private val backend: Backend, private val muted: () -> Boolean) {
    interface Backend {
        fun prepare(ready: (Boolean) -> Unit)
        fun play(gain: Float)
        fun stop()
        fun release()
    }
    private var ready = false
    private var preparing = false
    private var pendingPreview = false
    private var active = true
    private var closed = false
    init { if (prefs.isCrtSoundEnabled()) prepare() }
    private fun prepare() {
        if (ready || preparing || closed) return
        preparing = true
        backend.prepare { loaded ->
            if (!closed) { ready = loaded; preparing = false; if (pendingPreview) { pendingPreview = false; play() } }
        }
    }
    fun setEnabled(value: Boolean) {
        prefs.setCrtSoundEnabled(value)
        pendingPreview = false
        if (value) { if (ready) play() else { pendingPreview = true; prepare() } }
        else backend.stop()
    }
    fun committed(previous: Channel?, next: Channel) {
        if (previous != null && previous.stableId != next.stableId) play()
    }
    private fun play() {
        if (!closed && active && ready && prefs.isCrtSoundEnabled() && !muted()) { backend.stop(); backend.play(.35f) }
    }
    fun pause() { active = false; pendingPreview = false; backend.stop() }
    fun resume() { active = true; if (prefs.isCrtSoundEnabled()) prepare() }
    fun close() { closed = true; pendingPreview = false; backend.release() }
    companion object {
        fun create(context: Context, prefs: Prefs): CrtSwitchSound {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return CrtSwitchSound(prefs, LocalSoundPool(context.applicationContext)) {
                audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 ||
                    audio.ringerMode == AudioManager.RINGER_MODE_SILENT
            }
        }
    }
}

/** Construction, local decoding and release happen on one worker; completion is delivered on main. */
private class LocalSoundPool(private val context: Context) : CrtSwitchSound.Backend {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var pool: SoundPool? = null
    private var sample = 0
    private var stream = 0
    @Volatile private var released = false
    private fun submit(action: () -> Unit) {
        if (released) return
        runCatching { worker.execute { if (!released) runCatching(action).onFailure { DiagnosticLog.record("crt_audio_error", it) } } }
            .onFailure { DiagnosticLog.record("crt_audio_queue_error", it) }
    }
    override fun prepare(ready: (Boolean) -> Unit) {
        submit {
            fun complete(success: Boolean) { main.post { if (!released) ready(success) } }
            runCatching {
                pool?.release()
                val sound = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
                pool = sound
                sound.setOnLoadCompleteListener { _, _, status -> complete(status == 0) }
                sample = sound.load(context, R.raw.crt_switch, 1)
                if (sample == 0) complete(false)
            }.onFailure { DiagnosticLog.record("crt_audio_load_error", it); complete(false) }
        }
    }
    override fun play(gain: Float) { submit { pool?.stop(stream); stream = pool?.play(sample, gain, gain, 1, 0, 1f) ?: 0 } }
    override fun stop() { submit { pool?.stop(stream); stream = 0 } }
    override fun release() {
        if (released) return
        released = true
        worker.execute {
            runCatching { pool?.release() }.onFailure { DiagnosticLog.record("crt_audio_release_error", it) }
            pool = null; main.removeCallbacksAndMessages(null)
        }
        worker.shutdown()
    }
}
