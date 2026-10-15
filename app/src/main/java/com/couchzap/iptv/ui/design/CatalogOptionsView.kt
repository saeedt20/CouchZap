package com.couchzap.iptv.ui.design

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.couchzap.iptv.R
import com.couchzap.iptv.data.PublicCatalog

/** Presentation-only option traversal. A pending logical index survives held keys and recycling. */
class CatalogOptionsView(context:Context,private val route:String,private val entries:List<PublicCatalog.Entry>,
    private val choose:(PublicCatalog.Entry)->Unit):LinearLayout(context) {
    val search=EditText(context).apply {
        id=View.generateViewId();setTag(R.id.cz_action_key,"$route/search")
        hint=context.getString(R.string.search);isSingleLine=true;minimumHeight=dp(56)
        inputType=android.text.InputType.TYPE_CLASS_TEXT;imeOptions=android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        nextFocusUpId=R.id.settings_back
        CouchZapTokens.text(this,CouchZapTokens.Text.CONTROL)
        setTextColor(context.getColor(R.color.cz_text_primary));setHintTextColor(context.getColor(R.color.cz_text_secondary))
        setPadding(dp(16),dp(8),dp(16),dp(8));FocusMotionBinder.bind(this)
    }
    private val empty=TextView(context).apply{setText(R.string.catalog_empty);CouchZapTokens.text(this,CouchZapTokens.Text.BODY);setPadding(dp(16),dp(16),dp(16),dp(16))}
    val list=RecyclerView(context).apply{
        id=View.generateViewId();layoutManager=LinearLayoutManager(context);itemAnimator=null
        clipToPadding=false;setPadding(dp(8),dp(8),dp(8),dp(8));setHasFixedSize(false)
        preserveFocusAfterLayout=false;isFocusable=false
    }
    private val ids=entries.mapIndexed{index,entry->entry.code to index.toLong()}.toMap()
    private var filtered=entries
    private var pending:Int?=null
    private var logical=-1
    private var scheduled=false
    private val applyFocus=Runnable { scheduled=false;focusPending() }
    private val rows=Rows()
    init {
        orientation=VERTICAL;addView(search,LayoutParams(-1,-2));addView(empty,LayoutParams(-1,-2));addView(list,LayoutParams(-1,0,1f))
        list.adapter=rows
        list.addOnChildAttachStateChangeListener(object:RecyclerView.OnChildAttachStateChangeListener{
            override fun onChildViewAttachedToWindow(view:View){scheduleFocus()}
            override fun onChildViewDetachedFromWindow(view:View){}
        })
        list.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->focusPending()}
        search.doAfterTextChanged{filter(it.toString())}
        updateEmpty()
    }
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    private fun updateEmpty(){empty.visibility=if(filtered.isEmpty())VISIBLE else GONE;list.visibility=if(filtered.isEmpty())GONE else VISIBLE}
    private fun filter(query:String){
        val focusedKey=findFocus()?.getTag(R.id.cz_action_key) as? String
        val next=entries.filter{it.label.contains(query,true)}
        val retained=next.indexOfFirst{"$route/${it.code}"==focusedKey}
        if(retained<0&&list.hasFocus())search.requestFocusFromTouch()
        pending=null;logical=retained;filtered=next;rows.notifyDataSetChanged();updateEmpty()
        if(retained>=0)move(retained)
    }
    private fun scheduleFocus(){if(pending!=null&&!scheduled){scheduled=true;postOnAnimation(applyFocus)}}
    private fun focusPending(){
        val target=pending?:return
        if(!isShown||!isAttachedToWindow)return
        val row=list.findViewHolderForAdapterPosition(target)?.itemView
        if(row!=null&&!list.isComputingLayout){
            if(row.requestFocusFromTouch()){logical=target;pending=null}
        }
    }
    private fun move(index:Int){
        if(index<0||filtered.isEmpty()){pending=null;logical=-1;search.requestFocusFromTouch();return}
        val target=index.coerceIn(0,filtered.lastIndex);pending=target;logical=target
        list.scrollToPosition(target);focusPending();scheduleFocus()
    }
    fun restore(key:String?):Boolean {
        if(key=="$route/search"){pending=null;logical=-1;return search.requestFocusFromTouch()}
        val index=filtered.indexOfFirst{"$route/${it.code}"==key}
        if(index<0)return false
        move(index);return true
    }
    fun dispatch(event:KeyEvent,focus:View?):Boolean {
        if(event.keyCode!=KeyEvent.KEYCODE_DPAD_UP&&event.keyCode!=KeyEvent.KEYCODE_DPAD_DOWN)return false
        val inside=focus===search||list.hasFocus()
        if(focus!=null&&!inside&&pending==null)return false
        if(event.action!=KeyEvent.ACTION_DOWN)return true
        if(focus==null&&pending==null){logical=-1;search.requestFocusFromTouch();return true}
        if(focus===search&&pending==null){
            if(event.keyCode==KeyEvent.KEYCODE_DPAD_UP)return false
            move(0);return true
        }
        val position=pending?:focus?.let{list.findContainingViewHolder(it)}?.bindingAdapterPosition?.takeIf{it!=RecyclerView.NO_POSITION}?:logical
        move(position+if(event.keyCode==KeyEvent.KEYCODE_DPAD_DOWN)1 else -1)
        return true
    }
    override fun onDetachedFromWindow(){removeCallbacks(applyFocus);scheduled=false;super.onDetachedFromWindow()}
    private inner class Rows:RecyclerView.Adapter<Holder>() {
        init{setHasStableIds(true)}
        override fun getItemCount()=filtered.size
        override fun getItemId(position:Int)=ids.getValue(filtered[position].code)
        override fun onCreateViewHolder(parent:ViewGroup,viewType:Int)=Holder(ActionRow(context).apply{layoutParams=RecyclerView.LayoutParams(-1,-2)})
        override fun onBindViewHolder(holder:Holder,position:Int){val entry=filtered[position];holder.row.bind("$route/${entry.code}",entry.label)}
        override fun onViewRecycled(holder:Holder){FocusMotionBinder.reset(holder.row)}
    }
    private inner class Holder(val row:ActionRow):RecyclerView.ViewHolder(row){
        init {
            row.setOnClickListener{val p=bindingAdapterPosition;if(p!=RecyclerView.NO_POSITION&&pending==null)choose(filtered[p])}
            val semantic=row.onFocusChangeListener
            row.onFocusChangeListener=View.OnFocusChangeListener{v,hasFocus->
                semantic?.onFocusChange(v,hasFocus)
                if(hasFocus&&pending==null&&bindingAdapterPosition!=RecyclerView.NO_POSITION)logical=bindingAdapterPosition
            }
        }
    }
}
