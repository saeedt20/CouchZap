package com.couchzap.iptv

import android.app.UiModeManager
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnNextLayout
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.couchzap.iptv.data.Channel
import com.couchzap.iptv.data.PlaylistRepository
import com.couchzap.iptv.databinding.ActivityMainBinding
import com.couchzap.iptv.player.PlayerController
import com.couchzap.iptv.ui.ChannelListAdapter
import com.couchzap.iptv.ui.ImportFragmentlessScreen
import com.couchzap.iptv.ui.NumberEntryController
import com.couchzap.iptv.ui.OsdController
import com.couchzap.iptv.ui.SimpleFileBrowser
import com.couchzap.iptv.util.Anim
import com.couchzap.iptv.util.Keys
import com.couchzap.iptv.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity(), PlayerController.PlayerEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var prefs: Prefs

    private var playerController: PlayerController? = null
    private lateinit var osdController: OsdController
    private lateinit var numberEntryController: NumberEntryController
    private lateinit var importScreen: ImportFragmentlessScreen
    private lateinit var fileBrowser: SimpleFileBrowser
    private lateinit var gestureDetector: GestureDetector

    private val channels = mutableListOf<Channel>()
    private var currentIndex = 0
    private var pendingIndex = 0

    private val handler = Handler(Looper.getMainLooper())

    private var backPressTime = 0L
    private val aspectRatios = arrayOf(Prefs.ASPECT_FIT, Prefs.ASPECT_FILL, Prefs.ASPECT_16_9, Prefs.ASPECT_4_3)
    private var currentAspectIndex = 0
    private var isTvMode = false
    private var importInProgress = false
    private var pickerOpen = false
    private var consumeConfirmRelease = false

    // Debounced Zap Runnable
    private val zapRunnable = Runnable {
        executeZap()
    }

    private val savePrefsRunnable = Runnable {
        repository.saveLastChannelIndex(currentIndex)
    }

    // Document Picker launcher
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pickerOpen = false
            if (result.resultCode == RESULT_OK && result.data?.data != null) {
                val uri = result.data!!.data!!
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Fallback
                }
                importUri(uri)
            }
        }

    // Storage permission launcher for fallback browser
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                fileBrowser.openAtRoot()
            } else {
                if (channels.isEmpty()) importScreen.show()
                Toast.makeText(this, "Storage permission required for file picker", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PlaylistRepository(this)
        prefs = Prefs(this)
        currentAspectIndex = aspectRatios.indexOf(prefs.getAspectRatioMode()).coerceAtLeast(0)

        setupFullscreen()

        // Init OSD & UI Controllers
        osdController = OsdController(
            binding = binding.viewOsd,
            tvGhostNext = binding.tvGhostNext,
            onChannelsClick = { toggleChannelListOverlay() },
            onPrevClick = { zapPrev() },
            onPlayPauseClick = { playerController?.togglePlayPause() },
            onNextClick = { zapNext() },
            onAspectClick = { cycleAspectRatio() },
            onSettingsClick = { toggleSettingsMenu() }
        )

        numberEntryController = NumberEntryController(
            tvDigitGhost = binding.tvDigitGhost,
            tvToastMsg = binding.tvToastMsg,
            totalChannelCount = { channels.size },
            onCommitChannelNumber = { channelNum ->
                jumpToChannelNumber(channelNum)
            }
        )

        importScreen = ImportFragmentlessScreen(
            binding = binding.viewImport,
            onImportButtonClicked = {
                launchImportPicker()
            }
        )

        fileBrowser = SimpleFileBrowser(
            container = binding.layoutFileBrowser,
            tvPath = binding.tvFileBrowserPath,
            rvFiles = binding.rvFileBrowser,
            onFileSelected = { file ->
                importFile(file)
            },
            onCloseRequested = {
                if (channels.isEmpty()) importScreen.show()
            }
        )

        setupChannelListOverlay()
        setupSettingsMenu()
        setupGesturesAndTouch()
        applyInterfaceMode()
        updateAspectLabel()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        // Check if playlist is ready
        if (repository.isPlaylistReady()) {
            importScreen.hide()
            loadChannelsAndStartPlayback()
        } else {
            importScreen.show()
        }
    }

    private fun setupFullscreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        osdController.resume()
        if (playerController == null) {
            playerController = PlayerController(this, binding.surfaceView, binding.videoFrame, this)
            playerController?.setAspectRatioMode(prefs.getAspectRatioMode())
        }
        if (channels.isNotEmpty()) {
            playCurrentChannel()
        }
    }

    override fun onStop() {
        super.onStop()
        osdController.pause()
        consumeConfirmRelease = false
        handler.removeCallbacksAndMessages(null)
        numberEntryController.cancel()
        pendingIndex = currentIndex
        if (channels.isNotEmpty()) repository.saveLastChannelIndex(currentIndex)
        playerController?.release()
        playerController = null
    }

    private fun loadChannelsAndStartPlayback() {
        lifecycleScope.launch {
            val cached = try {
                repository.loadCachedChannels()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Could not read the saved playlist. Please import it again.", Toast.LENGTH_LONG).show()
                emptyList()
            }
            if (cached.isNotEmpty()) {
                channels.clear()
                channels.addAll(cached)
                currentIndex = repository.getLastChannelIndex().coerceIn(0, channels.size - 1)
                pendingIndex = currentIndex
                updateChannelListAdapter()

                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playCurrentChannel()
            } else {
                importScreen.show()
                Toast.makeText(this@MainActivity, R.string.no_channels_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchImportPicker() {
        if (importInProgress || pickerOpen) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "audio/x-mpegurl",
                    "application/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "text/plain",
                    "application/octet-stream"
                )
            )
        }
        try {
            pickerOpen = true
            openDocumentLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            pickerOpen = false
            openFallbackFileBrowser()
        }
    }

    private fun openFallbackFileBrowser() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Toast.makeText(this, "No document picker installed. Enable a system Files app to import playlists.", Toast.LENGTH_LONG).show()
            return
        }
        importScreen.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            fileBrowser.openAtRoot()
        }
    }

    private fun importUri(uri: Uri) = importPlaylist {
        contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
    }

    private fun importFile(file: java.io.File) = importPlaylist { file.inputStream() }

    private fun importPlaylist(openStream: () -> InputStream) {
        if (importInProgress) return
        importInProgress = true
        lifecycleScope.launch {
            importScreen.show()
            importScreen.showProgress("Importing playlist…")
            try {
                val parsedChannels = withContext(Dispatchers.IO) {
                    repository.importPlaylistFromStream(openStream())
                }
                if (parsedChannels.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.no_channels_found, Toast.LENGTH_LONG).show()
                } else {
                    handler.removeCallbacksAndMessages(null)
                    numberEntryController.cancel()
                    channels.clear()
                    channels.addAll(parsedChannels)
                    currentIndex = 0
                    pendingIndex = 0
                    updateChannelListAdapter()
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playCurrentChannel()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity, "Could not import playlist. Your previous playlist is preserved.", Toast.LENGTH_LONG).show()
            } finally {
                importInProgress = false
                importScreen.hideProgress()
                if (channels.isNotEmpty()) importScreen.hide()
            }
        }
    }

    private fun setupChannelListOverlay() {
        binding.viewChannelList.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.viewChannelList.rvChannels.itemAnimator = null
        val adapter = ChannelListAdapter { channel ->
            val index = channels.indexOf(channel)
            if (index != -1) {
                pendingIndex = index
                executeZap()
            }
            closeChannelListOverlay()
        }
        binding.viewChannelList.rvChannels.adapter = adapter
        binding.viewChannelList.btnCloseChannelList.setOnClickListener {
            closeChannelListOverlay()
        }
    }

    private fun updateChannelListAdapter() {
        val adapter = binding.viewChannelList.rvChannels.adapter as? ChannelListAdapter
        adapter?.setChannels(channels, currentIndex)
    }

    private fun toggleChannelListOverlay() {
        if (binding.viewChannelList.channelListContainer.visibility == View.VISIBLE) {
            closeChannelListOverlay()
        } else {
            openChannelListOverlay()
        }
    }

    private fun openChannelListOverlay() {
        closeSettingsMenu()
        osdController.hideOsd()
        binding.btnQuickSettings.visibility = View.GONE
        Anim.showOverlay(binding.viewChannelList.channelListContainer)
        val adapter = binding.viewChannelList.rvChannels.adapter as? ChannelListAdapter
        adapter?.setSelectedIndex(currentIndex)
        val list = binding.viewChannelList.rvChannels
        list.doOnNextLayout {
            if (binding.viewChannelList.root.visibility == View.VISIBLE) {
                list.findViewHolderForAdapterPosition(currentIndex)?.itemView?.requestFocus()
                    ?: list.requestFocus()
            }
        }
        (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentIndex, 0)
        list.requestLayout()
    }

    private fun closeChannelListOverlay() {
        Anim.hideOverlay(binding.viewChannelList.channelListContainer)
        binding.root.requestFocus()
        binding.btnQuickSettings.visibility = if (isTvMode) View.GONE else View.VISIBLE
    }

    private fun setupSettingsMenu() {
        binding.btnQuickSettings.setOnClickListener { toggleSettingsMenu() }
        binding.btnSettingsMode.setOnClickListener {
            val modes = arrayOf("Auto", "TV", "Phone")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Interface mode")
                .setSingleChoiceItems(modes, modes.indexOf(prefs.getInterfaceMode())) { dialog, which ->
                    prefs.setInterfaceMode(modes[which])
                    applyInterfaceMode()
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        binding.btnSettingsClose.setOnClickListener {
            closeSettingsMenu()
        }

        binding.btnSettingsReimport.setOnClickListener {
            closeSettingsMenu()
            launchImportPicker()
        }

        binding.btnSettingsAspect.setOnClickListener {
            cycleAspectRatio()
        }

        binding.btnSettingsExit.setOnClickListener {
            hardExit()
        }
    }

    private fun cycleAspectRatio() {
        currentAspectIndex = (currentAspectIndex + 1) % aspectRatios.size
        val mode = aspectRatios[currentAspectIndex]
        prefs.setAspectRatioMode(mode)
        playerController?.setAspectRatioMode(mode)

        updateAspectLabel()
    }

    private fun updateAspectLabel() {
        val modeName = when (prefs.getAspectRatioMode()) {
            Prefs.ASPECT_FIT -> getString(R.string.aspect_fit)
            Prefs.ASPECT_FILL -> getString(R.string.aspect_fill)
            Prefs.ASPECT_16_9 -> getString(R.string.aspect_16_9)
            Prefs.ASPECT_4_3 -> getString(R.string.aspect_4_3)
            else -> "Fit"
        }
        binding.btnSettingsAspect.text = getString(R.string.settings_aspect, modeName)
    }

    private fun toggleSettingsMenu() {
        if (binding.layoutSettings.visibility == View.VISIBLE) {
            closeSettingsMenu()
        } else {
            closeChannelListOverlay()
            osdController.hideOsd()
            binding.layoutSettings.visibility = View.VISIBLE
            binding.layoutSettings.bringToFront()
            binding.viewImport.root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.layoutFileBrowser.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.btnQuickSettings.visibility = View.GONE
            binding.btnSettingsReimport.requestFocus()
        }
    }

    private fun closeSettingsMenu() {
        binding.layoutSettings.visibility = View.GONE
        binding.viewImport.root.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.layoutFileBrowser.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (importScreen.isVisible()) binding.viewImport.btnImport.requestFocus()
        binding.btnQuickSettings.visibility = if (isTvMode) View.GONE else View.VISIBLE
    }

    private fun setupGesturesAndTouch() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (importScreen.isVisible() || fileBrowser.isVisible()) {
                    return false
                }
                if (binding.viewChannelList.channelListContainer.visibility == View.VISIBLE) {
                    closeChannelListOverlay()
                    return true
                }
                if (binding.layoutSettings.visibility == View.VISIBLE) {
                    closeSettingsMenu()
                    return true
                }
                if (channels.isNotEmpty()) {
                    if (osdController.isVisible()) {
                        osdController.hideOsd()
                    } else {
                        osdController.showOsd(channels[currentIndex], autoHideMs = 5000L)
                    }
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (channels.isNotEmpty()) {
                    playerController?.togglePlayPause()
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                if (importScreen.isVisible() || fileBrowser.isVisible()) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 100 && Math.abs(velocityX) > 150) {
                    if (binding.viewChannelList.channelListContainer.visibility == View.VISIBLE) {
                        if (deltaX < 0) {
                            closeChannelListOverlay()
                            return true
                        }
                    } else {
                        if (deltaX < 0) {
                            zapNext()
                            return true
                        } else {
                            zapPrev()
                            return true
                        }
                    }
                } else if (Math.abs(deltaY) > Math.abs(deltaX) && Math.abs(deltaY) > 100 && Math.abs(velocityY) > 150) {
                    if (deltaY < 0 && binding.viewChannelList.channelListContainer.visibility != View.VISIBLE) {
                        openChannelListOverlay()
                        return true
                    } else if (deltaY > 0 && binding.viewChannelList.channelListContainer.visibility == View.VISIBLE) {
                        closeChannelListOverlay()
                        return true
                    }
                }
                return false
            }
        })

        binding.surfaceView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && ::gestureDetector.isInitialized && gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        // A press that opens the menu must not release onto a newly focused row.
        if (Keys.isConfirmKey(key) && consumeConfirmRelease) {
            if (event.action == KeyEvent.ACTION_UP) {
                consumeConfirmRelease = false
                return true
            }
            if (event.repeatCount > 0) return true
            // Recover if a remote or window transition dropped the previous release.
            consumeConfirmRelease = false
        }
        if (Keys.isBackKey(key)) {
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        if (Keys.isMenuKey(key) && !importInProgress) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) toggleSettingsMenu()
            return true
        }
        val overlay = importScreen.isVisible() || fileBrowser.isVisible() ||
            binding.layoutSettings.visibility == View.VISIBLE ||
            binding.viewChannelList.channelListContainer.visibility == View.VISIBLE
        if (overlay) return super.dispatchKeyEvent(event)
        val osdFocused = osdController.controlsActive && binding.viewOsd.root.hasFocus() && osdController.isVisible()
        if (osdFocused && (Keys.isConfirmKey(key) || key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT)) {
            osdController.resetAutoHide()
            return super.dispatchKeyEvent(event)
        }
        val handled = Keys.isDigitKey(key) || Keys.isConfirmKey(key) ||
            Keys.isNextChannelKey(key) || Keys.isPrevChannelKey(key) ||
            key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            Keys.isInfoKey(key) || Keys.isPlayPauseKey(key)
        if (!handled || channels.isEmpty()) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0 && !Keys.isNextChannelKey(key) && !Keys.isPrevChannelKey(key)) return true
        when {
            Keys.isDigitKey(key) -> numberEntryController.appendDigit(Keys.getDigitFromKey(key))
            Keys.isConfirmKey(key) -> {
                consumeConfirmRelease = true
                if (numberEntryController.isTyping()) numberEntryController.commitDigits() else openChannelListOverlay()
            }
            Keys.isNextChannelKey(key) -> zapNext()
            Keys.isPrevChannelKey(key) -> zapPrev()
            key == KeyEvent.KEYCODE_DPAD_UP -> openChannelListOverlay()
            key == KeyEvent.KEYCODE_DPAD_DOWN || Keys.isInfoKey(key) -> {
                osdController.showOsd(channels[currentIndex], autoHideMs = 0)
                binding.viewOsd.btnOsdChannels.requestFocus()
            }
            Keys.isPlayPauseKey(key) -> playerController?.togglePlayPause()
        }
        return true
    }

    private fun handleBack() {
        when {
            importInProgress -> Toast.makeText(this, "Finishing playlist import…", Toast.LENGTH_SHORT).show()
            binding.layoutSettings.visibility == View.VISIBLE -> closeSettingsMenu()
            fileBrowser.isVisible() -> fileBrowser.handleBack()
            numberEntryController.isTyping() -> numberEntryController.cancel()
            binding.viewChannelList.channelListContainer.visibility == View.VISIBLE -> closeChannelListOverlay()
            osdController.isVisible() -> osdController.hideOsd()
            else -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (backPressTime != 0L && now - backPressTime < 2000L) hardExit()
                else {
                    backPressTime = now
                    Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyInterfaceMode() {
        val mode = prefs.getInterfaceMode()
        val detectedTv = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        isTvMode = mode == "TV" || (mode == "Auto" && detectedTv)
        requestedOrientation = if (isTvMode) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        binding.btnSettingsMode.text = "Interface: $mode (${if (isTvMode) "TV" else "Phone"})"
        binding.btnQuickSettings.visibility = if (isTvMode) View.GONE else View.VISIBLE
        updateResponsiveLayout()
    }

    private fun updateResponsiveLayout() {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val width = dp(resources.configuration.screenWidthDp)
        val height = dp(resources.configuration.screenHeightDp)
        val tvHorizontalInset = if (isTvMode) (width * 0.05f).toInt() else 0
        val tvVerticalInset = if (isTvMode) (height * 0.05f).toInt() else 0
        val portrait = !isTvMode && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        binding.viewChannelList.root.layoutParams = (binding.viewChannelList.root.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = if (portrait) FrameLayout.LayoutParams.MATCH_PARENT else minOf(dp(380), width - 2 * tvHorizontalInset)
            setMargins(tvHorizontalInset, tvVerticalInset, tvHorizontalInset, tvVerticalInset)
        }
        binding.viewOsd.root.layoutParams = (binding.viewOsd.root.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = minOf(dp(560), width - dp(if (isTvMode) 48 else 16))
            gravity = if (portrait) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
            setMargins(dp(if (isTvMode) 24 else 8), dp(8), dp(8), dp(16))
        }
        binding.layoutSettings.layoutParams = (binding.layoutSettings.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = minOf(dp(360), width - dp(16))
            this.height = minOf(dp(340), dp(resources.configuration.screenHeightDp) - dp(32))
        }
        binding.viewImport.tvImportTitle.textSize = if (isTvMode) 36f else 26f
        binding.viewImport.tvImportTitle.gravity = Gravity.CENTER
        binding.viewImport.tvImportHint.gravity = Gravity.CENTER
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateResponsiveLayout()
        setupFullscreen()
    }

    private fun zapNext() {
        if (channels.isEmpty()) return
        pendingIndex = (pendingIndex + 1) % channels.size
        scheduleDebouncedZap()
    }

    private fun zapPrev() {
        if (channels.isEmpty()) return
        pendingIndex = if (pendingIndex - 1 < 0) channels.size - 1 else pendingIndex - 1
        scheduleDebouncedZap()
    }

    private fun jumpToChannelNumber(channelNum: Int) {
        val targetIndex = channels.indexOfFirst { it.number == channelNum }
        if (targetIndex != -1) {
            pendingIndex = targetIndex
            executeZap()
        }
    }

    private fun scheduleDebouncedZap() {
        val pendingChannel = channels[pendingIndex]
        val nextIndex = (pendingIndex + 1) % channels.size
        val nextChannel = channels[nextIndex]

        osdController.showPendingZapOsd(pendingChannel, nextChannel.name)

        Anim.fadeInShutter(binding.vShutter)

        handler.removeCallbacks(zapRunnable)
        handler.postDelayed(zapRunnable, 400L)
    }

    private fun executeZap() {
        handler.removeCallbacks(zapRunnable)
        if (channels.isEmpty()) return
        currentIndex = pendingIndex
        val channel = channels[currentIndex]

        (binding.viewChannelList.rvChannels.adapter as? ChannelListAdapter)?.setSelectedIndex(currentIndex)

        playerController?.stop()
        playerController?.playChannel(channel)

        osdController.showOsd(channel, autoHideMs = 5000L)

        handler.removeCallbacks(savePrefsRunnable)
        handler.postDelayed(savePrefsRunnable, 1000L)
    }

    private fun playCurrentChannel() {
        if (channels.isEmpty()) return
        val channel = channels[currentIndex]
        playerController?.playChannel(channel)
        osdController.showOsd(channel, autoHideMs = 5000L)
    }

    // PlayerEventListener Callbacks
    override fun onRenderedFirstFrame() {
        Anim.fadeOutShutter(binding.vShutter)
    }

    override fun onTracksUpdated(videoFormat: androidx.media3.common.Format?, audioFormat: androidx.media3.common.Format?) {
        osdController.updateTracks(videoFormat, audioFormat)
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        // Video resized
    }

    override fun onPlaybackStateChanged(state: Int, isBuffering: Boolean) {
        osdController.setBuffering(isBuffering)
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onChannelFailed(channel: Channel) {
        Toast.makeText(this, getString(R.string.channel_unavailable), Toast.LENGTH_LONG).show()
    }

    // Hard Exit: "stop the app completely, no background usage"
    private fun hardExit() {
        // 1. Stop ExoPlayer
        playerController?.stop()

        // 2. Remove Handler callbacks
        handler.removeCallbacksAndMessages(null)
        osdController.destroy()

        // 3. Clear video surface & listeners & release
        playerController?.release()
        playerController = null

        // 4. Clear Keep Screen On
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 5. Finish and remove task
        finishAndRemoveTask()


    }

    override fun onDestroy() {
        super.onDestroy()
        playerController?.release()
        playerController = null
        osdController.destroy()
        numberEntryController.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
