package com.couchzap.iptv.ui

import android.view.View
import com.couchzap.iptv.databinding.ViewImportBinding

class ImportFragmentlessScreen(
    private val binding: ViewImportBinding,
    private val onImportButtonClicked: () -> Unit,
    private val onPublicButtonClicked: () -> Unit = {},
    private val onLanguageButtonClicked: () -> Unit = {}
) {

    init {
        binding.btnImport.setOnClickListener {
            onImportButtonClicked()
        }
        binding.btnPublic.setOnClickListener { onPublicButtonClicked() }
        binding.btnImportLanguage.setOnClickListener { onLanguageButtonClicked() }
    }

    fun updateLayout(tv: Boolean) {
        val density = binding.root.resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val width = binding.root.resources.configuration.screenWidthDp
        val twoColumns = width >= 700
        binding.importColumns.orientation = if (twoColumns) android.widget.LinearLayout.HORIZONTAL else android.widget.LinearLayout.VERTICAL
        listOf(binding.importIntro, binding.importActions).forEach { view ->
            view.layoutParams = android.widget.LinearLayout.LayoutParams(if (twoColumns) 0 else -1, -2, if (twoColumns) 1f else 0f)
        }
        binding.importIntro.setPaddingRelative(0, 0, if (twoColumns) dp(36) else 0, if (twoColumns) 0 else dp(28))
        binding.importContent.layoutParams = binding.importContent.layoutParams.apply { this.width = minOf(dp(1000), dp(width - if (tv) 96 else 48)) }
        binding.tvImportTitle.textSize = if (twoColumns) 36f else 28f
        binding.tvImportHint.textSize = if (tv) 18f else 16f
        binding.tvImportRemote.visibility = if (tv) View.VISIBLE else View.GONE
        binding.tvImportTitle.gravity = android.view.Gravity.START
        binding.tvImportHint.gravity = android.view.Gravity.START
    }

    fun show() {
        binding.importContainer.visibility = View.VISIBLE
        binding.btnImport.requestFocus()
    }

    fun hide() {
        binding.importContainer.visibility = View.GONE
    }

    fun isVisible(): Boolean = binding.importContainer.visibility == View.VISIBLE

    fun showProgress(text: String) {
        binding.btnImport.isEnabled = false
        binding.btnPublic.isEnabled = false
        binding.btnImportLanguage.isEnabled = false
        binding.tvImportProgress.text = text
        binding.tvImportProgress.visibility = View.VISIBLE
    }

    fun hideProgress() {
        binding.btnImport.isEnabled = true
        binding.btnPublic.isEnabled = true
        binding.btnImportLanguage.isEnabled = true
        binding.tvImportProgress.visibility = View.GONE
    }
}
