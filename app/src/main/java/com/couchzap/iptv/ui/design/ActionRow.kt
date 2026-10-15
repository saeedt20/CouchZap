package com.couchzap.iptv.ui.design

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.couchzap.iptv.R
import com.couchzap.iptv.ui.SettingRow

class ActionRow(context:Context):SettingRow(context) {
    private val tv=AdaptiveUi.isTv(context)
    private val language=com.couchzap.iptv.util.Prefs(context).getLanguage()?:"en"
    val title=TextView(context).apply{tag="cz_label";textAlignment=TEXT_ALIGNMENT_VIEW_START}
    val supporting=TextView(context).apply{tag="cz_supporting";visibility=GONE;textAlignment=TEXT_ALIGNMENT_VIEW_START}
    init {
        val column=LinearLayout(context).apply{orientation=VERTICAL;addView(title);addView(supporting)}
        addView(column,LayoutParams(0,-2,1f));style(tv,language)
    }
    fun bind(key:String,label:CharSequence,metadata:CharSequence?=null){
        setTag(R.id.cz_action_key,key);title.text=label;supporting.text=metadata
        val visibility=if(metadata.isNullOrEmpty())GONE else VISIBLE
        if(supporting.visibility!=visibility){supporting.visibility=visibility;style(tv,language)}
        contentDescription=if(metadata.isNullOrEmpty())label else "$label, $metadata"
    }
}
