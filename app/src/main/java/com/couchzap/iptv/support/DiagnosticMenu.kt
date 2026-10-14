package com.couchzap.iptv.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import com.couchzap.iptv.ui.SettingsPanel
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.couchzap.iptv.R
import java.io.File

class DiagnosticMenu(private val activity: AppCompatActivity, private val panel: SettingsPanel, private val notice: (String) -> Unit) {
    private var pendingReport = ""
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingReport) }
                ?: error("No document output")
        }.onSuccess { notice(activity.getString(R.string.log_saved)) }
            .onFailure { DiagnosticLog.record("log_save_failed", it); notice(activity.getString(R.string.log_save_failed)) }
    }
    fun show(sourceKind: String?, channelCount: Int) {
        pendingReport = DiagnosticLog.report(activity, sourceKind, channelCount)
        panel.page("diagnostics", R.string.diagnostic_log) { body ->
            panel.button(body, activity.getString(R.string.copy_log)) { copy(true) }
            panel.button(body, activity.getString(R.string.save_log)) {
                runCatching { save.launch("CouchZap-diagnostics.txt") }.onFailure { copy() }
            }
            panel.button(body, activity.getString(R.string.share_log)) { share() }
            panel.text(body, pendingReport, technical = true).apply { textSize = 13f; setTextIsSelectable(true) }
        }
    }
    private fun share() {
        runCatching {
            val dir = File(activity.cacheDir, "reports").apply { mkdirs() }
            dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(4)?.forEach { it.delete() }
            val file = File(dir, "CouchZap-diagnostics-${java.util.UUID.randomUUID()}.txt").apply { writeText(pendingReport) }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "CouchZap diagnostic report")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("Diagnostics", uri)
            if (intent.resolveActivity(activity.packageManager) == null) { copy(); return }
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_log)))
        }.onFailure { copy() }
    }
    private fun copy(explicit: Boolean = false) {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("CouchZap diagnostics", pendingReport))
        notice(activity.getString(if (explicit) R.string.log_copy_done else R.string.log_copied))
    }
}
