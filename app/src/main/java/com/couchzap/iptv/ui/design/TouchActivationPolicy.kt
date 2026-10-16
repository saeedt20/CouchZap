package com.couchzap.iptv.ui.design

import android.view.View
import android.widget.EditText

/**
 * Item 7 — first-tap activation policy for touch.
 *
 * A touch-focusable View that is not yet focused swallows the first tap:
 * ACTION_DOWN takes focus (View#focusTaken), so ACTION_UP then skips
 * performClick(). Ordinary action controls therefore stay keyboard/D-pad
 * focusable (isFocusable=true) but are NOT touch-mode focusable, so the
 * first completed touch gesture fires the click instead of only focusing.
 *
 * EditText fields keep touch focus for the IME. D-pad/keyboard focus is
 * unaffected: key input leaves touch mode and isFocusable=true still grants
 * focus. Programmatic focus restoration continues to use
 * requestFocusFromTouch(), which exits touch mode before requesting focus.
 */
object TouchActivationPolicy {
    fun apply(view: View) {
        if (view is EditText) return
        view.isFocusable = true
        view.isFocusableInTouchMode = false
    }
}
