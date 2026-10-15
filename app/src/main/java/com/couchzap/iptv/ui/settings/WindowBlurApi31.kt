package com.couchzap.iptv.ui.settings

import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.util.function.Consumer

@RequiresApi(31)
class WindowBlurApi31(private val window:Window,private val manager:WindowManager,private val changed:(Boolean)->Unit) {
    private val listener=Consumer<Boolean>{changed(it)}
    private var attached=false
    private var applied=-1
    fun attach(){if(attached)return;attached=true;manager.addCrossWindowBlurEnabledListener(listener)}
    fun radiusPx(value:Int){
        val radius=value.coerceAtLeast(0);if(radius==applied)return;applied=radius
        if(radius>0)window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes=window.attributes.apply{setBlurBehindRadius(radius)}
        if(radius==0)window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
    }
    fun detach(){radiusPx(0);if(attached)manager.removeCrossWindowBlurEnabledListener(listener);attached=false}
}
