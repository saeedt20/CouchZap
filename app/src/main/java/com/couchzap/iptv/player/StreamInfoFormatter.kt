package com.couchzap.iptv.player

import androidx.media3.common.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object StreamInfoFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun formatCurrentTime(): String {
        return timeFormat.format(Date())
    }

    fun formatVideoCodec(mimeType: String?): String {
        if (mimeType == null) return "Video"
        val lower = mimeType.lowercase()
        return when {
            lower.contains("avc") || lower.contains("h264") -> "H.264"
            lower.contains("hevc") || lower.contains("h265") -> "H.265/HEVC"
            lower.contains("vp9") -> "VP9"
            lower.contains("mp4v") -> "MPEG-4"
            lower.contains("av1") -> "AV1"
            else -> "Video"
        }
    }

    fun formatQualityTag(height: Int): String {
        return when {
            height <= 0 -> ""
            height <= 360 -> "LQ"
            height <= 576 -> "SD"
            height <= 720 -> "HD"
            height <= 1080 -> "FHD"
            height <= 1440 -> "QHD"
            height <= 2160 -> "4K"
            else -> "${height}p"
        }
    }

    fun formatResolution(width: Int, height: Int, fps: Float): String {
        if (height <= 0) return ""
        val res = "${height}p"
        return if (fps > 30.5f) {
            "$res@${fps.toInt()}"
        } else {
            res
        }
    }

    fun formatBitrate(bitrate: Int): String {
        if (bitrate <= 0) return ""
        val mbps = bitrate / 1_000_000f
        return String.format(Locale.US, "%.1f Mbps", mbps)
    }

    fun formatAudioCodec(mimeType: String?): String {
        if (mimeType == null) return "Audio"
        val lower = mimeType.lowercase()
        return when {
            lower.contains("mp4a") || lower.contains("aac") -> "AAC"
            lower.contains("mpeg") || lower.contains("mp3") -> "MP3"
            lower.contains("ac3") || lower.contains("eac3") -> "AC3"
            lower.contains("opus") -> "OPUS"
            lower.contains("flac") -> "FLAC"
            else -> "Audio"
        }
    }

    fun buildStreamInfoString(videoFormat: Format?, audioFormat: Format?, context: android.content.Context? = null): String {
        val parts = mutableListOf<String>()

        // 2. Video Codec
        if (videoFormat != null) {
            val vCodec = formatVideoCodec(videoFormat.sampleMimeType)
            if (vCodec.isNotEmpty()) parts.add(if (vCodec == "Video" && context != null) context.getString(com.couchzap.iptv.R.string.video) else vCodec)

            // 3. Resolution
            val res = formatResolution(videoFormat.width, videoFormat.height, videoFormat.frameRate)
            if (res.isNotEmpty()) parts.add(res)
            if (videoFormat.width > 0 && videoFormat.height > 0) parts.add("${videoFormat.width} × ${videoFormat.height}")
            if (videoFormat.frameRate > 0) parts.add(String.format(Locale.US, "%.2f fps", videoFormat.frameRate))

            // 5. Bitrate
            val bitrateStr = formatBitrate(videoFormat.bitrate)
            if (bitrateStr.isNotEmpty()) parts.add(context?.getString(com.couchzap.iptv.R.string.bitrate, bitrateStr) ?: "Bitrate $bitrateStr")
        }

        // 6. Audio Codec
        if (audioFormat != null) {
            val aCodec = formatAudioCodec(audioFormat.sampleMimeType)
            if (aCodec.isNotEmpty()) parts.add(if (aCodec == "Audio" && context != null) context.getString(com.couchzap.iptv.R.string.audio_codec) else aCodec)
        }

        return parts.joinToString("  •  ")
    }
}
