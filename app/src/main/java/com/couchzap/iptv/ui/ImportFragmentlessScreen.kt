package com.couchzap.iptv.ui

import android.view.View
import com.couchzap.iptv.databinding.ViewImportBinding

class ImportFragmentlessScreen(
    private val binding: ViewImportBinding,
    private val onImportButtonClicked: () -> Unit
) {

    init {
        binding.btnImport.setOnClickListener {
            onImportButtonClicked()
        }
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
        binding.tvImportProgress.text = text
        binding.tvImportProgress.visibility = View.VISIBLE
    }

    fun hideProgress() {
        binding.btnImport.isEnabled = true
        binding.tvImportProgress.visibility = View.GONE
    }
}
