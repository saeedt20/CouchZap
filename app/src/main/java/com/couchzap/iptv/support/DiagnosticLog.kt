package com.couchzap.iptv.support

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** An app-only, bounded log. Never records exception messages, URLs or playlist values. */
object DiagnosticLog {
    private var destination: File? = null
    private var handlerInstalled = false
    @Synchronized fun initialize(context: Context) {
        destination = File(context.applicationContext.filesDir, "diagnostics.txt")
        if (!handlerInstalled) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                record("uncaught_exception", error)
                previous?.uncaughtException(thread, error)
            }
            handlerInstalled = true
        }
    }
    fun safeError(error: Throwable): String {
        val output = StringBuilder()
        var current: Throwable? = error
        repeat(6) {
            val cause = current ?: return@repeat
            output.append(cause.javaClass.name).append('\n')
            cause.stackTrace.take(12).forEach { output.append("  at ").append(it.toString()).append('\n') }
            current = cause.cause?.takeUnless { it === cause }
        }
        return output.toString()
    }
    @Synchronized fun record(event: String, error: Throwable? = null) {
        // Call sites supply fixed event identifiers only. No arbitrary metadata is accepted.
        if (!event.matches(Regex("[a-z0-9_:-]{1,80}"))) return
        runCatching {
            val file = destination ?: return
            val previous = if (file.exists()) file.readText().takeLast(48 * 1024) else ""
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).format(Date())
            file.writeText(previous + "$stamp $event\n" + (error?.let(::safeError) ?: ""))
        }
    }
    @Synchronized fun report(context: Context, sourceKind: String?, channelCount: Int): String {
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return buildString {
            appendLine("CouchZap diagnostic report")
            appendLine("Version: ${info.versionName} (${info.versionCode})")
            appendLine("Android: ${Build.VERSION.RELEASE}; API: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Timezone: ${TimeZone.getDefault().id}")
            appendLine("Playlist kind: ${sourceKind?.takeIf { it in setOf("file", "url", "public") } ?: "none"}; channels: $channelCount")
            appendLine("No playlist URLs, channel names, tokens, account data or system-wide logcat included.")
            appendLine()
            append(destination?.let { runCatching { it.readText() }.getOrDefault("") } ?: "No events recorded.")
        }
    }
}
