package com.couchzap.iptv.ui.settings

import android.view.*
import android.widget.*
import com.couchzap.iptv.R
import com.couchzap.iptv.databinding.ActivityMainBinding
import com.couchzap.iptv.ui.SettingRow
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.ui.design.*
import com.couchzap.iptv.util.Anim

/** Categories regroup the existing root actions; routes and jobs still belong to SettingsPanel. */
class SettingsCategoryController(private val binding:ActivityMainBinding,private val panel:SettingsPanel) {
    private data class Memory(var id:Int=View.NO_ID,var scroll:Int=0)
    private val rail:LinearLayout=binding.layoutSettings.findViewById(R.id.cz_settings_rail)
    private val groups=listOf(binding.settingsPlaybackGroup,binding.settingsPlaylistGroup,binding.settingsAppGroup)
    private val buttons=listOf(R.id.cz_category_playback,R.id.cz_category_playlist,R.id.cz_category_app).map{rail.findViewById<Button>(it)}
    private val titles=listOf(R.string.playback_section,R.string.playlist_section,R.string.app_section)
    private val memories=List(3){Memory()}
    var selected=0;private set
    var expanded=false;private set
    private var selectionToken=0
    private var fadeGeneration=0
    private var fadingGroup:LinearLayout?=null
    private var fadingFocus=ViewGroup.FOCUS_AFTER_DESCENDANTS
    private var fadingAccess=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private val fadingClicks=mutableMapOf<View,Boolean>()
    val detailRoot:ViewGroup get()=groups[selected]
    init {
        panel.categories=this
        buttons.forEachIndexed{index,button->
            button.isFocusableInTouchMode=true
            button.setTag(R.id.cz_action_key,"category:$index")
            button.setOnFocusChangeListener{_,focused->if(focused){val token=++selectionToken;button.postOnAnimation{if(token==selectionToken&&button.isFocused)select(index)}}}
            button.setOnClickListener{select(index);enterDetail()}
            FocusMotionBinder.bind(button,FocusMotionBinder.Role.RAIL)
        }
    }
    fun configure(ui:AdaptiveUi){
        settle()
        expanded=ui.twoPane
        rail.visibility=if(expanded)View.VISIBLE else View.GONE
        binding.settingsRailScroll.visibility=rail.visibility
        val extra=if(rail.resources.configuration.fontScale>1.3f)minOf(64,((rail.resources.configuration.fontScale-1.3f)*80).toInt())else 0
        binding.settingsRailScroll.layoutParams=binding.settingsRailScroll.layoutParams.apply{width=ui.dp((if(ui.tv)180 else 196)+extra)}
        binding.settingsPaneDivider.visibility=rail.visibility
        val exit=binding.btnSettingsExit
        val target=if(expanded)rail else groups.last()
        if(exit.parent!==target){(exit.parent as? ViewGroup)?.removeView(exit);target.addView(exit,LinearLayout.LayoutParams(-1,-2))}
        groups.forEachIndexed{index,group->
            group.isTransitionGroup=true
            group.visibility=if(!expanded||index==selected)View.VISIBLE else View.GONE
            group.setBackgroundResource(R.drawable.bg_integrated);group.setPadding(ui.dp(8),ui.dp(8),ui.dp(8),ui.dp(8));group.clipChildren=false;group.clipToPadding=false
            group.dividerDrawable=android.graphics.drawable.GradientDrawable().apply{setColor(group.context.getColor(R.color.cz_divider));setSize(1,ui.dp(1))}
            group.showDividers=LinearLayout.SHOW_DIVIDER_MIDDLE;group.dividerPadding=ui.dp(16)
        }
        listOf(binding.localizedLabel6,binding.localizedLabel9,binding.localizedLabel10).forEach{it.visibility=if(expanded)View.GONE else View.VISIBLE;CouchZapTokens.text(it,CouchZapTokens.Text.FOOTER,ui.tv)}
        fun restyle(v:View){if(v is SettingRow)v.style(ui.tv);if(v is ViewGroup)for(i in 0 until v.childCount)restyle(v.getChildAt(i))}
        restyle(binding.settingsRootScroll);binding.btnSettingsExit.style(ui.tv)
        buttons.forEachIndexed{i,button->button.setText(titles[i]);CouchZapTokens.text(button,CouchZapTokens.Text.CONTROL,ui.tv);button.isSelected=i==selected}
        updateHeader()
    }
    private fun select(index:Int){
        if(selected==index)return
        settle()
        if(panel.depth==0)rememberDetail(panel.focused)
        else panel.reset()
        if(expanded&&Anim.enabled(rail)){
            val token=fadeGeneration;val outgoing=groups[selected]
            fadingGroup=outgoing;fadingFocus=outgoing.descendantFocusability;fadingAccess=outgoing.importantForAccessibility
            outgoing.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS;outgoing.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            for(i in 0 until outgoing.childCount){val child=outgoing.getChildAt(i);if(child is SettingRow){fadingClicks[child]=child.isClickable;child.isClickable=false}}
            android.transition.TransitionManager.beginDelayedTransition(binding.settingsRootGroups,
                android.transition.Fade().apply{
                    duration=100;addTarget(outgoing);addTarget(groups[index])
                    addListener(object:android.transition.Transition.TransitionListener{
                        override fun onTransitionStart(transition:android.transition.Transition){}
                        override fun onTransitionEnd(transition:android.transition.Transition){if(token==fadeGeneration)restoreOutgoing()}
                        override fun onTransitionCancel(transition:android.transition.Transition){if(token==fadeGeneration)restoreOutgoing()}
                        override fun onTransitionPause(transition:android.transition.Transition){}
                        override fun onTransitionResume(transition:android.transition.Transition){}
                    })
                })
        }
        selected=index
        groups.forEachIndexed{i,group->group.visibility=if(!expanded||i==index)View.VISIBLE else View.GONE}
        buttons.forEachIndexed{i,b->b.isSelected=i==index}
        binding.settingsRootScroll.scrollTo(0,memories[index].scroll)
        updateHeader()
    }
    private fun restoreOutgoing(){fadingGroup?.let{it.descendantFocusability=fadingFocus;it.importantForAccessibility=fadingAccess};fadingClicks.forEach{(view,clickable)->view.isClickable=clickable};fadingClicks.clear();fadingGroup=null}
    fun settle(){fadeGeneration++;android.transition.TransitionManager.endTransitions(binding.settingsRootGroups);restoreOutgoing()}
    fun activateForAction(view:View){
        val index=groups.indexOfFirst{group->var p:View?=view;var found=false;while(p!=null){if(p===group){found=true;break};p=p.parent as? View};found}
        if(index>=0&&index!=selected){selected=index;groups.forEachIndexed{i,g->g.visibility=if(!expanded||i==index)View.VISIBLE else View.GONE};buttons.forEachIndexed{i,b->b.isSelected=i==index};updateHeader()}
    }
    fun activateRoute(key:String){
        val index=when {
            key=="language"||key=="mode"||key=="effects"||key=="update"||key=="diagnostics"||key=="about"->2
            key=="sources"||key=="own-playlist"||key=="public-providers"||key.startsWith("provider-")||key.startsWith("catalog-")||key.startsWith("link-")||key=="guide"->1
            else->return
        }
        selected=index
        groups.forEachIndexed{i,g->g.visibility=if(!expanded||i==index)View.VISIBLE else View.GONE}
        buttons.forEachIndexed{i,b->b.isSelected=i==index}
        updateHeader()
    }
    fun rememberDetail(view:View?){if(view!=null&&groups[selected].findViewById<View>(view.id)!=null){memories[selected].id=view.id;memories[selected].scroll=binding.settingsRootScroll.scrollY}}
    fun focusRail(){buttons[selected].requestFocusFromTouch()}
    fun enterDetail(){
        if(panel.depth>0){panel.focusFirst();return}
        select(selected)
        val memory=memories[selected]
        val target=groups[selected].findViewById<View>(memory.id)?.takeIf{it.isShown&&it.isEnabled&&it.isFocusable}
        if(target==null||!target.requestFocusFromTouch())panel.focusFirst()
        binding.settingsRootScroll.scrollTo(0,memory.scroll)
    }
    fun backAtRoot():Boolean {
        if(!expanded||rail.hasFocus())return false
        rememberDetail(panel.focused);focusRail();return true
    }
    fun updateHeader(){
        val title=binding.layoutSettings.findViewById<TextView>(R.id.cz_settings_category_title)
        title.visibility=if(expanded&&panel.depth==0)View.VISIBLE else View.GONE
        title.setText(titles[selected]);CouchZapTokens.text(title,CouchZapTokens.Text.PANE)
        binding.layoutSettings.findViewById<TextView>(R.id.cz_settings_footer).apply{setText(R.string.cz_settings_hint);visibility=if(AdaptiveUi.isTv(context))View.VISIBLE else View.GONE;CouchZapTokens.text(this,CouchZapTokens.Text.FOOTER)}
    }
    fun dispatch(event:KeyEvent):Boolean {
        if(!expanded)return false
        val focus=panel.focused?:return false
        if(focus is EditText&&event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT))return false
        val rtl=binding.layoutSettings.layoutDirection==View.LAYOUT_DIRECTION_RTL
        val towardDetail=if(rtl)KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val towardRail=if(rtl)KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        if(rail.hasFocus()){
            val railTargets=buttons+binding.btnSettingsExit
            val index=railTargets.indexOfFirst{it===focus}
            if(event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)){
                if(event.action==KeyEvent.ACTION_DOWN){val next=(index+if(event.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1).coerceIn(0,railTargets.lastIndex);railTargets[next].requestFocusFromTouch()};return true
            }
            if(event.keyCode==towardDetail){if(event.action==KeyEvent.ACTION_DOWN&&focus!==binding.btnSettingsExit){select(index.coerceIn(0,2));enterDetail()};return true}
            if(event.keyCode==towardRail)return true
        }else if(event.keyCode==towardRail&&(panel.depth==0||focus===binding.settingsBack)){
            if(event.action==KeyEvent.ACTION_DOWN){rememberDetail(focus);focusRail()};return true
        }
        return false
    }
}
