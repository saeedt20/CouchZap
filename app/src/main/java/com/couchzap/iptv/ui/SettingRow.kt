package com.couchzap.iptv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout

class SettingRow(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true }
    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
}
