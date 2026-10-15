package com.couchzap.iptv

import android.view.View
import android.widget.TextView
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.design.ChoiceRow

/** Access actual retained views/windows; no duplicate tree or platform lookup override. */
val View.isUiAction get()=this is SettingRow||this is ChoiceRow||this is android.widget.Button
fun View.uiLabel():TextView=when(this){is ChoiceRow->title;is SettingRow->requireNotNull(labelView);is TextView->this;else->error("Not a labeled action: ${javaClass.name}")}
fun MainActivity.layoutSettingsForTest(width:Int,height:Int){
    val root=settingsWindowRoot()
    if(uiView<View>(R.id.layout_settings).visibility!=View.VISIBLE)return
    root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
    root.layout(0,0,width,height)
}
