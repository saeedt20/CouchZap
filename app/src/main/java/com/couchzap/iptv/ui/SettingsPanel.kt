package com.couchzap.iptv.ui

import android.graphics.Rect
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.couchzap.iptv.R
import com.couchzap.iptv.databinding.ActivityMainBinding
import com.couchzap.iptv.ui.design.*
import com.couchzap.iptv.ui.settings.SettingsCategoryController
import com.couchzap.iptv.util.Anim

/** Retained logical routes with one parent per view and explicit window-local focus lookup. */
class SettingsPanel(private val activity: AppCompatActivity, private val binding: ActivityMainBinding) {
    private data class Page(val key:String,val title:Int,val view:ViewGroup,var focusKey:String?=null,var focusId:Int=View.NO_ID,
        var scrollY:Int=0,val leave:()->Unit={},var left:Boolean=false,var reactivate:(()->Unit)?=null,
        var suspendRenderer:(()->Unit)?=null,var suspended:Boolean=false,var direction:((KeyEvent)->Boolean)?=null,var initialFocus:View?=null,
        var restoreFocus:((String?)->Boolean)?=null)
    private val stack=mutableListOf<Page>()
    private val root=binding.settingsRootScroll
    private val pageMotion=DetailPageMotion(binding.settingsPages)
    private var rootFocusKey:String?=null
    private var rootFocusId=View.NO_ID
    private var rootScrollY=0
    private var actionOrigin:View?=null
    private var generation=0
    private var accepting=true
    private var presentationKey:String?=null
    private var presentationScroll=0
    private var presentationSelection:Pair<Int,Int>?=null
    var categories:SettingsCategoryController?=null
    var hostOwnsInput:()->Boolean={binding.layoutSettings.visibility==View.VISIBLE}
    val hostRoot:ViewGroup get()=binding.layoutSettings
    val focused:View? get()=hostRoot.findFocus()
    val depth:Int get()=stack.size
    val currentKey:String? get()=stack.lastOrNull()?.key
    val currentRoot:View get()=stack.lastOrNull()?.view?:root
    var onRootBack:()->Unit={}
    var onClose:()->Unit={}
    init {
        root.isSmoothScrollingEnabled=false
        binding.settingsBack.setOnClickListener{back()}
        binding.btnSettingsClose.setOnClickListener{onClose()}
        FocusMotionBinder.bind(binding.settingsBack,FocusMotionBinder.Role.ICON)
        FocusMotionBinder.bind(binding.btnSettingsClose,FocusMotionBinder.Role.ICON)
    }
    fun <T:View> find(id:Int):T?=hostRoot.findViewById<T>(id)?:root.findViewById(id)
    fun actionKey(view:View?):String?=view?.getTag(R.id.cz_action_key) as? String
        ?:view?.id?.takeIf{it!=View.NO_ID}?.let{"id:$it"}
    private fun findKey(view:View,key:String?):View? {
        if(key==null)return null
        if(actionKey(view)==key)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)findKey(view.getChildAt(i),key)?.let{return it}
        return null
    }
    fun onClick(view:View,action:()->Unit) {
        if(view.getTag(R.id.cz_action_key)==null)view.setTag(R.id.cz_action_key,"id:${view.id}")
        view.setOnClickListener{if(!accepting)return@setOnClickListener;actionOrigin=view;if(depth==0)categories?.activateForAction(view);try{action()}finally{actionOrigin=null}}
    }
    fun isCurrent(body:View):Boolean=accepting&&hostOwnsInput()&&stack.lastOrNull()?.view?.let{contains(it,body)}==true
    private fun contains(parent:View,child:View):Boolean {
        var node:View?=child
        while(node!=null){if(node===parent)return true;node=node.parent as? View}
        return false
    }
    fun onReactivate(action:()->Unit){stack.lastOrNull()?.reactivate=action}
    fun onSuspendPresentation(action:()->Unit){stack.lastOrNull()?.suspendRenderer=action}
    fun onDirection(action:(KeyEvent)->Boolean){stack.lastOrNull()?.direction=action}
    fun onRestoreFocus(action:(String?)->Boolean){stack.lastOrNull()?.restoreFocus=action}
    fun initialFocus(view:View){stack.lastOrNull()?.initialFocus=view}
    private fun leave(page:Page){if(!page.left){page.left=true;page.leave()}}
    fun rememberPresentationFocus(){presentationKey=actionKey(focused);presentationScroll=currentRoot.scrollY;presentationSelection=(focused as? EditText)?.let{it.selectionStart to it.selectionEnd};categories?.rememberDetail(focused)}
    fun pausePresentation(){rememberPresentationFocus();categories?.settle();pageMotion.settle();accepting=false;generation++;stack.forEach{if(!it.suspended){it.suspended=true;it.suspendRenderer?.invoke()}}}
    fun beginClose(){pausePresentation();stack.forEach(::leave)}
    fun resume(){accepting=true;stack.forEach{page->if((page.left||page.suspended)&&page.reactivate!=null){page.left=false;page.reactivate?.invoke()};page.suspended=false}}
    fun restorePresentationFocus(){
        if(!accepting||!hostOwnsInput())return
        val target=findKey(hostRoot,presentationKey)
        if(target?.isShown==true&&target.isEnabled&&target.requestFocusFromTouch())currentRoot.scrollTo(0,presentationScroll)
        else if(stack.lastOrNull()?.restoreFocus?.invoke(presentationKey)!=true)focusEntry()
        if(target is EditText)presentationSelection?.let{(start,end)->if(start>=0&&end>=0)target.setSelection(start.coerceAtMost(target.length()),end.coerceAtMost(target.length()))}
    }
    fun page(key:String,title:Int,leave:()->Unit={},fixed:Boolean=false,build:(LinearLayout)->Unit) {
        if(stack.lastOrNull()?.key==key)return
        categories?.settle()
        val current=stack.lastOrNull()
        if(current==null)categories?.activateRoute(key)
        val origin=actionOrigin?:focused
        if(current==null){rootFocusKey=actionKey(origin);rootFocusId=origin?.id?:View.NO_ID;rootScrollY=root.scrollY;categories?.rememberDetail(origin)}
        else{current.focusKey=actionKey(origin);current.focusId=origin?.id?:View.NO_ID;current.scrollY=current.view.scrollY}
        generation++
        val body=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),dp(8),dp(8),dp(8));clipChildren=false;clipToPadding=false}
        val scroll:ViewGroup=if(fixed)FrameLayout(activity).apply{addView(body,FrameLayout.LayoutParams(-1,-1))}
            else ScrollView(activity).apply{isFillViewport=true;isVerticalScrollBarEnabled=false;isSmoothScrollingEnabled=false;addView(body)}
        stack.add(Page(key,title,scroll,leave=leave))
        build(body);pageMotion.show(scroll,false);header();focusFirst()
        ViewCompat.setAccessibilityPaneTitle(scroll,activity.getString(title))
    }
    private fun clearDisplayed(){pageMotion.clear()}
    fun back():Boolean {
        if(stack.isEmpty())return categories?.backAtRoot()?:false
        generation++
        val removed=stack.removeAt(stack.lastIndex);leave(removed);Anim.finishAll(removed.view)
        val parent=stack.lastOrNull();val page=parent?.view?:root
        pageMotion.show(page,true);header()
        parent?.reactivate?.invoke()
        val key=parent?.focusKey?:rootFocusKey
        val target:View?=findKey(page,key)?:page.findViewById<View>(parent?.focusId?:rootFocusId)
        val position=parent?.scrollY?:rootScrollY
        if(hostOwnsInput()&&accepting){if(target==null||!target.requestFocusFromTouch())if(parent?.restoreFocus?.invoke(key)!=true)focusFirst()}
        page.scrollTo(0,position)
        val token=generation
        page.addOnLayoutChangeListener(object:View.OnLayoutChangeListener{
            override fun onLayoutChange(v:View,l:Int,t:Int,r:Int,b:Int,ol:Int,ot:Int,or:Int,ob:Int){
                v.removeOnLayoutChangeListener(this)
                if(token==generation&&hostOwnsInput()&&accepting){if(!page.hasFocus())target?.requestFocusFromTouch();page.scrollTo(0,position)}
            }
        })
        if(parent==null)onRootBack()
        return true
    }
    /** Silent reset: never recursively issues root Back/close callbacks. */
    fun reset(){
        categories?.settle()
        generation++;stack.asReversed().forEach{leave(it);Anim.finishAll(it.view)};stack.clear();clearDisplayed()
        (root.parent as? ViewGroup)?.removeView(root);binding.settingsPages.addView(root,ViewGroup.LayoutParams(-1,-1));accepting=true;header()
    }
    fun header(){
        binding.settingsTitle.setText(stack.lastOrNull()?.title?:R.string.settings)
        ViewCompat.setAccessibilityPaneTitle(binding.layoutSettings,if(stack.isEmpty())activity.getString(R.string.settings)else null)
        CouchZapTokens.text(binding.settingsTitle,CouchZapTokens.Text.HEADING)
        binding.settingsTitle.maxLines=2;binding.settingsTitle.ellipsize=android.text.TextUtils.TruncateAt.END
        ViewCompat.setAccessibilityHeading(binding.settingsTitle,true)
        binding.settingsBack.visibility=if(stack.isEmpty())View.GONE else View.VISIBLE
        // 3.1: scoped header back-icon override (e.g. About in FA/AR). Reset on leave.
        binding.settingsBack.setImageResource(headerBackIconOverride?:R.drawable.ic_back)
        binding.settingsBack.contentDescription=activity.getString(R.string.back)
        categories?.updateHeader()
    }
    /** 3.1: optional per-page back-icon override; cleared when the page is left. */
    var headerBackIconOverride:Int?=null
    fun focusFirst(){
        if(!accepting||!hostOwnsInput())return
        val view=stack.lastOrNull()?.view?:categories?.takeIf{it.expanded}?.detailRoot?:root
        val choices=ArrayList<View>();view.addFocusables(choices,View.FOCUS_FORWARD,View.FOCUSABLES_ALL)
        val target=stack.lastOrNull()?.initialFocus?.takeIf{it.isShown&&it.isEnabled}
            ?:choices.firstOrNull{it.visibility==View.VISIBLE&&it.isEnabled&&it!==view}
        if(target==null||!target.requestFocusFromTouch())if(stack.isNotEmpty())binding.settingsBack.requestFocusFromTouch() else binding.btnSettingsClose.requestFocusFromTouch()
    }
    fun focusEntry(){if(depth==0&&categories?.expanded==true)categories?.focusRail() else focusFirst()}
    fun dispatchDirection(event:android.view.KeyEvent):Boolean {
        val direction=when(event.keyCode){android.view.KeyEvent.KEYCODE_DPAD_UP->View.FOCUS_UP;android.view.KeyEvent.KEYCODE_DPAD_DOWN->View.FOCUS_DOWN;android.view.KeyEvent.KEYCODE_DPAD_LEFT->View.FOCUS_LEFT;android.view.KeyEvent.KEYCODE_DPAD_RIGHT->View.FOCUS_RIGHT;else->return false}
        if(stack.lastOrNull()?.direction?.invoke(event)==true)return true
        if(categories?.dispatch(event)==true)return true
        val focus=focused
        if(focus is EditText&&direction in listOf(View.FOCUS_LEFT,View.FOCUS_RIGHT))return false
        if(event.action!=android.view.KeyEvent.ACTION_DOWN)return true
        if(focus==null){focusEntry();return true}
        val scope=if(focus===binding.settingsBack||focus===binding.btnSettingsClose)focus.parent as ViewGroup else if(depth==0&&categories?.expanded==true)categories!!.detailRoot else currentRoot as ViewGroup
        val id=when(direction){View.FOCUS_UP->focus.nextFocusUpId;View.FOCUS_DOWN->focus.nextFocusDownId;View.FOCUS_LEFT->focus.nextFocusLeftId;else->focus.nextFocusRightId}
        val explicit=hostRoot.findViewById<View>(id)?.takeIf{it.isShown&&it.isEnabled&&it.isFocusable&&(contains(scope,it)||it===binding.settingsBack||it===binding.btnSettingsClose)}
        val next=explicit?:FocusFinder.getInstance().findNextFocus(scope,focus.takeIf{contains(scope,it)},direction)
        if(next!=null)next.requestFocusFromTouch()
        else if(direction==View.FOCUS_UP&&depth>0)binding.settingsBack.requestFocusFromTouch()
        return true
    }
    fun text(body:LinearLayout,value:CharSequence,technical:Boolean=false):TextView=TextView(activity).apply{
        text=value;CouchZapTokens.text(this,CouchZapTokens.Text.BODY);setPadding(dp(8),dp(12),dp(8),dp(12))
        if(technical){textDirection=View.TEXT_DIRECTION_LTR;layoutDirection=View.LAYOUT_DIRECTION_LTR;typeface=android.graphics.Typeface.MONOSPACE}
        body.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    fun button(body:LinearLayout,label:CharSequence,selected:Boolean=false,key:String?=null,metadata:CharSequence?=null,action:()->Unit):ActionRow=ActionRow(activity).apply{
        id=View.generateViewId();bind(key?:"${currentKey}/action/${body.childCount}",label,metadata);isSelected=selected
        body.addView(this,LinearLayout.LayoutParams(-1,-2));onClick(this,action)
        val semantics=onFocusChangeListener
        onFocusChangeListener=View.OnFocusChangeListener{v,focused->semantics?.onFocusChange(v,focused);if(focused&&accepting&&hostOwnsInput())v.post{if(v.isAttachedToWindow&&v.hasFocus())v.requestRectangleOnScreen(Rect(0,0,v.width,v.height),true)}}
    }
    fun choices(key:String,title:Int,labels:List<String>,selected:Int=-1,secondary:List<String>?=null,keys:List<String>?=null,description:String?=null,action:(Int)->Unit){
        page(key,title){body->
            if(description!=null)text(body,description)
            body.isFocusable=false
            ViewCompat.setAccessibilityDelegate(body,object:androidx.core.view.AccessibilityDelegateCompat(){
                override fun onInitializeAccessibilityNodeInfo(host:View,info:androidx.core.view.accessibility.AccessibilityNodeInfoCompat){super.onInitializeAccessibilityNodeInfo(host,info);info.setCollectionInfo(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(labels.size,1,false,1))}
            })
            val rows=labels.mapIndexed{index,label->ChoiceRow(activity).apply{
                id=View.generateViewId();bind("$key/${keys?.getOrNull(index)?:index}",label,secondary?.getOrNull(index),index==selected)
                if(key=="language"){
                    CouchZapTokens.text(this.title,CouchZapTokens.Text.CONTROL,language=com.couchzap.iptv.util.AppLanguage.supported[index].code)
                    CouchZapTokens.text(subtitle,CouchZapTokens.Text.VALUE,language="en");subtitle.textDirection=View.TEXT_DIRECTION_LTR
                }
                onClick(this){action(index)};body.addView(this,LinearLayout.LayoutParams(-1,-2))
            }}
            rows.forEachIndexed{i,row->row.nextFocusUpId=rows.getOrNull(i-1)?.id?:binding.settingsBack.id;row.nextFocusDownId=rows.getOrNull(i+1)?.id?:row.id}
            // 3.1.1 item 3: on TV, initially focus the configured value instead
            // of the first option. Safe fallback: invalid index or non-TV keeps
            // the existing first-focus behavior.
            if (com.couchzap.iptv.ui.design.AdaptiveUi.isTv(activity)) {
                rows.getOrNull(selected)?.takeIf { it.isEnabled && it.isFocusable }?.let { initialFocus(it) }
            }
        }
    }
    fun dp(value:Int)=(value*activity.resources.displayMetrics.density+.5f).toInt()
}
