package com.couchzap.iptv.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.couchzap.iptv.R
import com.couchzap.iptv.ui.SettingsPanel
import kotlinx.coroutines.*
import java.io.File

/** Activity-owned work and state, page-owned observer; no coroutine retains a detached View. */
class UpdateMenu(private val activity: AppCompatActivity, private val panel: SettingsPanel,
    private val backend: Backend = NetworkBackend(activity.applicationContext)) {
    interface Backend {
        suspend fun latest(): ReleaseUpdater.Release
        suspend fun download(release: ReleaseUpdater.Release, progress: (Int) -> Unit): File
    }
    private class NetworkBackend(private val context: android.content.Context) : Backend {
        override suspend fun latest() = withContext(Dispatchers.IO) { ReleaseUpdater.latest() }
        override suspend fun download(release: ReleaseUpdater.Release, progress: (Int) -> Unit) =
            withContext(Dispatchers.IO) { ReleaseUpdater.download(context, release, progress) }
    }
    enum class State { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, PERMISSION, FAILED, CANCELLED }
    var state = State.IDLE; private set
    private var release: ReleaseUpdater.Release? = null
    private var installed = ""
    private var percent = 0
    private var job: Job? = null
    private var pendingInstall: File? = null
    private var waitingForPermission = false
    private var render: (() -> Unit)? = null
    fun show() {
        panel.page("update", R.string.check_updates, leave = { render = null }) { body ->
            render = {
                val focusedIndex = (0 until body.childCount).indexOfFirst { body.getChildAt(it).hasFocus() }
                body.removeAllViews()
                when (state) {
                    State.IDLE -> panel.button(body, activity.getString(R.string.check_updates)) { check() }
                    State.CHECKING -> { panel.text(body, activity.getString(R.string.checking_updates)); cancelButton(body) }
                    State.CURRENT -> { panel.text(body, activity.getString(R.string.up_to_date, installed)); retryButton(body) }
                    State.AVAILABLE -> {
                        release?.let { r ->
                            panel.text(body, activity.getString(R.string.update_available, r.version))
                            panel.text(body, activity.getString(R.string.update_download_prompt, r.size / 1024 / 1024))
                            panel.button(body, activity.getString(R.string.download_update)) { download(r) }
                            panel.text(body, r.notes)
                        }
                    }
                    State.DOWNLOADING -> { panel.text(body, activity.getString(R.string.update_progress, percent)); cancelButton(body) }
                    State.READY -> {
                        panel.text(body, activity.getString(R.string.update_ready_body))
                        panel.button(body, activity.getString(R.string.install_update)) { install() }
                    }
                    State.PERMISSION -> {
                        panel.text(body, activity.getString(R.string.allow_updates_body))
                        panel.button(body, activity.getString(R.string.open_settings)) {
                            runCatching {
                                waitingForPermission = true
                                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                            }.onFailure { waitingForPermission = false; failure("update_permission_failed", it) }
                        }
                    }
                    State.FAILED, State.CANCELLED -> {
                        panel.text(body, activity.getString(if (state == State.FAILED) R.string.update_failed else R.string.update_cancelled)); retryButton(body)
                    }
                }
                if (focusedIndex >= 0) body.getChildAt(focusedIndex.coerceAtMost(body.childCount - 1))?.takeIf { it.isFocusable }?.requestFocusFromTouch()
                // Only recover focus if this page is currently visible, never steal from another overlay.
                if (body.isShown && !body.hasFocus() && !activity.currentFocus.let { it === activity.findViewById<android.view.View>(R.id.settings_back) || it === activity.findViewById<android.view.View>(R.id.btn_settings_close) }) panel.focusFirst()
            }
            render?.invoke()
        }
        if (state == State.IDLE) check()
    }
    private fun retryButton(body: android.widget.LinearLayout) = panel.button(body, activity.getString(R.string.check_updates)) { check() }
    private fun cancelButton(body: android.widget.LinearLayout) = panel.button(body, activity.getString(R.string.cancel)) { cancel() }
    fun cancel() { job?.cancel(); job = null; state = State.CANCELLED; render?.invoke() }
    private fun check() {
        if (job?.isActive == true) return
        state = State.CHECKING; render?.invoke()
        job = activity.lifecycleScope.launch {
            try {
                val latest = backend.latest()
                @Suppress("DEPRECATION")
                val current = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
                installed = current; release = latest
                state = if (ReleaseUpdater.isNewer(latest.version, current)) State.AVAILABLE else State.CURRENT
                DiagnosticLog.record("update_check_success"); render?.invoke()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure("update_check_failed", error) }
        }
    }
    private fun download(release: ReleaseUpdater.Release) {
        if (job?.isActive == true) return
        percent = 0; state = State.DOWNLOADING; render?.invoke()
        job = activity.lifecycleScope.launch {
            try {
                val coroutine = currentCoroutineContext()
                pendingInstall = backend.download(release) { progress ->
                    coroutine.ensureActive()
                    activity.runOnUiThread { if (coroutine.isActive && state == State.DOWNLOADING && percent != progress) { percent = progress; render?.invoke() } }
                }
                DiagnosticLog.record("update_download_verified")
                state = State.READY; render?.invoke()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure("update_download_failed", error) }
        }
    }
    private fun install() {
        val file = pendingInstall?.takeIf { it.exists() } ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            state = State.PERMISSION; render?.invoke(); return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { failure("update_installer_unavailable", it) }
    }
    fun onResume() {
        if (waitingForPermission && Build.VERSION.SDK_INT >= 26) {
            waitingForPermission = false
            if (activity.packageManager.canRequestPackageInstalls()) { state = State.READY; install() }
        }
    }
    private fun failure(event: String, error: Throwable) {
        DiagnosticLog.record(event, error); state = State.FAILED; render?.invoke()
    }
}
