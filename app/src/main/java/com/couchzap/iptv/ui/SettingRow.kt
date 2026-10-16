package com.couchzap.iptv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.couchzap.iptv.R
import com.couchzap.iptv.ui.design.*

open class SettingRow(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private var checkable=false
    private var checked=false
    private var arrow:ImageView?=null
    /** Whether this row navigates to a child page. Rows that perform an
     * immediate action (e.g. the channel-switch sound toggle) set this to false
     * and show no chevron. */
    var navigates: Boolean = true
        set(value) { field = value; updateArrow() }
    private fun updateArrow() {
        val d = resources.displayMetrics.density
        if (navigates) {
            if (arrow == null) {
                // 3.1: row chevron auto-mirrors with the app layout direction,
                // so FA/AR rows show the owner-requested left-pointing ‹ while
                // EN/ES/HI/TH keep the right-pointing ›. Layout/text stay RTL.
                arrow = ImageView(context).apply { setImageResource(R.drawable.ic_chevron_row); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
                addView(arrow, LayoutParams((24 * d).toInt(), (24 * d).toInt()).apply { marginStart = (8 * d).toInt() })
            }
            arrow?.visibility = VISIBLE
        } else {
            arrow?.visibility = GONE
        }
    }
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true; isFocusableInTouchMode = true }
    /**
     * 3.1 first-tap: an unfocused touch-focusable row would consume the first
     * tap by taking focus on DOWN, requiring a second tap to activate. To
     * preserve the D-pad focus contract (isFocusableInTouchMode=true) while
     * allowing a completed tap to activate once, suppress touch-focus
     * acquisition while DOWN/UP are dispatched. D-pad/key focus via
     * requestFocus() and requestFocusFromTouch() outside touch dispatch is
     * unaffected, as is pressed/click handling: DOWN still sets pressed, UP
     * still clicks. Cancellation, scrolling, and disabled state keep native
     * behavior because we do not consume or synthesize events.
     */
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        var suppressed = false
        val action = event.actionMasked
        if ((action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_UP) &&
            isFocusableInTouchMode && !isFocused) {
            // Don't let the tap take focus; the tap itself is the activation.
            isFocusableInTouchMode = false
            suppressed = true
        }
        try {
            return super.onTouchEvent(event)
        } finally {
            if (suppressed) isFocusableInTouchMode = true
        }
    }
    val labelView:TextView? get()=texts(this).firstOrNull{it.tag=="cz_label"}
    val valueView:TextView? get()=texts(this).firstOrNull{it.tag=="cz_value"||resources.getResourceEntryNameSafe(it.id).endsWith("_value")}
    private fun texts(v:View):List<TextView> = if(v is TextView)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else emptyList()
    private fun android.content.res.Resources.getResourceEntryNameSafe(id:Int)=if(id==NO_ID)"" else runCatching{getResourceEntryName(id)}.getOrDefault("")
    override fun onFinishInflate(){super.onFinishInflate();style()}
    fun style(tv:Boolean=AdaptiveUi.isTv(context),language:String?=null) {
        val d=resources.displayMetrics.density
        minimumHeight=((if(texts(this).any{it.tag=="cz_supporting"&&it.visibility!=GONE})if(tv)104 else 96 else if(tv)64 else 60)*d).toInt()
        setPadding((16*d).toInt(),(8*d).toInt(),(16*d).toInt(),(8*d).toInt())
        texts(this).forEach{CouchZapTokens.text(it,if(it===labelView)CouchZapTokens.Text.CONTROL else CouchZapTokens.Text.VALUE,tv,language);it.isFocusable=false;it.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
        valueView?.apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
        valueView?.let{(it.layoutParams as? LayoutParams)?.marginStart=(12*d).toInt()}
        updateArrow()
        FocusMotionBinder.bind(this,if(id==R.id.btn_import||id==R.id.btn_public)FocusMotionBinder.Role.WELCOME else FocusMotionBinder.Role.ROW)
    }
    fun setLabel(label:CharSequence){labelView?.text=label;refreshDescription()}
    fun setBoolean(value:Boolean){checkable=true;checked=value;ViewCompat.setStateDescription(this,context.getString(if(value)R.string.cz_on else R.string.off));refreshDescription()}
    fun refreshDescription(){contentDescription=texts(this).mapNotNull{it.text.takeIf{t->t.isNotEmpty()}}.joinToString(", ")}
    override fun setEnabled(enabled:Boolean){
        if(!enabled&&isFocused){val next=focusSearch(FOCUS_DOWN)?.takeIf{it!==this&&it.isShown&&it.isEnabled};if(next==null||!next.requestFocus())clearFocus()}
        super.setEnabled(enabled)
        texts(this).forEach{it.setTextColor(context.getColor(if(!enabled)R.color.cz_disabled_text else if(it===labelView)R.color.cz_text_primary else R.color.cz_text_secondary))}
        (getTag(R.id.cz_focus_binder) as? FocusMotionBinder)?.refresh()
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){valueView?.maxWidth=(MeasureSpec.getSize(widthMeasureSpec)*.35f).toInt();super.onMeasure(widthMeasureSpec,heightMeasureSpec)}
    override fun getAccessibilityClassName(): CharSequence = if(checkable)"android.widget.Switch" else "android.widget.Button"
    override fun onInitializeAccessibilityNodeInfo(info:AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.isCheckable=checkable;info.isChecked=checked;info.contentDescription=texts(this).mapNotNull{it.text.takeIf{t->t.isNotEmpty()}}.joinToString(", ")}
}
