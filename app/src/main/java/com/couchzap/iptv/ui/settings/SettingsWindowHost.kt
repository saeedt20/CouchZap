package com.couchzap.iptv.ui.settings

import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.PowerManager
import android.view.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import com.couchzap.iptv.R
import com.couchzap.iptv.databinding.ActivityMainBinding
import com.couchzap.iptv.support.DiagnosticLog
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.ui.design.*
import com.couchzap.iptv.util.Prefs

/** The only Settings window. The video hierarchy never moves into this window. */
class SettingsWindowHost(
    private val activity:AppCompatActivity,private val binding:ActivityMainBinding,private val panel:SettingsPanel,
    private val canClose:()->Boolean,private val blockedClose:()->Unit,private val onBack:()->Unit,
    private val beforeOpen:()->Unit,private val afterClosed:()->Unit,private val menuAllowed:()->Boolean={true}
) {
    val root=FrameLayout(activity)
    val shell=binding.layoutSettings
    private val scrim=View(activity).apply{setBackgroundColor(Color.BLACK);alpha=0f;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    private val surface=android.graphics.drawable.GradientDrawable().apply{setColor(activity.getColor(R.color.cz_surface_shell))}
    private var closingForLifecycle=false
    private var disposed=false
    private var restoreAfterStop=false
    private var finishAfterStop=false
    private var sessionDowngraded=false
    private var capabilities:EffectsCapabilities?=null
    private var currentLayout=AdaptiveUi.from(binding.root)
    private var blur:WindowBlurApi31?=null
    private var restoreAccess=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    private var restoreFocus=ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private var covered=false
    private var originKeyboard=true
    private var confirmKey:String?=null
    private var confirmView:View?=null
    private var backDown=false
    private lateinit var headerDismiss:HeaderDismissGesture
    private val window=object:Dialog(activity,R.style.Theme_CouchZap_Settings){
        @Deprecated("Handled by the owned window") override fun onBackPressed(){handleBack()}
        override fun dispatchTouchEvent(event:MotionEvent):Boolean{if(event.actionMasked==MotionEvent.ACTION_DOWN)FocusMotionBinder.input(root.rootView,false);return super.dispatchTouchEvent(event)}
    }
    private val monitor=TransitionFrameMonitor{sessionDowngraded=true;motion.downgrade();DiagnosticLog.record("effects_session_safe")}
    val motion=SettingsMotionController(shell,scrim,binding.root.findViewById(R.id.cz_hud_scene),
        SceneVideoDepth(binding.root.findViewById(R.id.cz_video_scene),binding.root.findViewById(R.id.cz_video_mask)),::setBlur,::finishedClose)
    val ownsInput get()=motion.ownsInput
    val desiredOpen get()=motion.desiredOpen
    val renderedVisible get()=window.isShowing&&shell.visibility==View.VISIBLE
    val effectiveTier get()=motion.tier
    val windowRoot:View get()=window.window?.decorView?:root
    private var predictive:SettingsBackApi33?=null
    private val power=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){refreshPolicy()}}
    init {
        root.id=R.id.cz_settings_window_root;root.isFocusable=true;root.isFocusableInTouchMode=true;root.descendantFocusability=ViewGroup.FOCUS_AFTER_DESCENDANTS
        if(Build.VERSION.SDK_INT>=26){root.defaultFocusHighlightEnabled=false;shell.defaultFocusHighlightEnabled=false}
        root.layoutDirection=binding.root.layoutDirection
        shell.isClickable=true
        shell.background=surface
        (shell.parent as ViewGroup).removeView(shell)
        root.addView(scrim,FrameLayout.LayoutParams(-1,-1));root.addView(shell,FrameLayout.LayoutParams(-1,-1))
        window.setContentView(root);window.setCanceledOnTouchOutside(false);window.setCancelable(false)
        if(Build.VERSION.SDK_INT>=26)window.window?.decorView?.defaultFocusHighlightEnabled=false
        binding.settingsBack.isFocusableInTouchMode=true;binding.btnSettingsClose.isFocusableInTouchMode=true
        window.window?.apply{setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);attributes=attributes.apply{windowAnimations=0}}
        scrim.setOnClickListener{close()}
        window.setOnKeyListener{_,key,event->dispatchLocalKey(key,event)}
        ViewCompat.setOnApplyWindowInsetsListener(root){_,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime=insets.getInsets(WindowInsetsCompat.Type.ime())
            root.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,ime.bottom))
            if(root.width>0&&root.height>0){val layout=AdaptiveUi.from(root,currentLayout.tv);if(layout!=currentLayout)configure(layout)}
            insets
        }
        panel.hostOwnsInput={ownsInput&&desiredOpen}
        motion.onSettled={monitor.end()}
        motion.onSurfaceFailure={sessionDowngraded=true;root.post{if(!disposed&&ownsInput){setTierTags(shell,EffectsTier.SAFE);FocusMotionBinder.configure(windowRoot,"safe")};DiagnosticLog.record("effects_surface_unavailable")}}
        runCatching{androidx.core.content.ContextCompat.registerReceiver(activity,power,IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)}
        headerDismiss=HeaderDismissGesture(shell,motion,{panel.depth==0&&!AdaptiveUi.isTv(activity)&&canClose()&&motion.tier!=EffectsTier.OFF},{panel.beginClose()})
        binding.settingsTitle.setOnTouchListener(headerDismiss)
    }
    fun configure(ui:AdaptiveUi){
        currentLayout=ui
        val tv=ui.tv
        shell.layoutParams=FrameLayout.LayoutParams(if(tv)minOf(ui.dp(720),ui.safeWidth)else if(ui.expanded)minOf(ui.dp(840),ui.width)else ui.width,
            if(tv)ui.safeHeight else ui.height,Gravity.START or Gravity.TOP).apply{setMargins(ui.safeX,ui.safeY,ui.safeX,ui.safeY)}
        shell.setPaddingRelative(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12))
        surface.cornerRadius=if(!ui.tv&&!ui.expanded)0f else ui.dp(18).toFloat()
        panel.categories?.configure(ui);motion.configure(ui)
        if(ownsInput)setTierTags(shell,motion.tier)
    }
    fun open(staged:Boolean=false){
        if(disposed)return
        val fresh=!ownsInput
        if(desiredOpen)return
        if(fresh){if(!staged)beforeOpen();captureCover();shell.visibility=View.VISIBLE;shell.alpha=0f;scrim.alpha=0f;window.show();window.window?.setLayout(-1,-1)
            WindowCompat.setDecorFitsSystemWindows(window.window!!,false)
            WindowInsetsControllerCompat(window.window!!,root).apply{hide(WindowInsetsCompat.Type.systemBars());systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
            if(Build.VERSION.SDK_INT>=33&&predictive==null)predictive=SettingsBackApi33(window,::handleBack)
            attachEffects()
        }
        panel.resume()
        capabilities=EffectsPolicy.read(windowRoot,sessionDowngraded)
        val requested=EffectsPolicy.resolve(EffectsMode.from(Prefs(activity).getEffectsMode()),capabilities!!)
        motion.open(requested)
        val tier=motion.tier
        FocusMotionBinder.input(windowRoot,if(fresh)originKeyboard else FocusMotionBinder.keyboard(windowRoot))
        setTierTags(shell,tier)
        FocusMotionBinder.configure(windowRoot,tier.name.lowercase())
        panel.focusEntry()
        ViewCompat.setAccessibilityPaneTitle(shell,if(panel.depth==0)activity.getString(R.string.settings)else null)
        if(tier==EffectsTier.FULL)monitor.begin(windowRoot.display?.refreshRate?:60f)
    }
    fun close(){
        if(!ownsInput)return
        if(!canClose()){blockedClose();return}
        if(!desiredOpen)return
        headerDismiss.cancel();panel.beginClose();motion.close()
        if(effectiveTier==EffectsTier.FULL)monitor.begin(windowRoot.display?.refreshRate?:60f)
    }
    fun toggle(){if(desiredOpen)close()else open()}
    private fun dispatchLocalKey(key:Int,event:KeyEvent):Boolean {
        if(event.action==KeyEvent.ACTION_DOWN)FocusMotionBinder.input(windowRoot,true)
        when(key){
            KeyEvent.KEYCODE_MENU->{if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0&&menuAllowed())toggle();return true}
            KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_ESCAPE->{if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0)backDown=true;if(event.action==KeyEvent.ACTION_UP&&backDown){backDown=false;handleBack()};return true}
        }
        if(!desiredOpen)return true
        if(key in listOf(KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER)){
            if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount==0){confirmView=panel.focused;confirmKey=panel.actionKey(confirmView)}
            if(event.action==KeyEvent.ACTION_DOWN&&event.repeatCount>0)return true
            if(event.action==KeyEvent.ACTION_UP){val matches=!event.isCanceled&&confirmKey==panel.actionKey(panel.focused);confirmKey=null;val pressed=confirmView;confirmView=null;if(!matches){pressed?.isPressed=false;return true}}
        }
        return panel.dispatchDirection(event)
    }
    private fun handleBack(){
        if(!canClose()){blockedClose();return}
        val insets=ViewCompat.getRootWindowInsets(root)
        if(insets?.isVisible(WindowInsetsCompat.Type.ime())==true){ViewCompat.getWindowInsetsController(root)?.hide(WindowInsetsCompat.Type.ime());return}
        if(motion.state==SettingsMotionController.State.ENTERING){close();return}
        if(desiredOpen)onBack()
    }
    /** Activity test/legacy input enters the actual Dialog dispatcher, never the playback dispatcher. */
    fun dispatch(event:KeyEvent)=window.dispatchKeyEvent(event)
    private fun attachEffects(){
        if(Build.VERSION.SDK_INT>=31&&blur==null)runCatching{
            blur=WindowBlurApi31(window.window!!,activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager){enabled->if(!enabled&&ownsInput){motion.downgrade();setTierTags(shell,EffectsTier.SAFE)}}.also{it.attach()}
        }.onFailure{sessionDowngraded=true;blur=null;DiagnosticLog.record("effects_blur_unavailable")}
        runCatching{monitor.attach(activity.window);monitor.attach(window.window!!)}
    }
    private fun setBlur(value:Int){if(Build.VERSION.SDK_INT>=31)runCatching{blur?.radiusPx(value)}.onFailure{sessionDowngraded=true;root.post{if(ownsInput)motion.downgrade(true)}}}
    fun refreshPolicy(){
        if(!ownsInput)return
        capabilities=EffectsPolicy.read(windowRoot,sessionDowngraded)
        val tier=EffectsPolicy.resolve(EffectsMode.from(Prefs(activity).getEffectsMode()),capabilities!!)
        if(tier!=EffectsTier.FULL){motion.reduceTier(tier);setTierTags(shell,tier);FocusMotionBinder.configure(windowRoot,tier.name.lowercase())}
    }
    fun retryEffectsOnNextOpen(){sessionDowngraded=false;monitor.retry()}
    fun explanation():Int {
        val c=capabilities?:EffectsPolicy.read(windowRoot,sessionDowngraded)
        return when{!c.systemAnimationsEnabled->R.string.cz_effects_system_off;effectiveTier==EffectsTier.FULL->R.string.cz_effects_full_help;
            effectiveTier==EffectsTier.OFF->R.string.cz_effects_off_help;Prefs(activity).getEffectsMode()=="full"&&c.api>=31&&!c.windowBlurEnabled->R.string.cz_effects_unavailable;else->R.string.cz_effects_safe_reason}
    }
    private fun setTierTags(view:View,tier:EffectsTier){
        view.setTag(R.id.cz_motion_owner,tier.name.lowercase())
        if(view===shell)surface.setColor(activity.getColor(if(tier==EffectsTier.FULL)R.color.cz_surface_shell_translucent else R.color.cz_surface_shell))
        if(view.id in intArrayOf(R.id.settings_playback_group,R.id.settings_playlist_group,R.id.settings_app_group))
            (view.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(activity.getColor(if(tier==EffectsTier.FULL)R.color.cz_surface_content_translucent else R.color.cz_surface_content))
        if(view is ViewGroup)for(i in 0 until view.childCount)setTierTags(view.getChildAt(i),tier)
    }
    private fun captureCover(){if(covered)return;covered=true;originKeyboard=FocusMotionBinder.keyboard(binding.root.rootView);FocusMotionBinder.input(binding.root,false);restoreAccess=binding.root.importantForAccessibility;restoreFocus=binding.root.descendantFocusability;binding.root.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;binding.root.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS}
    private fun restoreCover(){if(!covered)return;covered=false;binding.root.importantForAccessibility=restoreAccess;binding.root.descendantFocusability=restoreFocus;FocusMotionBinder.input(binding.root,FocusMotionBinder.keyboard(windowRoot))}
    private fun finishedClose(){
        if(disposed)return
        setBlur(0);monitor.end();panel.reset();shell.visibility=View.GONE
        window.dismiss();restoreCover()
        if(!closingForLifecycle)afterClosed()
    }
    fun settleForConfigurationChange(){headerDismiss.cancel();monitor.pause();if(ownsInput)motion.settleDesired()}
    fun closeImmediatelyForLifecycle(){
        restoreAfterStop=desiredOpen;finishAfterStop=ownsInput&&!desiredOpen
        headerDismiss.cancel();confirmView?.isPressed=false;confirmView=null;confirmKey=null;backDown=false
        closingForLifecycle=true;monitor.pause();panel.pausePresentation();motion.closeImmediately();shell.visibility=View.GONE;window.dismiss();restoreCover();closingForLifecycle=false
    }
    fun resumeAfterStop(){if(disposed)return;if(finishAfterStop){finishAfterStop=false;panel.reset();afterClosed();return};if(!restoreAfterStop)return;restoreAfterStop=false;open(staged=true);panel.restorePresentationFocus()}
    fun destroy(){if(disposed)return;closeImmediatelyForLifecycle();restoreAfterStop=false;panel.reset();disposed=true;motion.destroy();monitor.destroy();if(Build.VERSION.SDK_INT>=31)runCatching{blur?.detach()};if(Build.VERSION.SDK_INT>=33)predictive?.destroy();predictive=null;runCatching{activity.unregisterReceiver(power)}}
}
