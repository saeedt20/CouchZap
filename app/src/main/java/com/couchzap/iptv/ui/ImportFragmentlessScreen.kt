package com.couchzap.iptv.ui

import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.couchzap.iptv.databinding.ViewImportBinding
import com.couchzap.iptv.ui.design.*
import com.couchzap.iptv.util.AppLanguage
import com.couchzap.iptv.util.Prefs

class ImportFragmentlessScreen(
    private val binding: ViewImportBinding,
    private val onImportButtonClicked: () -> Unit,
    private val onPublicButtonClicked: () -> Unit = {},
    private val onLanguageButtonClicked: () -> Unit = {}
) {

    init {
        binding.btnImport.nextFocusUpId=binding.btnImportLanguage.id
        binding.btnImport.nextFocusDownId=binding.btnPublic.id
        binding.btnPublic.nextFocusUpId=binding.btnImport.id
        binding.btnPublic.nextFocusDownId=binding.btnPublic.id
        binding.btnImportLanguage.nextFocusDownId=binding.btnImport.id
        ViewCompat.setAccessibilityHeading(binding.tvImportTitle,true)
        binding.btnImport.setOnClickListener {
            onImportButtonClicked()
        }
        binding.btnPublic.setOnClickListener { onPublicButtonClicked() }
        binding.btnImportLanguage.setOnClickListener { onLanguageButtonClicked() }
    }

    fun updateLayout(tv: Boolean) {
        val ui=AdaptiveUi.from(binding.root,tv)
        fun dp(n:Int)=ui.dp(n)
        val twoColumns = if(tv)ui.twoPane else ui.width>=dp(700)&&ui.height>=dp(480)
        binding.importColumns.orientation = if (twoColumns) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        binding.importColumns.gravity=if(twoColumns)android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP
        (binding.importColumns.layoutParams as LinearLayout.LayoutParams).apply{weight=if(twoColumns)1f else 0f}
        listOf(binding.importIntro, binding.importActions).forEachIndexed { index,view ->
            view.layoutParams = LinearLayout.LayoutParams(if (twoColumns) 0 else -1, -2, if (twoColumns) if(index==0).44f else .56f else 0f).apply {
                if(index==1&&twoColumns)marginStart=dp(40)
                if(index==1&&!twoColumns)topMargin=dp(24)
            }
        }
        binding.importIntro.setPadding(0,0,0,0)
        binding.importContent.setPadding(if(tv)ui.safeX else dp(20),if(tv)ui.safeY else dp(16),if(tv)ui.safeX else dp(20),if(tv)ui.safeY else dp(20))
        (binding.importHeader.layoutParams as LinearLayout.LayoutParams).bottomMargin=dp(if(tv)40 else 28)
        CouchZapTokens.text(binding.localizedLabel2,CouchZapTokens.Text.PANE,tv);binding.localizedLabel2.textSize=if(tv)24f else 22f
        CouchZapTokens.text(binding.importEyebrow,CouchZapTokens.Text.FOOTER,tv);binding.importEyebrow.setTextColor(binding.root.context.getColor(com.couchzap.iptv.R.color.cz_sage))
        CouchZapTokens.text(binding.tvImportTitle,CouchZapTokens.Text.DISPLAY,tv)
        CouchZapTokens.text(binding.tvImportHint,CouchZapTokens.Text.BODY,tv)
        CouchZapTokens.text(binding.tvImportRemote,CouchZapTokens.Text.FOOTER,tv)
        CouchZapTokens.text(binding.tvImportProgress,CouchZapTokens.Text.BODY,tv)
        val language=Prefs(binding.root.context).getLanguage()?:AppLanguage.DEFAULT
        binding.btnImportLanguage.text=AppLanguage.supported.first{it.code==language}.name
        binding.btnImportLanguage.contentDescription="${binding.root.context.getString(com.couchzap.iptv.R.string.language)}, ${binding.btnImportLanguage.text}"
        CouchZapTokens.text(binding.btnImportLanguage,CouchZapTokens.Text.CONTROL,tv)
        FocusMotionBinder.bind(binding.btnImportLanguage)
        listOf(binding.btnImport,binding.btnPublic).forEach{it.style(tv);it.minimumHeight=dp(88)}
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
