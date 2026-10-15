package com.couchzap.iptv.ui.design

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import com.couchzap.iptv.util.Prefs
import kotlin.math.ceil

/** Window bounds and input mode are independent: a wide phone remains a touch device. */
data class AdaptiveUi(val tv: Boolean, val width: Int, val height: Int, val density: Float) {
    fun dp(value: Int) = (value * density + .5f).toInt()
    val safeX = if (tv) ceil(maxOf(dp(24).toFloat(), width * .05f)).toInt() else 0
    val safeY = if (tv) ceil(maxOf(dp(24).toFloat(), height * .05f)).toInt() else 0
    val safeWidth get() = (width - 2 * safeX).coerceAtLeast(0)
    val safeHeight get() = (height - 2 * safeY).coerceAtLeast(0)
    val short get() = height / density < 480
    val expanded get() = width / density >= 840 && !short
    val twoPane get() = if (tv) safeWidth >= dp(600) && safeHeight >= dp(360) else expanded
    val channelWidth get() = if (tv) minOf(dp(400), safeWidth) else
        if (width >= dp(660) && width > height) minOf(dp(420), width - dp(280)) else width
    companion object {
        fun isTv(context: Context): Boolean {
            val mode = Prefs(context).getInterfaceMode()
            val detected = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            return mode == "TV" || (mode == "Auto" && detected)
        }
        fun from(view: View, tv: Boolean = isTv(view.context)): AdaptiveUi {
            val d = view.resources.displayMetrics.density
            val c = view.resources.configuration
            return AdaptiveUi(tv, (view.width - view.paddingLeft - view.paddingRight).takeIf { it > 0 } ?: (c.screenWidthDp * d).toInt(),
                (view.height - view.paddingTop - view.paddingBottom).takeIf { it > 0 } ?: (c.screenHeightDp * d).toInt(), d)
        }
    }
}
