package com.couchzap.iptv.ui

import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ArrayAdapter
import com.couchzap.iptv.R
import com.couchzap.iptv.data.HttpStreams
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.data.PlaylistSource
import kotlinx.coroutines.*
import org.json.JSONArray

class PlaylistMenus(
    private val activity: AppCompatActivity,
    private val repository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val onFile: () -> Unit,
    private val onSource: (PlaylistSource, Boolean) -> Unit,
    private val onNotice: (String) -> Unit
) {
    fun show() {
        val items = arrayOf(activity.getString(R.string.btn_import_playlist), activity.getString(R.string.import_link),
            activity.getString(R.string.public_channels), activity.getString(R.string.refresh_playlist))
        AlertDialog.Builder(activity).setTitle(R.string.playlist_sources).setItems(items) { _, index -> when (index) {
            0 -> onFile()
            1 -> link()
            2 -> publicChoices()
            3 -> repository.source()?.let { onSource(it, true) } ?: onNotice(activity.getString(R.string.no_refresh_source))
        } }.setNegativeButton(R.string.cancel, null).show()
    }
    fun link(title: Int = R.string.import_link, onAccept: (String) -> Unit = { onSource(PlaylistSource("url", it, "Playlist link"), false) }) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.enter_link); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true; setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(activity).setTitle(title).setView(input)
            .setPositiveButton(R.string.import_action, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = input.text.toString().trim()
            if (!HttpStreams.validUrl(url)) input.error = activity.getString(R.string.invalid_link)
            else { dialog.dismiss(); onAccept(url) }
        } }
        dialog.show()
    }
    fun publicChoices() {
        val items = arrayOf(activity.getString(R.string.all_public), activity.getString(R.string.by_country),
            activity.getString(R.string.by_language), activity.getString(R.string.by_category))
        AlertDialog.Builder(activity).setTitle(R.string.choose_public).setItems(items) { _, index ->
            if (index == 0) onSource(PlaylistSource.public(), false)
            else catalog(listOf("countries", "languages", "categories")[index - 1])
        }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun catalog(kind: String) {
        val loading = AlertDialog.Builder(activity).setTitle(R.string.choose_public).setMessage("Loading choices…")
            .setNegativeButton(R.string.cancel, null).show()
        val job = scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    val json = HttpStreams.open("https://iptv-org.github.io/api/$kind.json", 4L * 1024 * 1024).use { JSONArray(it.bufferedReader().readText()) }
                    (0 until json.length()).mapNotNull { i ->
                        val item = json.getJSONObject(i); val code = item.optString(if (kind == "categories") "id" else "code")
                        if (!code.matches(Regex("[a-zA-Z0-9_-]+"))) null else item.getString("name") to code
                    }.sortedBy { it.first.lowercase(java.util.Locale.ROOT) }
                }
                loading.dismiss()
                val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
                val search = EditText(activity).apply { hint = "Search"; isSingleLine = true }
                val list = ListView(activity)
                layout.addView(search); layout.addView(list, LinearLayout.LayoutParams(-1, dp(300)))
                var filtered = entries
                fun update(query: String) { filtered = entries.filter { it.first.contains(query, true) }; list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, filtered.map { it.first }) }
                update(""); search.doAfterTextChanged { update(it.toString()) }
                val dialog = AlertDialog.Builder(activity).setTitle(R.string.choose_public).setView(layout).setNegativeButton(R.string.cancel, null).show()
                list.setOnItemClickListener { _, _, position, _ ->
                    val (label, code) = filtered[position]; dialog.dismiss(); onSource(PlaylistSource.public(kind, code, label), false)
                }
                list.requestFocus()
            } catch (cancelled: CancellationException) { loading.dismiss(); throw cancelled }
            catch (_: Exception) { loading.dismiss(); onNotice("Could not load public choices. Try again, or choose all channels.") }
        }
        loading.setOnCancelListener { job.cancel() }
        loading.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { job.cancel(); loading.dismiss() }
    }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
