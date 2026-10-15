package com.couchzap.iptv.ui.settings

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window

/** Samples only requested Full transitions; UI metrics do not claim decoder/network health. */
class TransitionFrameMonitor(private val unhealthy:()->Unit) {
    private var samples=0;private var late=0;private var extreme=false;private var active=false;private var badTransitions=0
    private var period=16_666_667L
    private val windows=mutableListOf<Window>()
    private val listener=Window.OnFrameMetricsAvailableListener {_,metrics,_->
        if(active){val duration=metrics.getMetric(FrameMetrics.TOTAL_DURATION);samples++;if(duration>period*1.5)late++;if(duration>100_000_000L)extreme=true}
    }
    fun attach(window:Window){if(window in windows)return;windows.add(window);window.addOnFrameMetricsAvailableListener(listener,Handler(Looper.getMainLooper()))}
    fun begin(refresh:Float){samples=0;late=0;extreme=false;period=(1_000_000_000L/refresh.coerceAtLeast(24f)).toLong();active=true}
    fun end(){if(!active)return;active=false;if(samples>=20&&(extreme||late.toFloat()/samples>.1f)){badTransitions++;if(badTransitions>=2)unhealthy()}}
    fun pause(){active=false}
    fun retry(){active=false;badTransitions=0}
    fun destroy(){active=false;windows.forEach{it.removeOnFrameMetricsAvailableListener(listener)};windows.clear()}
}
