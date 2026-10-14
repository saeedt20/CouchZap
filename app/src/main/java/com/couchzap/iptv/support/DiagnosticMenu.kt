package com.couchzap.iptv.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.couchzap.iptv.R
import java.io.File

class DiagnosticMenu(private val activity: AppCompatActivity, private val notice: (String) -> Unit) {
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
        val text = TextView(activity).apply {
            text = pendingReport; textSize = 13f; setTextIsSelectable(true)
            val padding = (16 * resources.displayMetrics.density).toInt(); setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(activity).apply { addView(text) }
        AlertDialog.Builder(activity).setTitle(R.string.diagnostic_log).setView(scroll)
            .setPositiveButton(R.string.share_log) { _, _ -> share() }
            .setNeutralButton(R.string.save_log) { _, _ ->
                runCatching { save.launch("CouchZap-diagnostics.txt") }.onFailure { copy() }
            }.setNegativeButton(R.string.cancel, null).show().also {
                scroll.layoutParams = scroll.layoutParams.apply { height = (300 * activity.resources.displayMetrics.density).toInt() }
            }
    }
    private fun share() {
        runCatching {
            val dir = File(activity.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, "CouchZap-diagnostics.txt").apply { writeText(pendingReport) }
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "CouchZap diagnostic report")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("Diagnostics", uri)
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.share_log)))
        }.onFailure { copy() }
    }
    private fun copy() {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("CouchZap diagnostics", pendingReport))
        notice(activity.getString(R.string.log_copied))
    }
}
