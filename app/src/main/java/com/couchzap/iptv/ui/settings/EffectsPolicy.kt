package com.couchzap.iptv.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import com.couchzap.iptv.util.Anim

enum class EffectsMode(val key:String) { AUTO("auto"), FULL("full"), SAFE("safe"), OFF("off");
    companion object {fun from(key:String)=entries.firstOrNull{it.key==key}?:AUTO}
}
enum class EffectsTier { FULL, SAFE, OFF }
data class EffectsCapabilities(val api:Int,val hardwareAccelerated:Boolean,val lowRam:Boolean,val totalRamBytes:Long,
    val processorCount:Int,val powerSave:Boolean,val windowBlurEnabled:Boolean,val surfaceFailure:Boolean=false,
    val sessionDowngraded:Boolean=false,val systemAnimationsEnabled:Boolean=true)

/** Screening is conservative; frame monitoring and the user's Safe override remain authoritative. */
object EffectsPolicy {
    fun resolve(mode:EffectsMode,c:EffectsCapabilities):EffectsTier {
        if(mode==EffectsMode.OFF||!c.systemAnimationsEnabled)return EffectsTier.OFF
        if(mode==EffectsMode.SAFE)return EffectsTier.SAFE
        val hard=c.api<31||!c.hardwareAccelerated||c.lowRam||c.totalRamBytes<3L*1024*1024*1024||c.powerSave||
            !c.windowBlurEnabled||c.surfaceFailure||c.sessionDowngraded
        if(hard||(mode==EffectsMode.AUTO&&c.processorCount<4))return EffectsTier.SAFE
        return EffectsTier.FULL
    }
    fun read(view:View,sessionDowngraded:Boolean=false):EffectsCapabilities {
        val context=view.context
        val am=context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory=ActivityManager.MemoryInfo().also{am?.getMemoryInfo(it)}
        val pm=context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wm=context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return EffectsCapabilities(Build.VERSION.SDK_INT,view.isHardwareAccelerated,am?.isLowRamDevice?:true,memory.totalMem,
            Runtime.getRuntime().availableProcessors(),pm?.isPowerSaveMode?:true,
            Build.VERSION.SDK_INT>=31&&runCatching{wm.isCrossWindowBlurEnabled}.getOrDefault(false),
            sessionDowngraded=sessionDowngraded,systemAnimationsEnabled=Anim.systemEnabled(view))
    }
}
