package com.couchzap.iptv.ui.settings

import android.app.Dialog
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi

/** Registered only in the Dialog window; Activity Back never handles this same gesture. */
@RequiresApi(33)
class SettingsBackApi33(dialog:Dialog,back:()->Unit) {
    private val dispatcher=dialog.onBackInvokedDispatcher
    private val callback=OnBackInvokedCallback{back()}
    init{dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,callback)}
    fun destroy(){dispatcher.unregisterOnBackInvokedCallback(callback)}
}
