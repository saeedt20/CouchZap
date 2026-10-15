package com.couchzap.iptv.ui.design

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import com.couchzap.iptv.R
import com.couchzap.iptv.util.Prefs

object CouchZapTokens {
    enum class Text(val tv: Float, val phone: Float, val tvLine: Int, val phoneLine: Int, val weight: Int) {
        DISPLAY(38f,30f,46,38,600), HEADING(28f,26f,36,34,600), PANE(22f,22f,30,30,600),
        CONTROL(20f,17f,28,24,500), CHANNEL(20f,17f,28,24,500), BODY(18f,16f,26,24,400),
        VALUE(17f,14f,24,21,400), FOOTER(16f,13f,22,19,500), MICRO(14f,12f,20,18,400), NUMBER(48f,40f,56,48,500)
    }
    private val fonts = mutableMapOf<String, Typeface>()
    // Explicit IDs also retain every bundled weight through release resource shrinking.
    private val resources=mapOf(
        "inter_400" to R.font.inter_400,"inter_500" to R.font.inter_500,"inter_600" to R.font.inter_600,
        "vazirmatn_400" to R.font.vazirmatn_400,"vazirmatn_500" to R.font.vazirmatn_500,"vazirmatn_600" to R.font.vazirmatn_600,
        "noto_devanagari_400" to R.font.noto_devanagari_400,"noto_devanagari_500" to R.font.noto_devanagari_500,"noto_devanagari_600" to R.font.noto_devanagari_600,
        "noto_thai_400" to R.font.noto_thai_400,"noto_thai_500" to R.font.noto_thai_500,"noto_thai_600" to R.font.noto_thai_600)
    fun text(view: TextView, role: Text, tv: Boolean = AdaptiveUi.isTv(view.context), language:String?=null) {
        view.textSize = if (tv) role.tv else role.phone
        val lang = language ?: Prefs(view.context).getLanguage() ?: "en"
        val family = when(lang) { "fa","ar" -> "vazirmatn"; "hi" -> "noto_devanagari"; "th" -> "noto_thai"; else -> "inter" }
        val key = "${family}_${role.weight}"
        view.typeface = fonts.getOrPut(key) {
            val id = resources.getValue(key)
            if (id != 0) runCatching { ResourcesCompat.getFont(view.context,id) }.getOrNull() ?: Typeface.DEFAULT
            else Typeface.create(if(role.weight >= 500) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        }
        view.includeFontPadding = lang in listOf("fa","ar","hi","th")
        view.letterSpacing = if(role == Text.DISPLAY && lang in listOf("en","es")) -.01f else 0f
        TextViewCompat.setLineHeight(view, ((if(tv) role.tvLine else role.phoneLine) * view.resources.displayMetrics.scaledDensity).toInt())
        view.setTextColor(view.context.getColor(if(role in listOf(Text.VALUE,Text.FOOTER,Text.MICRO)) R.color.cz_text_secondary else R.color.cz_text_primary))
    }
    fun dp(view: View, n: Float) = n * view.resources.displayMetrics.density
}
