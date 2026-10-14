package com.couchzap.iptv.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.couchzap.iptv.R
import com.couchzap.iptv.util.Anim
import com.couchzap.iptv.support.DiagnosticLog
import com.couchzap.iptv.databinding.ActivityMainBinding

/** One focus owner and a retained view stack: returning preserves parent scroll and focus. */
class SettingsPanel(private val activity: AppCompatActivity, private val binding: ActivityMainBinding) {
    private data class Page(val key: String, val title: Int, val view: View, var focus: View? = null, var scrollY: Int = 0, val leave: () -> Unit = {})
    private val stack = mutableListOf<Page>()
    private val root = binding.settingsRootScroll
    private var rootFocus: View? = null
    private var rootScrollY = 0
    private var actionOrigin: View? = null
    private var generation = 0
    fun onClick(view: View, action: () -> Unit) {
        view.setOnClickListener { actionOrigin = view; try { action() } finally { actionOrigin = null } }
    }
    val depth: Int get() = stack.size
    val currentKey: String? get() = stack.lastOrNull()?.key
    var onRootBack: () -> Unit = {}
    var onClose: () -> Unit = {}
    init {
        root.isSmoothScrollingEnabled = false
        binding.settingsBack.setOnClickListener { back() }
        binding.btnSettingsClose.setOnClickListener { onClose() }
    }
    fun page(key: String, title: Int, leave: () -> Unit = {}, build: (LinearLayout) -> Unit) {
        if (stack.lastOrNull()?.key == key) return
        val current = stack.lastOrNull()
        if (current == null) { rootFocus = actionOrigin ?: activity.currentFocus; rootScrollY = root.scrollY }
        else { current.focus = actionOrigin ?: activity.currentFocus; current.scrollY = current.view.scrollY }
        generation++
        binding.settingsPages.getChildAt(0)?.let { Anim.finishAll(it); binding.settingsPages.removeView(it) }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { isFillViewport = true; isScrollbarFadingEnabled = false; isSmoothScrollingEnabled = false; addView(body) }
        stack.add(Page(key, title, scroll, leave = leave))
        binding.settingsPages.addView(scroll, ViewGroup.LayoutParams(-1, -1))
        build(body)
        header()
        Anim.showOverlay(scroll, fresh = true)
        focusFirst()
    }
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        generation++
        stack.removeAt(stack.lastIndex).also { Anim.finishAll(it.view) }.leave()
        binding.settingsPages.removeAllViews()
        val parent = stack.lastOrNull()
        Anim.finish(parent?.view ?: root)
        binding.settingsPages.addView(parent?.view ?: root, ViewGroup.LayoutParams(-1, -1))
        header()
        val target = parent?.focus ?: rootFocus
        val pageView = parent?.view ?: root
        val position = parent?.scrollY ?: rootScrollY
        if (target == null || !target.requestFocusFromTouch()) focusFirst()
        pageView.scrollTo(0, position)
        val token = generation
        pageView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                v.removeOnLayoutChangeListener(this)
                if (token == generation && binding.layoutSettings.visibility == View.VISIBLE) {
                    if (!pageView.hasFocus()) target?.requestFocusFromTouch()
                    pageView.scrollTo(0, position)
                }
            }
        })
        val rootHighlight = android.os.Build.VERSION.SDK_INT >= 26 && binding.root.defaultFocusHighlightEnabled
        val pageHighlight = android.os.Build.VERSION.SDK_INT >= 26 && pageView.defaultFocusHighlightEnabled
        DiagnosticLog.record("settings_back:depth=${stack.size};focus=${activity.currentFocus?.id ?: -1};touch=${pageView.isInTouchMode};root_highlight=$rootHighlight;page_highlight=$pageHighlight")
        if (parent == null) onRootBack()
        return true
    }
    fun reset() { while (back()) {} }
    fun header() {
        binding.settingsTitle.setText(stack.lastOrNull()?.title ?: R.string.settings)
        binding.settingsTitle.textSize = if (stack.isEmpty()) 24f else 20f
        binding.settingsBack.visibility = if (stack.isEmpty()) View.GONE else View.VISIBLE
        binding.settingsBack.textDirection = View.TEXT_DIRECTION_LTR
        binding.settingsBack.text = if (activity.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "›" else "‹"
        binding.settingsBack.setContentDescription(activity.getString(R.string.back))
    }
    fun focusFirst() {
        val view = stack.lastOrNull()?.view ?: root
        val choices = ArrayList<View>(); view.addFocusables(choices, View.FOCUS_FORWARD, View.FOCUSABLES_ALL)
        val target = choices.firstOrNull { it.visibility == View.VISIBLE && it.isEnabled && it !== view }
        if (target == null || !target.requestFocusFromTouch()) {
            if (stack.isNotEmpty()) binding.settingsBack.requestFocusFromTouch() else binding.btnSettingsClose.requestFocusFromTouch()
        }
    }
    fun text(body: LinearLayout, value: CharSequence, technical: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = 16f; setTextColor(Color.WHITE); setPadding(dp(8), dp(12), dp(8), dp(12))
        if (technical) { textDirection = View.TEXT_DIRECTION_LTR; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    fun button(body: LinearLayout, label: CharSequence, selected: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        text = if (selected) "✓  $label" else label; isAllCaps = false; textSize = 16f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL; setTextColor(activity.getColorStateList(R.color.selector_channel_text))
        setBackgroundResource(R.drawable.bg_row); setPadding(dp(14), dp(8), dp(14), dp(8)); minHeight = dp(56)
        onClick(this, action); body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        setOnFocusChangeListener { v, focused -> if (focused) v.post { v.requestRectangleOnScreen(android.graphics.Rect(0, 0, v.width, v.height), true) } }
    }
    fun choices(key: String, title: Int, labels: List<String>, selected: Int = -1, action: (Int) -> Unit) {
        page(key, title) { body -> labels.forEachIndexed { i, label -> button(body, label, i == selected) { action(i) } } }
    }
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
