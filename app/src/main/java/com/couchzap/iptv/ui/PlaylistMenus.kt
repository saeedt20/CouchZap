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
import com.couchzap.iptv.ui.design.CatalogOptionsView
import com.couchzap.iptv.ui.design.CouchZapTokens
import com.couchzap.iptv.ui.design.FocusMotionBinder

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
            panel.button(body, activity.getString(R.string.import_file), metadata=activity.getString(R.string.playlist_formats)) { confirmFile() }
            panel.button(body, activity.getString(R.string.import_link)) { link() }
        }
    }
    private fun confirmFile() {
        if (!repository.isPlaylistReady()) { onFile(); return }
        panel.page("replace-file", R.string.replace_playlist) { body ->
            panel.text(body, activity.getString(R.string.confirm_replace))
            panel.button(body, activity.getString(R.string.replace_playlist)) { onFile() }
            panel.initialFocus(panel.button(body, activity.getString(R.string.cancel)) { panel.back() })
        }
    }
    private fun accept(source: PlaylistSource) {
        if (!repository.isPlaylistReady()) { onSource(source, false); return }
        panel.page("replace", R.string.replace_playlist) { body ->
            panel.text(body, activity.getString(R.string.confirm_replace))
            panel.button(body, activity.getString(R.string.replace_playlist)) { onSource(source, false) }
            panel.initialFocus(panel.button(body, activity.getString(R.string.cancel)) { panel.back() })
        }
    }
    fun link(title: Int = R.string.import_link, onAccept: (String) -> Unit = { accept(PlaylistSource("url", it, activity.getString(R.string.playlist_link))) }) {
        onOpen()
        panel.page("link-$title", title) { body ->
            val label=panel.text(body, activity.getString(title))
            val input = EditText(activity).apply {
                id=View.generateViewId();setTag(R.id.cz_action_key,"link-$title/input")
                hint = activity.getString(R.string.enter_link); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                isSingleLine = true; textDirection = View.TEXT_DIRECTION_LTR; layoutDirection = View.LAYOUT_DIRECTION_LTR
                minimumHeight=panel.dp(56);imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setTextColor(activity.getColor(R.color.cz_text_primary));setHintTextColor(activity.getColor(R.color.cz_text_secondary))
                setPadding(panel.dp(16),panel.dp(8),panel.dp(16),panel.dp(8))
                CouchZapTokens.text(this,CouchZapTokens.Text.CONTROL);FocusMotionBinder.bind(this)
            }
            body.addView(input, LinearLayout.LayoutParams(-1, -2))
            label.labelFor=input.id
            val error=panel.text(body, "").apply { visibility=View.GONE;setTextColor(activity.getColor(R.color.cz_error));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
            androidx.core.view.ViewCompat.setAccessibilityDelegate(input,object:androidx.core.view.AccessibilityDelegateCompat(){
                override fun onInitializeAccessibilityNodeInfo(host:View,info:androidx.core.view.accessibility.AccessibilityNodeInfoCompat){super.onInitializeAccessibilityNodeInfo(host,info);info.isContentInvalid=error.visibility==View.VISIBLE;if(info.isContentInvalid)info.error=error.text}
            })
            input.doAfterTextChanged{if(error.visibility==View.VISIBLE)error.visibility=View.GONE}
            var submitted=false
            fun submit() {
                if(submitted)return
                val url = input.text.toString().trim()
                if (!HttpStreams.validUrl(url)) { error.setText(R.string.invalid_link);error.visibility=View.VISIBLE;input.requestFocusFromTouch() }
                else {submitted=true;error.visibility=View.GONE;onAccept(url)}
            }
            panel.button(body, activity.getString(R.string.import_action),key="link-$title/submit") { submit() }
            input.setOnEditorActionListener { _, action, event ->
                if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event?.keyCode==android.view.KeyEvent.KEYCODE_ENTER&&event.action==android.view.KeyEvent.ACTION_UP)) {submit();true} else false
            }
            panel.onReactivate { submitted=false }
        }
    }
    fun publicChoices() {
        onOpen()
        panel.page("public-providers", R.string.choose_provider) { body ->
            panel.button(body, "iptv-org", metadata=activity.getString(R.string.iptv_org_description)) { providerChoices("iptv-org") }
            panel.button(body, "Free-TV", metadata=activity.getString(R.string.free_tv_description)) { providerChoices("free-tv") }
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
        panel.page("catalog-$provider-$kind", R.string.choose_public, leave = { job?.cancel() }, fixed=true) { body ->
            panel.text(body,if(provider=="free-tv")"Free-TV" else "iptv-org",technical=true)
            val status = panel.text(body, activity.getString(R.string.loading_choices))
            var loaded=false
            var retry:View?=null
            fun load() {
                if(loaded||job?.isActive==true)return
                retry?.visibility=View.GONE;status.setText(R.string.loading_choices);status.visibility=View.VISIBLE
                job = scope.launch {
                try {
                    val entries = loadCatalog(provider, kind)
                    currentCoroutineContext().ensureActive()
                    loaded=true
                    status.visibility = View.GONE
                    val options=CatalogOptionsView(activity,"catalog-$provider-$kind",entries){(label,code)->
                        accept(if(provider=="free-tv")PlaylistSource.freeTv(code,label) else PlaylistSource.public(kind,code,label))
                    }
                    body.addView(options,LinearLayout.LayoutParams(-1,0,1f))
                    panel.onDirection{event->options.dispatch(event,panel.focused)}
                    panel.onRestoreFocus{key->options.restore(key)}
                    panel.initialFocus(options.search)
                    if(panel.isCurrent(body))panel.focusFirst()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    DiagnosticLog.record("public_catalog_failed", error)
                    status.setText(R.string.catalog_failed)
                    if(retry==null)retry=panel.button(body,activity.getString(R.string.retry),key="catalog-$provider-$kind/retry"){job=null;load()}
                    retry?.visibility=View.VISIBLE
                    if(panel.isCurrent(body))panel.focusFirst()
                }
                }
            }
            panel.onReactivate{load()}
            load()
        }
    }
}
