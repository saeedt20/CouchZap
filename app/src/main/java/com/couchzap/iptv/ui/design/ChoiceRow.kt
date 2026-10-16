package com.couchzap.iptv.ui.design

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.couchzap.iptv.R

/** A single accessible radio target; native text and a separate persistent selection mark. */
class ChoiceRow(context: Context) : LinearLayout(context) {
    val title=TextView(context)
    val subtitle=TextView(context)
    private val mark=RadioMark(context)
    init {
        orientation=HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; isFocusable=true; isClickable=true; isFocusableInTouchMode=true
        val d=resources.displayMetrics.density
        minimumHeight=(if(AdaptiveUi.isTv(context))64*d else 60*d).toInt()
        setPadding((16*d).toInt(),(8*d).toInt(),(16*d).toInt(),(8*d).toInt())
        val text=LinearLayout(context).apply { orientation=VERTICAL; addView(title);addView(subtitle) }
        addView(text,LayoutParams(0,-2,1f));addView(mark,LayoutParams((24*d).toInt(),(24*d).toInt()).apply { marginStart=(12*d).toInt() })
        title.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; subtitle.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        title.textAlignment=TEXT_ALIGNMENT_VIEW_START;subtitle.textAlignment=TEXT_ALIGNMENT_VIEW_START
        CouchZapTokens.text(title,CouchZapTokens.Text.CONTROL);CouchZapTokens.text(subtitle,CouchZapTokens.Text.VALUE)
        FocusMotionBinder.bind(this)
    }
    fun bind(key: String,label: CharSequence,secondary: CharSequence?=null,checked: Boolean=false) {
        setTag(R.id.cz_action_key,key);title.text=label;subtitle.text=secondary;subtitle.visibility=if(secondary.isNullOrEmpty())GONE else VISIBLE
        isSelected=checked; mark.checked=checked
        contentDescription=listOfNotNull(label,secondary?.takeIf { it.isNotEmpty() }).joinToString(", ")
        ViewCompat.setStateDescription(this,if(checked)context.getString(R.string.cz_selected) else null)
    }
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.RadioButton";info.isCheckable=true;info.isChecked=isSelected
    }
    private class RadioMark(context: Context): View(context) {
        var checked=false;set(value){field=value;invalidate()}
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE;strokeWidth=2*resources.displayMetrics.density;color=context.getColor(R.color.cz_sage) }
        private val check=Path()
        init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){check.reset();check.moveTo(w*.28f,h*.50f);check.lineTo(w*.44f,h*.66f);check.lineTo(w*.73f,h*.35f)}
        override fun onDraw(canvas:Canvas){canvas.drawCircle(width/2f,height/2f,width*.44f,paint);if(checked)canvas.drawPath(check,paint)}
    }
}
