package com.couchzap.iptv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import android.widget.ImageView
import com.couchzap.iptv.R
import com.couchzap.iptv.data.HttpStreams
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ChannelArtwork(private val scope: CoroutineScope) {
    private val permits = Semaphore(3)
    private val jobs = java.util.WeakHashMap<ImageView, Job>()
    fun bind(view: ImageView, url: String?) {
        jobs.remove(view)?.cancel()
        view.setTag(R.id.artwork_request, url)
        view.setImageResource(R.drawable.ic_station)
        view.imageTintList = androidx.core.content.ContextCompat.getColorStateList(view.context, R.color.selector_channel_text)
        if (url == null || !HttpStreams.validUrl(url)) return
        cache.get(url)?.let { view.imageTintList = null; view.setImageBitmap(it); return }
        jobs[view] = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { permits.withPermit { runCatching {
                val bytes = HttpStreams.open(url, 2L * 1024 * 1024).use { it.readBytes() }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth > 0) {
                    options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 192).coerceAtLeast(1)
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } else {
                    val svg = SVG.getFromString(bytes.toString(Charsets.UTF_8))
                    Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also {
                        svg.setDocumentWidth(192f); svg.setDocumentHeight(192f); svg.renderToCanvas(Canvas(it))
                    }
                }
            }.getOrNull() } }
            if (bitmap != null) { cache.put(url, bitmap); if (view.getTag(R.id.artwork_request) == url) { view.imageTintList = null; view.setImageBitmap(bitmap) } }
        }
    }
    companion object {
        private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }
    }
}
