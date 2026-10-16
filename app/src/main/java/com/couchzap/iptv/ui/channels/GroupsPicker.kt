package com.couchzap.iptv.ui.channels

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.R
import com.couchzap.iptv.ui.design.*

/** An owned, scoped native modal. Group IDs remain source identities, never translated labels. */
class GroupsPicker(private val activity: androidx.appcompat.app.AppCompatActivity,private val canRestore:()->Boolean={true},private val onMenu:()->Unit) {
    data class Group(val id:String,val label:String,val count:Int)
    private var dialog:Dialog?=null
    private var cleanup:(()->Unit)?=null
    val ownsInput get()=dialog?.isShowing==true
    fun show(groups:List<Group>,selected:String?,origin:View,covered:ViewGroup,onSelected:(String)->Unit) {
        close()
        val tv=AdaptiveUi.isTv(activity)
        val window=object:Dialog(activity,R.style.Theme_CouchZap_Settings){
            override fun dispatchTouchEvent(event:MotionEvent):Boolean{if(event.actionMasked==MotionEvent.ACTION_DOWN)FocusMotionBinder.input(getWindow()!!.decorView,false);return super.dispatchTouchEvent(event)}
        }
        dialog=window
        var returnFocus=false
        window.setOnCancelListener{returnFocus=true}
        val full=FrameLayout(activity).apply {setBackgroundColor(0x7A000000);layoutDirection=covered.layoutDirection}
        val shell=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setBackgroundResource(R.drawable.bg_osd);setPadding(20.dp,20.dp,20.dp,20.dp)}
        val title=TextView(activity).apply {setText(R.string.groups);CouchZapTokens.text(this,CouchZapTokens.Text.PANE);ViewCompat.setAccessibilityHeading(this,true)}
        val help=TextView(activity).apply {setText(R.string.cz_groups_help);CouchZapTokens.text(this,CouchZapTokens.Text.BODY)}
        shell.addView(title);shell.addView(help)
        val list=RecyclerView(activity).apply {layoutManager=LinearLayoutManager(activity);itemAnimator=null;setPadding(8.dp,8.dp,8.dp,8.dp);clipToPadding=false}
        var target=groups.indexOfFirst{it.id==selected}.coerceAtLeast(0)
        var pending:Int?=null
        fun focus(index:Int) {
            if(groups.isEmpty())return
            target=index.coerceIn(0,groups.lastIndex);pending=target
            val row=list.findViewHolderForAdapterPosition(target)?.itemView
            if(row!=null&&row.requestFocusFromTouch())pending=null else{list.requestFocusFromTouch();list.scrollToPosition(target)}
        }
        list.adapter=object:RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount()=groups.size
            override fun onCreateViewHolder(parent:ViewGroup,type:Int):RecyclerView.ViewHolder=object:RecyclerView.ViewHolder(ChoiceRow(activity).apply {layoutParams=RecyclerView.LayoutParams(-1,-2)}){}
            override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int) {
                val item=groups[position];val row=holder.itemView as ChoiceRow
                // Item 9: group counts use localized digits for FA/AR.
                row.bind(item.id,item.label,com.couchzap.iptv.util.LocalizedNumbers.localize(item.count,com.couchzap.iptv.util.Prefs(activity).getLanguage()),item.id==selected)
                row.minimumHeight=if(tv)68.dp else 64.dp
                row.setOnClickListener {close();onSelected(item.id)}
            }
            override fun onViewRecycled(holder:RecyclerView.ViewHolder){FocusMotionBinder.reset(holder.itemView)}
        }
        list.isFocusableInTouchMode=true
        list.viewTreeObserver.addOnGlobalLayoutListener {pending?.let {index->list.findViewHolderForAdapterPosition(index)?.itemView?.let {pending=null;it.requestFocusFromTouch()} }}
        shell.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        val cancel=Button(activity).apply {setText(R.string.cancel);isAllCaps=false;isFocusable=true;minimumHeight=56.dp;CouchZapTokens.text(this,CouchZapTokens.Text.CONTROL);setOnClickListener {window.cancel()};FocusMotionBinder.bind(this);com.couchzap.iptv.ui.design.TouchActivationPolicy.apply(this)}
        shell.addView(cancel,LinearLayout.LayoutParams(-1,-2))
        val bounds=AdaptiveUi.from(activity.findViewById(R.id.root_container))
        val height=minOf(bounds.safeHeight,(160+groups.size.coerceAtMost(6)*68).dp)
        full.addView(shell,FrameLayout.LayoutParams(minOf(440.dp,bounds.safeWidth),height,Gravity.CENTER))
        full.setOnClickListener{window.cancel()};shell.isClickable=true
        window.setContentView(full);window.setCanceledOnTouchOutside(false)
        val oldAccess=covered.importantForAccessibility;val oldFocus=covered.descendantFocusability
        val keyboard=FocusMotionBinder.keyboard(covered.rootView);FocusMotionBinder.input(covered,false)
        covered.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        covered.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
        var restored=false
        val restore:()->Unit=restore@{
            if(restored)return@restore
            restored=true
            covered.importantForAccessibility=oldAccess;covered.descendantFocusability=oldFocus
            if(dialog===window){dialog=null;cleanup=null}
            FocusMotionBinder.input(covered,FocusMotionBinder.keyboard(window.window!!.decorView))
            if(returnFocus&&origin.isShown&&canRestore()){
                origin.requestFocusFromTouch()
                origin.postOnAnimation{if(!ownsInput&&canRestore()&&origin.isShown&&origin.hasWindowFocus())origin.requestFocusFromTouch()}
            }
        }
        cleanup=restore
        window.setOnDismissListener{restore()}
        var confirm:String?=null
        var confirmView:View?=null
        window.setOnKeyListener {_,key,event->
            if(event.action==KeyEvent.ACTION_DOWN)FocusMotionBinder.input(full,true)
            when(key) {
                KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE->{if(event.action==KeyEvent.ACTION_UP)window.cancel();true}
                KeyEvent.KEYCODE_MENU->{if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0){close();onMenu()};true}
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER->{
                    val current=full.findFocus()
                    if(event.action==KeyEvent.ACTION_DOWN){if(event.repeatCount>0)true else{confirm=current?.getTag(R.id.cz_action_key) as? String?:"cancel";confirmView=current;false}}
                    else{val valid=!event.isCanceled&&confirm==(current?.getTag(R.id.cz_action_key) as? String?:"cancel");confirm=null;if(!valid)confirmView?.isPressed=false;confirmView=null;!valid}
                }
                KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_UP->if(list.hasFocus()||pending!=null) {
                    if(event.action==KeyEvent.ACTION_DOWN) {
                        val pos=pending?:list.findFocus()?.let(list::findContainingItemView)?.let(list::getChildAdapterPosition)?:target
                        if(key==KeyEvent.KEYCODE_DPAD_DOWN && pos==groups.lastIndex){pending=null;cancel.requestFocusFromTouch()}
                        else focus(pos+if(key==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1)
                    };true
                }else if(cancel.hasFocus()){if(event.action==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_UP)focus(groups.lastIndex);true}else false
                else->false
            }
        }
        window.window?.apply {setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply{windowAnimations=0}}
        window.show();window.window?.setLayout(-1,-1)
        FocusMotionBinder.input(full.rootView,keyboard)
        ViewCompat.setAccessibilityPaneTitle(shell,activity.getString(R.string.groups))
        focus(target)
    }
    fun close(){val owned=dialog;val restore=cleanup;owned?.dismiss();restore?.invoke();if(dialog===owned){dialog=null;cleanup=null}}
    private val Int.dp get()=(this*activity.resources.displayMetrics.density+.5f).toInt()
}
