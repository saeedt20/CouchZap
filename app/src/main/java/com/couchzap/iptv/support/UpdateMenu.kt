package com.couchzap.iptv.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.couchzap.iptv.R
import kotlinx.coroutines.*
import java.io.File

class UpdateMenu(private val activity: AppCompatActivity) {
    private var busy = false
    private var pendingInstall: File? = null
    private var waitingForPermission = false
    fun show() {
        pendingInstall?.takeIf { it.exists() }?.let { offerInstall(); return }
        if (busy) return
        busy = true
        val loading = AlertDialog.Builder(activity).setTitle(R.string.check_updates).setMessage(R.string.checking_updates)
            .setNegativeButton(R.string.cancel, null).show()
        val job = activity.lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { ReleaseUpdater.latest() }
                @Suppress("DEPRECATION") val installed = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
                loading.dismiss()
                DiagnosticLog.record("update_check_success")
                if (!ReleaseUpdater.isNewer(release.version, installed)) {
                    AlertDialog.Builder(activity).setTitle(R.string.check_updates).setMessage(activity.getString(R.string.up_to_date, installed))
                        .setPositiveButton(android.R.string.ok, null).show()
                } else {
                    AlertDialog.Builder(activity).setTitle(activity.getString(R.string.update_available, release.version))
                        .setMessage(activity.getString(R.string.update_download_prompt, release.size / 1024 / 1024) + "\n\n" + release.notes)
                        .setPositiveButton(R.string.download_update) { _, _ -> download(release) }
                        .setNegativeButton(R.string.cancel, null).show()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { DiagnosticLog.record("update_check_failed", error); failure() }
            finally { loading.dismiss(); busy = false }
        }
        loading.setOnCancelListener { job.cancel() }
        loading.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { job.cancel(); loading.dismiss() }
    }
    private fun download(release: ReleaseUpdater.Release) {
        if (busy) return
        busy = true
        val progress = AlertDialog.Builder(activity).setTitle(R.string.download_update).setMessage(activity.getString(R.string.update_progress, 0))
            .setNegativeButton(R.string.cancel, null).show()
        val job = activity.lifecycleScope.launch {
            try {
                pendingInstall = withContext(Dispatchers.IO) {
                    val coroutine = currentCoroutineContext()
                    ReleaseUpdater.download(activity.applicationContext, release) { percent ->
                        coroutine.ensureActive()
                        activity.runOnUiThread { if (!activity.isFinishing && progress.isShowing) progress.setMessage(activity.getString(R.string.update_progress, percent)) }
                    }
                }
                DiagnosticLog.record("update_download_verified")
                progress.dismiss(); offerInstall()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { DiagnosticLog.record("update_download_failed", error); failure() }
            finally { progress.dismiss(); busy = false }
        }
        progress.setOnCancelListener { job.cancel() }
        progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { job.cancel(); progress.dismiss() }
    }
    private fun offerInstall() {
        AlertDialog.Builder(activity).setTitle(R.string.update_ready).setMessage(R.string.update_ready_body)
            .setPositiveButton(R.string.install_update) { _, _ -> install() }.setNegativeButton(R.string.later, null).show()
    }
    private fun install() {
        val file = pendingInstall?.takeIf { it.exists() } ?: return
        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle(R.string.allow_updates).setMessage(R.string.allow_updates_body)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    runCatching {
                        waitingForPermission = true
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    }.onFailure { waitingForPermission = false; DiagnosticLog.record("update_permission_failed", it); failure() }
                }.setNegativeButton(R.string.cancel, null).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { DiagnosticLog.record("update_installer_unavailable", it); failure() }
    }
    fun onResume() {
        if (waitingForPermission && Build.VERSION.SDK_INT >= 26) {
            waitingForPermission = false
            if (activity.packageManager.canRequestPackageInstalls()) install()
        }
    }
    private fun failure() {
        if (!activity.isFinishing) AlertDialog.Builder(activity).setTitle(R.string.check_updates).setMessage(R.string.update_failed)
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
