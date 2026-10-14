package com.couchzap.iptv.support

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.couchzap.iptv.data.HttpStreams
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object ReleaseUpdater {
    const val RELEASE_API = "https://api.github.com/repos/saeedt20/CouchZap/releases/latest"
    const val CERTIFICATE_SHA256 = "539f14a4b71cc830108cffefa8778ec06797be539c90ab80bc0c89b0c60eb187"
    data class Release(val version: String, val url: String, val checksumUrl: String, val digest: String?, val size: Long, val notes: String)
    fun versionParts(value: String): List<Int>? = value.removePrefix("v").takeIf {
        it.matches(Regex("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}"))
    }?.split('.')?.map { it.toInt() }
    fun isNewer(candidate: String, installed: String): Boolean {
        val next = versionParts(candidate) ?: return false
        val current = versionParts(installed) ?: return false
        for (i in next.indices) if (next[i] != current[i]) return next[i] > current[i]
        return false
    }
    fun parse(json: JSONObject): Release {
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) { "Not a stable release" }
        val tag = json.getString("tag_name")
        require(tag.startsWith("v") && versionParts(tag) != null) { "Invalid release version" }
        val version = tag.removePrefix("v")
        val name = "CouchZap-$version.apk"
        val expected = "https://github.com/saeedt20/CouchZap/releases/download/$tag/$name"
        val assets = json.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.single { it.optString("name") == name }
        require(asset.getString("browser_download_url") == expected) { "Unexpected download source" }
        val size = asset.getLong("size")
        require(size in 1..80L * 1024 * 1024) { "Invalid APK size" }
        val digest = asset.optString("digest").takeIf { it.matches(Regex("sha256:[0-9a-fA-F]{64}")) }?.substringAfter(':')?.lowercase()
        return Release(version, expected, "$expected.sha256", digest, size, json.optString("body").take(2000))
    }
    fun latest(): Release = HttpStreams.open(RELEASE_API, 1024 * 1024).use { parse(JSONObject(it.bufferedReader().readText())) }
    fun download(context: Context, release: Release, progress: (Int) -> Unit): File {
        val checksum = release.digest ?: HttpStreams.open(release.checksumUrl, 4096).use {
            it.bufferedReader().readText().trim().split(Regex("\\s+")).first().lowercase()
        }
        require(checksum.matches(Regex("[0-9a-f]{64}"))) { "Invalid release checksum" }
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(dir, "download.part")
        val output = File(dir, "CouchZap-${release.version}.apk")
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            HttpStreams.open(release.url, 80L * 1024 * 1024).use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
                        out.write(buffer, 0, count); hash.update(buffer, 0, count); total += count
                        val percent = (total * 100 / release.size).coerceIn(0, 100).toInt()
                        if (percent != lastPercent) { lastPercent = percent; progress(percent) }
                    }
                }
            }
            require(total == release.size && hex(hash.digest()) == checksum) { "Downloaded APK checksum mismatch" }
            verifyApk(context, partial, release.version)
            if (output.exists()) check(output.delete())
            check(partial.renameTo(output)) { "Could not save APK" }
            return output
        } finally { partial.delete() }
    }
    @Suppress("DEPRECATION")
    fun verifyApk(context: Context, file: File, version: String) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Not an APK")
        require(archive.packageName == context.packageName && archive.versionName == version) { "Wrong application or version" }
        val signatures = (if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures) ?: error("Missing signing certificate")
        require(signatures.size == 1 && hex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray())) == CERTIFICATE_SHA256) { "Wrong signing certificate" }
    }
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
