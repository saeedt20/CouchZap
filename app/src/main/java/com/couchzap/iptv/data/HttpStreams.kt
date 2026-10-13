package com.couchzap.iptv.data

import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object HttpStreams {
    fun validUrl(value: String): Boolean = runCatching {
        val uri = URI(value.trim())
        uri.scheme?.lowercase(java.util.Locale.ROOT) in listOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    fun open(url: String, maxBytes: Long = 40L * 1024 * 1024): InputStream {
        require(validUrl(url)) { "Enter a valid HTTP or HTTPS link" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "CouchZap/2.0")
        connection.setRequestProperty("Accept-Encoding", "identity")
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("The source returned HTTP $code")
            if (connection.contentLengthLong > maxBytes) throw IOException("This file is too large")
            return object : LimitedInputStream(connection.inputStream, maxBytes) {
                override fun close() { try { super.close() } finally { connection.disconnect() } }
            }
        } catch (error: Exception) { connection.disconnect(); throw error }
    }
}

open class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    private var readBytes = 0L
    private fun account(amount: Int) {
        if (amount > 0) readBytes += amount
        if (readBytes > limit) throw IOException("This file is too large")
    }
    override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also(::account)
}
