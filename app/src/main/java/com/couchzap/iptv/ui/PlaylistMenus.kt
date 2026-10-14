package com.couchzap.iptv.ui

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.couchzap.iptv.R
import com.couchzap.iptv.data.HttpStreams
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.data.PlaylistSource
import com.couchzap.iptv.data.PublicCatalog
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.*
import org.json.JSONArray
import com.couchzap.iptv.support.DiagnosticLog

class PlaylistMenus(
    private val activity: AppCompatActivity,
    private val repository: PlaylistRepository,
    private val scope: CoroutineScope,
    private val onFile: () -> Unit,
    private val onSource: (PlaylistSource, Boolean) -> Unit,
    private val onNotice: (String) -> Unit,
    private val panel: SettingsPanel,
    private val onOpen: () -> Unit,
    private val loadCatalog: suspend (String, String) -> List<PublicCatalog.Entry> = { provider, kind ->
        withContext(Dispatchers.IO) { HttpStreams.open(PublicCatalog.url(provider, kind), 4L * 1024 * 1024).use {
            PublicCatalog.parse(provider, kind, JSONArray(it.bufferedReader().readText()))
        } }
    }
) {
    fun show() {
        onOpen()
        panel.page("sources", R.string.playlist_sources) { body ->
            panel.text(body, repository.source()?.label ?: activity.getString(R.string.playlist_not_added))
            if (repository.updatedAt() > 0) panel.text(body, activity.getString(R.string.source_updated, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(repository.updatedAt()))))
            if (repository.unsupportedCount() > 0) panel.text(body, activity.getString(R.string.unsupported_skipped, repository.unsupportedCount()))
            panel.button(body, activity.getString(R.string.btn_import_playlist)) { confirmFile() }
            panel.button(body, activity.getString(R.string.import_link)) { link() }
            panel.button(body, activity.getString(R.string.public_channels)) { publicChoices() }
            panel.button(body, activity.getString(R.string.refresh_playlist)) {
                repository.source()?.let { onSource(it, true) } ?: onNotice(activity.getString(R.string.no_refresh_source))
            }
        }
    }
    fun ownChoices() {
        onOpen()
        panel.page("own-playlist", R.string.btn_import_playlist) { body ->
            panel.text(body, activity.getString(R.string.own_playlist_help))
            panel.button(body, activity.getString(R.string.import_file)) { confirmFile() }
            panel.button(body, activity.getString(R.string.import_link)) { link() }
        }
    }
    private fun confirmFile() {
        if (!repository.isPlaylistReady()) { onFile(); return }
        panel.page("replace-file", R.string.replace_playlist) { body ->
            panel.text(body, activity.getString(R.string.confirm_replace))
            panel.button(body, activity.getString(R.string.replace_playlist)) { onFile() }
            panel.button(body, activity.getString(R.string.cancel)) { panel.back() }
        }
    }
    private fun accept(source: PlaylistSource) {
        if (!repository.isPlaylistReady()) { onSource(source, false); return }
        panel.page("replace", R.string.replace_playlist) { body ->
            panel.text(body, activity.getString(R.string.confirm_replace))
            panel.button(body, activity.getString(R.string.replace_playlist)) { onSource(source, false) }
            panel.button(body, activity.getString(R.string.cancel)) { panel.back() }
        }
    }
    fun link(title: Int = R.string.import_link, onAccept: (String) -> Unit = { accept(PlaylistSource("url", it, activity.getString(R.string.playlist_link))) }) {
        onOpen()
        panel.page("link-$title", title) { body ->
            val input = EditText(activity).apply {
                hint = activity.getString(R.string.enter_link); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                isSingleLine = true; textDirection = View.TEXT_DIRECTION_LTR; layoutDirection = View.LAYOUT_DIRECTION_LTR
                setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.LTGRAY)
            }
            body.addView(input, LinearLayout.LayoutParams(-1, -2))
            panel.button(body, activity.getString(R.string.import_action)) {
                val url = input.text.toString().trim()
                if (!HttpStreams.validUrl(url)) input.error = activity.getString(R.string.invalid_link)
                else onAccept(url)
            }
        }
    }
    fun publicChoices() {
        onOpen()
        panel.page("public-providers", R.string.choose_provider) { body ->
            panel.button(body, "iptv-org") { providerChoices("iptv-org") }
            panel.text(body, activity.getString(R.string.iptv_org_description))
            panel.button(body, "Free-TV") { providerChoices("free-tv") }
            panel.text(body, activity.getString(R.string.free_tv_description))
        }
    }
    private fun providerChoices(provider: String) {
        val kinds = if (provider == "iptv-org") listOf("all", "countries", "languages", "categories") else listOf("all", "countries", "categories")
        val strings = mapOf("all" to R.string.all_public, "countries" to R.string.by_country, "languages" to R.string.by_language, "categories" to R.string.by_category)
        panel.page("provider-$provider", R.string.choose_public) { body ->
            panel.text(body, if (provider == "free-tv") "Free-TV" else "iptv-org", technical = true)
            kinds.forEach { kind -> panel.button(body, activity.getString(strings.getValue(kind))) {
                if (kind == "all") accept(if (provider == "free-tv") PlaylistSource.freeTv(label = activity.getString(R.string.all_public)) else PlaylistSource.public(label = activity.getString(R.string.all_public)))
                else catalog(provider, kind)
            } }
            if (provider == "free-tv") panel.text(body, activity.getString(R.string.public_support_note))
        }
    }
    private fun catalog(provider: String, kind: String) {
        var job: Job? = null
        panel.page("catalog-$provider-$kind", R.string.choose_public, leave = { job?.cancel() }) { body ->
            val status = panel.text(body, activity.getString(R.string.loading_choices))
            job = scope.launch {
                try {
                    val entries = loadCatalog(provider, kind)
                    currentCoroutineContext().ensureActive()
                    status.visibility = View.GONE
                    val search = EditText(activity).apply { hint = activity.getString(R.string.search); isSingleLine = true; setTextColor(android.graphics.Color.WHITE) }
                    val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    body.addView(search); body.addView(rows)
                    fun update(query: String) {
                        rows.removeAllViews()
                        val filtered = entries.filter { it.label.contains(query, true) }
                        if (filtered.isEmpty()) panel.text(rows, activity.getString(R.string.catalog_empty))
                        filtered.forEach { (label, code) -> panel.button(rows, label) { accept(if (provider == "free-tv") PlaylistSource.freeTv(code, label) else PlaylistSource.public(kind, code, label)) } }
                    }
                    update(""); search.doAfterTextChanged { update(it.toString()) }; panel.focusFirst()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    DiagnosticLog.record("public_catalog_failed", error)
                    status.setText(R.string.catalog_failed)
                    panel.button(body, activity.getString(R.string.retry)) { panel.back(); catalog(provider, kind) }
                    panel.focusFirst()
                }
            }
        }
    }
}
