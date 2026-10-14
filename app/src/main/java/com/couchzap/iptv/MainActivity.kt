package com.couchzap.iptv

import android.content.Context
import android.widget.LinearLayout
import com.couchzap.iptv.ui.SettingsPanel
import com.couchzap.iptv.ui.WelcomeIntro
import com.couchzap.iptv.player.CrtSwitchSound
import com.couchzap.iptv.util.AppLanguage
import android.app.UiModeManager
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
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
import android.view.ViewGroup
import android.view.FocusFinder
import android.view.ViewConfiguration
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
import com.couchzap.iptv.player.ChannelPlaybackStatus
import com.couchzap.iptv.ui.ChannelListNavigation
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
import com.couchzap.iptv.data.PlaylistSource
import com.couchzap.iptv.data.HttpStreams
import com.couchzap.iptv.data.EpgRepository
import com.couchzap.iptv.ui.ChannelArtwork
import com.couchzap.iptv.ui.PlaylistMenus
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.C
import java.text.DateFormat
import java.util.Date
import com.couchzap.iptv.support.DiagnosticLog
import com.couchzap.iptv.support.DiagnosticMenu
import com.couchzap.iptv.support.UpdateMenu

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity(), PlayerController.PlayerEventListener {

    private lateinit var welcomeIntro: WelcomeIntro
    private lateinit var crtSound: CrtSwitchSound
    private lateinit var settingsPanel: SettingsPanel
    private var languageGate = false
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.wrap(newBase)) }

    private lateinit var updateMenu: UpdateMenu
    private lateinit var diagnosticMenu: DiagnosticMenu
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: PlaylistRepository
    private lateinit var prefs: Prefs

    private var playerController: PlayerController? = null
    private lateinit var osdController: OsdController
    private lateinit var numberEntryController: NumberEntryController
    private lateinit var importScreen: ImportFragmentlessScreen
    private lateinit var fileBrowser: SimpleFileBrowser
    private lateinit var gestureDetector: GestureDetector
    private lateinit var epg: EpgRepository
    private lateinit var artwork: ChannelArtwork
    private lateinit var playlistMenus: PlaylistMenus
    private lateinit var channelNavigation: ChannelListNavigation
    private var playbackStatus = ChannelPlaybackStatus.CONNECTING
    private var confirmChannelId: String? = null
    private var confirmLongHandled = false
    private val longConfirm = Runnable {
        val id = confirmChannelId
        val channel = channelNavigation.targetChannel()
        if (id != null && channel?.stableId == id && binding.viewChannelList.root.visibility == View.VISIBLE) {
            confirmLongHandled = true
            toggleFavourite(channel)
        }
    }
    private var favouritesOnly = false
    private var selectedGroup: String? = null
    private var previousWatchedId: String? = null
    private var guidePicker = false
    private var sleepExpired = false
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        sleepExpired = true
        playerController?.pause()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) notice(getString(R.string.timer_done))
        sleepMinutes = 0
        binding.tvSettingsSleepValue.text = getString(R.string.off)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private val channels = mutableListOf<Channel>()
    private var currentIndex = 0
    private var pendingIndex = 0

    private val handler = Handler(Looper.getMainLooper())

    private var backPressTime = 0L
    private val aspectRatios = arrayOf(Prefs.ASPECT_FIT, Prefs.ASPECT_FILL, Prefs.ASPECT_16_9, Prefs.ASPECT_4_3)
    private var currentAspectIndex = 0
    private var isTvMode = false
    private var importMessage = R.string.download_playlist
    private var renderImport: (() -> Unit)? = null
    private var importInProgress = false
    private var importCommitting = false
    private var importGeneration = 0
    private var importJob: kotlinx.coroutines.Job? = null
    private var welcomeFlowOrigin: View? = null
    private var pickerOpen = false
    private var consumeConfirmRelease = false

    // Debounced Zap Runnable
    private val zapRunnable = Runnable {
        executeZap()
    }

    private val savePrefsRunnable = Runnable {
        repository.saveLastChannelIndex(currentIndex, channels.getOrNull(currentIndex))
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
                if (guidePicker) importGuide(uri.toString()) { contentResolver.openInputStream(uri) ?: error("Could not read file") }
                else importUri(uri)
            }
        }

    // Storage permission launcher for fallback browser
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                showFallbackBrowser()
            } else {
                if (channels.isEmpty()) importScreen.show()
                Toast.makeText(this, getString(R.string.storage_permission), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLog.initialize(this)
        DiagnosticLog.record("app_start")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.layoutDirection = if (Prefs(this).getLanguage() == "fa") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        repository = PlaylistRepository(this)
        prefs = Prefs(this)
        crtSound = CrtSwitchSound.create(this, prefs)
        welcomeIntro = WelcomeIntro(binding.viewImport, prefs)
        settingsPanel = SettingsPanel(this, binding).also { panel ->
            panel.onClose = { if (!languageGate && !importCommitting) closeSettingsMenu() }
            panel.onRootBack = { if (welcomeFlowOrigin != null && !languageGate) closeSettingsMenu() }
        }
        updateMenu = UpdateMenu(this, settingsPanel)
        diagnosticMenu = DiagnosticMenu(this, settingsPanel, ::notice)
        epg = EpgRepository(this)
        artwork = ChannelArtwork(lifecycleScope)
        playlistMenus = PlaylistMenus(this, repository, lifecycleScope,
            onFile = { launchImportPicker() },
            onSource = { source, refresh -> importSource(source, refresh) },
            onNotice = ::notice, panel = settingsPanel, onOpen = { if (binding.layoutSettings.visibility != View.VISIBLE) toggleSettingsMenu() })
        currentAspectIndex = aspectRatios.indexOf(prefs.getAspectRatioMode()).coerceAtLeast(0)

        setupFullscreen()

        // Init OSD & UI Controllers
        osdController = OsdController(
            binding = binding.viewOsd,
            tvGhostNext = binding.tvGhostNext,
            onChannelsClick = { toggleChannelListOverlay() },
            onPrevClick = { zapPrev() },
            onPlayPauseClick = { togglePlayback() },
            onNextClick = { zapNext() },
            onAspectClick = { cycleAspectRatio() },
            onSettingsClick = { toggleSettingsMenu() },
            onFavouriteClick = { channels.getOrNull(currentIndex)?.let(::toggleFavourite) },
            onPreviousWatchedClick = { zapPreviousWatched() },
            isFavourite = repository::isFavourite,
            programmes = { epg.nowNext(it) },
            artwork = artwork
        )

        numberEntryController = NumberEntryController(
            tvDigitGhost = binding.tvDigitGhost,
            tvToastMsg = binding.tvToastMsg,
            totalChannelCount = { channels.maxOfOrNull { it.number } ?: 0 },
            onCommitChannelNumber = { channelNum ->
                jumpToChannelNumber(channelNum)
            }
        )

        importScreen = ImportFragmentlessScreen(
            binding = binding.viewImport,
            onImportButtonClicked = { openWelcomeFlow(binding.viewImport.btnImport) { playlistMenus.ownChoices() } },
            onPublicButtonClicked = { openWelcomeFlow(binding.viewImport.btnPublic) { playlistMenus.publicChoices() } },
            onLanguageButtonClicked = { openWelcomeFlow(binding.viewImport.btnImportLanguage) { languageMenu() } }
        )

        fileBrowser = SimpleFileBrowser(
            container = binding.layoutFileBrowser,
            tvPath = binding.tvFileBrowserPath,
            rvFiles = binding.rvFileBrowser,
            onFileSelected = { file ->
                if (binding.layoutSettings.visibility == View.VISIBLE && settingsPanel.depth > 0) settingsPanel.back()
                if (guidePicker) importGuide(Uri.fromFile(file).toString()) { file.inputStream() } else importFile(file)
            },
            onCloseRequested = {
                if (binding.layoutSettings.visibility == View.VISIBLE && settingsPanel.depth > 0) settingsPanel.back()
                else if (channels.isEmpty()) importScreen.show()
            }
        )

        // The IO load may finish inline; every UI controller must exist first.
        lifecycleScope.launch { withContext(Dispatchers.IO) { epg.load() }; osdController.refresh() }

        setupChannelListOverlay()
        setupSettingsMenu()
        setupGesturesAndTouch()
        binding.btnRetry.setOnClickListener { binding.layoutPlaybackError.visibility = View.GONE; playCurrentChannel() }
        binding.btnErrorNext.setOnClickListener { zapNext() }
        applyInterfaceMode()
        updateAspectLabel()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { handleBack() }
        })

        // Check if playlist is ready
        if (repository.isPlaylistReady()) {
            prefs.markIntroSeen()
            importScreen.hide()
            loadChannelsAndStartPlayback()
        } else {
            importScreen.show()
            if (prefs.getLanguage() == null) { languageGate = true; toggleSettingsMenu(); importScreen.hide(); languageMenu() }
            else welcomeIntro.start()
        }
    }

    private fun setupFullscreen() {
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
        crtSound.resume()
        osdController.resume()
        // The welcome screen does not need codecs, audio focus or a player surface.
        if (channels.isNotEmpty() && !sleepExpired) {
            playCurrentChannel()
        }
        if (binding.viewChannelList.root.visibility == View.VISIBLE) focusCurrentChannel()
        updatePlaybackErrorFocus()
    }

    override fun onStop() {
        super.onStop()
        welcomeIntro.finish(); crtSound.pause(); Anim.finishAll(binding.root)
        osdController.pause()
        consumeConfirmRelease = false
        cancelRowConfirm()
        channelNavigation.cancel()
        handler.removeCallbacksAndMessages(null)
        numberEntryController.cancel()
        pendingIndex = currentIndex
        if (channels.isNotEmpty()) repository.saveLastChannelIndex(currentIndex, channels[currentIndex])
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
                Toast.makeText(this@MainActivity, getString(R.string.saved_playlist_failed), Toast.LENGTH_LONG).show()
                emptyList()
            }
            if (cached.isNotEmpty()) {
                channels.clear()
                channels.addAll(cached)
                currentIndex = repository.getLastChannelIndex().coerceIn(0, channels.size - 1)
                pendingIndex = currentIndex
                updateChannelListAdapter()

                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && !sleepExpired) playCurrentChannel()
            } else {
                importScreen.show()
                Toast.makeText(this@MainActivity, R.string.no_channels_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchImportPicker(forGuide: Boolean = false) {
        if (importInProgress || pickerOpen) return
        guidePicker = forGuide
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                if (forGuide) arrayOf("application/xml", "text/xml", "application/gzip", "application/octet-stream") else arrayOf(
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
            notice(getString(R.string.pick_failure))
            return
        }
        importScreen.hide()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            showFallbackBrowser()
        }
    }

    private fun showFallbackBrowser() {
        if (binding.layoutSettings.visibility != View.VISIBLE) toggleSettingsMenu()
        val browser = binding.layoutFileBrowser
        settingsPanel.page("files", R.string.btn_import_playlist, leave = {
            fileBrowser.close(); (browser.parent as? ViewGroup)?.removeView(browser)
            binding.root.addView(browser, FrameLayout.LayoutParams(-1, -1))
        }) { body ->
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            body.addView(browser, LinearLayout.LayoutParams(-1, settingsPanel.dp(320)))
            fileBrowser.openAtRoot()
        }
    }

    private fun importUri(uri: Uri) = importPlaylist(PlaylistSource("file", uri.toString(), uri.lastPathSegment?.substringAfterLast('/') ?: getString(R.string.playlist_file))) {
        contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
    }

    private fun importFile(file: java.io.File) = importPlaylist(PlaylistSource("file", Uri.fromFile(file).toString(), file.name)) { file.inputStream() }

    private fun importSource(source: PlaylistSource, refresh: Boolean) = importPlaylist(source, refresh) {
        if (source.kind == "file") contentResolver.openInputStream(Uri.parse(source.location)) ?: error("The original file is no longer available")
        else HttpStreams.open(source.location)
    }

    private fun cancelPlaylistImport(): Boolean {
        if (importCommitting) { notice(getString(R.string.finishing_import)); return false }
        if (!importInProgress) return true
        importGeneration++; importJob?.cancel(); importJob = null; importInProgress = false
        importScreen.hideProgress(); notice(getString(R.string.import_cancelled))
        return true
    }

    private fun importPlaylist(source: PlaylistSource? = null, refresh: Boolean = false, openStream: () -> InputStream) {
        if (importInProgress) return
        val before = channels.getOrNull(currentIndex)
        if (refresh && before != null) repository.saveLastChannelIndex(currentIndex, before)
        val token = ++importGeneration
        importInProgress = true
        importCommitting = false
        DiagnosticLog.record("playlist_import_start")
        importMessage = if (source?.kind == "file") R.string.loading_channels else R.string.download_playlist
        if (binding.layoutSettings.visibility != View.VISIBLE) toggleSettingsMenu()
        settingsPanel.page("playlist-import", R.string.playlist_sources, leave = {
            renderImport = null
            if (importInProgress && !importCommitting) cancelPlaylistImport()
        }) { body ->
            val status = settingsPanel.text(body, "")
            val cancel = settingsPanel.button(body, getString(R.string.cancel)) { if (cancelPlaylistImport()) settingsPanel.back() }
            renderImport = {
                status.text = if (importCommitting) getString(R.string.finishing_import)
                    else if (importMessage == R.string.loading_channels) getString(R.string.loading_channels, 0) else getString(importMessage)
                cancel.isEnabled = !importCommitting
            }; renderImport?.invoke()
        }
        importScreen.showProgress(getString(R.string.download_playlist))
        importJob = lifecycleScope.launch {
            var succeeded = false
            try {
                val prepared = withContext(Dispatchers.IO) { repository.preparePlaylist(openStream(), source) }
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (token != importGeneration) return@launch
                if (prepared.channels.isEmpty()) {
                    importMessage = R.string.no_channels_found; renderImport?.invoke()
                    notice(getString(R.string.no_channels_found)); return@launch
                }
                // UI cancellation is accepted until this short atomic commit begins.
                importCommitting = true; renderImport?.invoke()
                val parsedChannels = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    repository.commitPlaylist(prepared, source, refresh)
                }
                if (token != importGeneration || isDestroyed) return@launch
                handler.removeCallbacksAndMessages(null); numberEntryController.cancel()
                channels.clear(); channels.addAll(parsedChannels)
                currentIndex = repository.getLastChannelIndex().coerceIn(channels.indices); pendingIndex = currentIndex
                val after = channels[currentIndex]
                val samePlayback = refresh && before?.stableId == after.stableId && before.url == after.url
                if (!refresh) {
                    previousWatchedId = null; favouritesOnly = false; selectedGroup = null
                    binding.viewChannelList.etChannelSearch.setText("")
                }
                updateChannelListAdapter()
                if (!samePlayback && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playCurrentChannel()
                else osdController.refreshChannel(after)
                succeeded = true
                DiagnosticLog.record("playlist_import_success")
                notice(getString(R.string.loaded_channels, parsedChannels.size) + if (prepared.skipped > 0) "\n" + getString(R.string.unsupported_skipped, prepared.skipped) else "")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (token == importGeneration) { importMessage = R.string.import_failed; renderImport?.invoke(); DiagnosticLog.record("playlist_import_failed", error); showImportError(error) }
            } finally {
                if (token == importGeneration) {
                    importInProgress = false; importCommitting = false; importScreen.hideProgress()
                    if (channels.isNotEmpty()) importScreen.hide()
                    if (succeeded && !refresh) { closeSettingsMenu(); if (source?.kind != "file") openChannelListOverlay() }
                    else if (succeeded && settingsPanel.currentKey == "playlist-import") { settingsPanel.back(); updateSourceLabel() }
                }
            }
        }
    }

    private fun setupChannelListOverlay() {
        binding.viewChannelList.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.viewChannelList.rvChannels.itemAnimator = null
        val adapter = ChannelListAdapter(artwork = artwork, onFavourite = ::toggleFavourite,
            onFocused = { channel -> channelNavigation.remember(channel) }) { channel ->
            selectChannel(channel)
        }
        binding.viewChannelList.rvChannels.adapter = adapter
        channelNavigation = ChannelListNavigation(binding.viewChannelList.rvChannels, adapter,
            isActive = { binding.viewChannelList.root.visibility == View.VISIBLE && !isFinishing && !isDestroyed },
            isTv = { isTvMode }, onEmpty = { binding.viewChannelList.etChannelSearch.requestFocus() })
        binding.viewChannelList.btnCloseChannelList.setOnClickListener {
            closeChannelListOverlay()
        }
        binding.viewChannelList.etChannelSearch.doAfterTextChanged { updateChannelListAdapter() }
        binding.viewChannelList.etChannelSearch.setOnEditorActionListener { _, _, _ -> focusChannel(0); true }
        binding.viewChannelList.btnFilterAll.setOnClickListener { favouritesOnly = false; selectedGroup = null; updateChannelListAdapter(); focusCurrentChannel() }
        binding.viewChannelList.btnFilterFavourites.setOnClickListener { favouritesOnly = true; selectedGroup = null; updateChannelListAdapter(); focusCurrentChannel() }
        binding.viewChannelList.btnFilterGroups.setOnClickListener {
            val values = channels.map { it.group.orEmpty() }.distinct().sorted()
            val names = values.map { it.ifEmpty { getString(R.string.other_group) } }.toTypedArray()
            AlertDialog.Builder(this).setTitle(R.string.groups).setItems(names) { _, index ->
                selectedGroup = values[index]; favouritesOnly = false; updateChannelListAdapter(); focusCurrentChannel()
            }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    private fun updateChannelListAdapter() {
        val adapter = binding.viewChannelList.rvChannels.adapter as? ChannelListAdapter
        val restoreRow = ::channelNavigation.isInitialized && binding.viewChannelList.root.visibility == View.VISIBLE &&
            (channelNavigation.hasPending || channelNavigation.focusedPosition() >= 0)
        if (::channelNavigation.isInitialized) channelNavigation.cancel()
        val query = binding.viewChannelList.etChannelSearch.text.toString().trim()
        val favourites = repository.favouriteIds()
        val filtered = channels.filter { (!favouritesOnly || it.stableId in favourites) &&
            (selectedGroup == null || it.group.orEmpty() == selectedGroup) &&
            (query.isEmpty() || it.name.contains(query, true) || it.number.toString().startsWith(query)) }
        val playingId = channels.getOrNull(currentIndex)?.stableId
        adapter?.setChannels(filtered, filtered.indexOfFirst { it.stableId == playingId }, favourites)
        adapter?.updatePlayback(playingId, playbackStatus)
        binding.viewChannelList.tvEmptyChannels.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.viewChannelList.tvChannelListHeader.text = getString(R.string.channels) + " · " + filtered.size
        binding.viewChannelList.btnFilterAll.isSelected = !favouritesOnly && selectedGroup == null
        binding.viewChannelList.btnFilterFavourites.isSelected = favouritesOnly
        binding.viewChannelList.btnFilterGroups.isSelected = selectedGroup != null
        if (restoreRow) focusCurrentChannel()
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
        updatePlaybackErrorFocus()
        updateChannelListAdapter()
        focusCurrentChannel()
    }

    private fun focusCurrentChannel() {
        channelNavigation.restore(channels.getOrNull(currentIndex)?.stableId)
    }
    private fun focusChannel(position: Int) {
        channelNavigation.focus(position)
    }
    private fun toggleFavourite(channel: Channel) {
        val list = binding.viewChannelList.rvChannels
        val row = currentFocus?.let { list.findContainingItemView(it) }
        val index = row?.let { list.getChildAdapterPosition(it) } ?: -1
        val added = repository.toggleFavourite(channel)
        updateChannelListAdapter(); osdController.refresh()
        if (index >= 0 && binding.viewChannelList.root.visibility == View.VISIBLE) focusChannel(index)
        notice(getString(if (added) R.string.favourite_added else R.string.favourite_removed))
    }

    private fun selectChannel(channel: Channel) {
        val index = channels.indexOf(channel)
        if (index < 0) return
        pendingIndex = index
        executeZap()
        closeChannelListOverlay()
    }

    private fun closeChannelListOverlay() {
        cancelRowConfirm()
        channelNavigation.cancel()
        Anim.hideOverlay(binding.viewChannelList.channelListContainer)
        binding.root.requestFocus()
        updatePlaybackErrorFocus()
        binding.btnQuickSettings.visibility = if (isTvMode) View.GONE else View.VISIBLE
    }

    private fun setupSettingsMenu() {
        binding.btnSettingsCrt.isChecked = prefs.isCrtSoundEnabled()
        binding.btnSettingsCrt.setOnCheckedChangeListener { _, checked -> if (checked != prefs.isCrtSoundEnabled()) crtSound.setEnabled(checked) }
        binding.btnQuickSettings.setOnClickListener { toggleSettingsMenu() }
        settingsPanel.onClick(binding.btnSettingsClose) { if (!languageGate) closeSettingsMenu() }
        settingsPanel.onClick(binding.btnSettingsMode) {
            val modes = listOf("Auto", "TV", "Phone")
            settingsPanel.choices("mode", R.string.interface_mode,
                listOf(getString(R.string.automatic), "TV", getString(R.string.touch_mode)), modes.indexOf(prefs.getInterfaceMode())) { which ->
                prefs.setInterfaceMode(modes[which]); applyInterfaceMode(); settingsPanel.back()
            }
        }
        settingsPanel.onClick(binding.btnSettingsReimport) { playlistMenus.show() }
        settingsPanel.onClick(binding.btnSettingsAspect) {
            settingsPanel.choices("aspect", R.string.choose_aspect,
                listOf(getString(R.string.aspect_fit), getString(R.string.aspect_fill), "16:9", "4:3"), currentAspectIndex) { which ->
                currentAspectIndex = which; prefs.setAspectRatioMode(aspectRatios[which])
                playerController?.setAspectRatioMode(aspectRatios[which]); updateAspectLabel(); settingsPanel.back()
            }
        }
        settingsPanel.onClick(binding.btnSettingsExit) { hardExit() }
        settingsPanel.onClick(binding.btnSettingsTracks) {
            settingsPanel.choices("tracks", R.string.audio_subtitles, listOf(getString(R.string.audio), getString(R.string.subtitles))) { which ->
                chooseTrack(if (which == 0) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
            }
        }
        settingsPanel.onClick(binding.btnSettingsSleep) {
            val times = intArrayOf(0, 15, 30, 60, 90)
            settingsPanel.choices("sleep", R.string.sleep_timer,
                times.map { if (it == 0) getString(R.string.off) else getString(R.string.minutes, it) }, times.indexOf(sleepMinutes)) { index ->
                sleepHandler.removeCallbacks(sleepRunnable); sleepExpired = false; sleepMinutes = times[index]
                if (sleepMinutes > 0) sleepHandler.postDelayed(sleepRunnable, sleepMinutes * 60000L)
                updateSleepLabel(); notice(getString(if (sleepMinutes == 0) R.string.timer_off else R.string.timer_minutes, sleepMinutes))
                settingsPanel.back()
            }
        }
        settingsPanel.onClick(binding.btnSettingsLanguage) { languageMenu() }
        settingsPanel.onClick(binding.btnSettingsGuide) { guideMenu() }
        settingsPanel.onClick(binding.btnSettingsUpdate) { updateMenu.show() }
        settingsPanel.onClick(binding.btnSettingsLogs) { diagnosticMenu.show(repository.source()?.kind, channels.size) }
        settingsPanel.onClick(binding.btnSettingsAbout) {
            settingsPanel.page("about", R.string.about) { body ->
                settingsPanel.text(body, getString(R.string.about_body))
                val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                settingsPanel.text(body, getString(R.string.version_label, androidx.core.text.BidiFormatter.getInstance(prefs.getLanguage() == "fa").unicodeWrap(version, false)))
                settingsPanel.button(body, getString(R.string.github)) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/saeedt20/CouchZap"))) }
                        .onFailure { notice(getString(R.string.update_failed)) }
                }
            }
        }
    }

    private var guideJob: kotlinx.coroutines.Job? = null
    private var guideMessage: Pair<Int, Int>? = null
    private var renderGuide: (() -> Unit)? = null
    private var sleepMinutes = 0
    private fun updateSleepLabel() { binding.tvSettingsSleepValue.text = if (sleepMinutes == 0) getString(R.string.off) else getString(R.string.minutes, sleepMinutes) }
    private fun languageMenu() {
        welcomeIntro.finish(); Anim.finishAll(binding.root)
        settingsPanel.choices("language", if (languageGate) R.string.choose_language else R.string.language,
            listOf("فارسی", "English"), listOf("fa", "en").indexOf(prefs.getLanguage())) { which ->
            val language = if (which == 0) "fa" else "en"
            prefs.setLanguage(language)
            AppLanguage.apply(this, binding.root, language)
            AppLanguage.translate(this, binding.settingsRootScroll)
            settingsPanel.back(); settingsPanel.header()
            applyInterfaceMode(); updateAspectLabel(); updateSleepLabel(); updateSourceLabel()
            binding.viewChannelList.rvChannels.adapter?.notifyDataSetChanged()
            playerController?.refreshPlaybackInfo()
            osdController.refresh()
            if (languageGate) { languageGate = false; binding.btnSettingsClose.visibility = View.VISIBLE; importScreen.show(); closeSettingsMenu(); welcomeIntro.start() }
        }
        if (languageGate) { binding.settingsBack.visibility = View.GONE; binding.btnSettingsClose.visibility = View.GONE }
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
        binding.tvSettingsAspectValue.text = modeName
        binding.btnSettingsAspect.contentDescription = getString(R.string.settings_aspect, modeName)
    }

    private fun openWelcomeFlow(origin: View, action: () -> Unit) {
        if (languageGate) return
        welcomeFlowOrigin = origin
        if (binding.layoutSettings.visibility != View.VISIBLE) toggleSettingsMenu()
        action()
    }

    private fun toggleSettingsMenu() {
        if (binding.layoutSettings.visibility == View.VISIBLE) {
            closeSettingsMenu()
        } else {
            closeChannelListOverlay()
            osdController.hideOsd()
            Anim.showOverlay(binding.layoutSettings)
            updatePlaybackErrorFocus()
            binding.layoutSettings.bringToFront()
            binding.viewImport.root.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.layoutFileBrowser.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            binding.btnQuickSettings.visibility = View.GONE
            updateSourceLabel()
            settingsPanel.header(); settingsPanel.focusFirst()
        }
    }

    private fun updateSourceLabel() {
        val source = repository.source()
        val label = source?.label?.let { androidx.core.text.BidiFormatter.getInstance(prefs.getLanguage() == "fa").unicodeWrap(it) }
        binding.tvSettingsSource.text = if (source == null) getString(R.string.playlist_not_added) else label + "\n" +
            getString(R.string.source_updated, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(repository.updatedAt())))
    }

    private fun closeSettingsMenu() {
        if (importCommitting) { notice(getString(R.string.finishing_import)); return }
        val origin = welcomeFlowOrigin
        welcomeFlowOrigin = null
        settingsPanel.reset()
        Anim.hideOverlay(binding.layoutSettings)
        binding.viewImport.root.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.layoutFileBrowser.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (importScreen.isVisible()) (origin ?: binding.viewImport.btnImport).requestFocusFromTouch()
        binding.btnQuickSettings.visibility = if (isTvMode) View.GONE else View.VISIBLE
        updatePlaybackErrorFocus()
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
                    togglePlayback()
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

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && ::welcomeIntro.isInitialized) welcomeIntro.finish()
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && ::gestureDetector.isInitialized && gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::welcomeIntro.isInitialized) welcomeIntro.finish()
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
            cancelRowConfirm()
            if (event.action == KeyEvent.ACTION_UP) handleBack()
            return true
        }
        if (Keys.isMenuKey(key) && !importInProgress) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !languageGate) toggleSettingsMenu()
            return true
        }
        if (binding.viewChannelList.root.visibility == View.VISIBLE) {
            val list = binding.viewChannelList.rvChannels
            if (Keys.isConfirmKey(key) && (list.hasFocus() || channelNavigation.hasPending || confirmChannelId != null)) {
                handleRowConfirm(event)
                return true
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (list.hasFocus() || channelNavigation.hasPending || !binding.viewChannelList.root.hasFocus()) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (!binding.viewChannelList.root.hasFocus()) recordFocusState("focus_recovered", "channels")
                        cancelRowConfirm()
                        channelNavigation.move(if (key == KeyEvent.KEYCODE_DPAD_UP) -1 else 1)
                    }
                    return true
                }
            }
            if (key == (if (binding.root.layoutDirection == View.LAYOUT_DIRECTION_RTL) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) && (list.hasFocus() || channelNavigation.hasPending)) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    cancelRowConfirm()
                    channelNavigation.cancel()
                    val filter = when { favouritesOnly -> binding.viewChannelList.btnFilterFavourites; selectedGroup != null -> binding.viewChannelList.btnFilterGroups; else -> binding.viewChannelList.btnFilterAll }
                    if (isTvMode) filter.requestFocusFromTouch() else filter.requestFocus()
                }
                return true
            }
            if (dispatchPanelDirection(binding.viewChannelList.root, event)) return true
        } else if (binding.layoutSettings.visibility == View.VISIBLE && dispatchPanelDirection(binding.layoutSettings, event)) {
            return true
        }
        val overlay = importScreen.isVisible() || fileBrowser.isVisible() ||
            binding.layoutSettings.visibility == View.VISIBLE ||
            binding.viewChannelList.channelListContainer.visibility == View.VISIBLE ||
            binding.layoutPlaybackError.visibility == View.VISIBLE
        if (overlay) return super.dispatchKeyEvent(event)
        val osdFocused = osdController.controlsActive && binding.viewOsd.root.hasFocus() && osdController.isVisible()
        if (osdFocused && (Keys.isConfirmKey(key) || key in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT)) {
            osdController.resetAutoHide()
            return super.dispatchKeyEvent(event)
        }
        val handled = Keys.isDigitKey(key) || Keys.isConfirmKey(key) ||
            Keys.isNextChannelKey(key) || Keys.isPrevChannelKey(key) ||
            key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
            Keys.isInfoKey(key) || Keys.isPlayPauseKey(key) || key == KeyEvent.KEYCODE_LAST_CHANNEL || key == KeyEvent.KEYCODE_DEL
        if (!handled || channels.isEmpty()) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (event.repeatCount > 0 && !Keys.isNextChannelKey(key) && !Keys.isPrevChannelKey(key)) return true
        when {
            key == KeyEvent.KEYCODE_DEL -> numberEntryController.deleteLastDigit()
            key == KeyEvent.KEYCODE_LAST_CHANNEL -> zapPreviousWatched()
            Keys.isDigitKey(key) -> numberEntryController.appendDigit(Keys.getDigitFromKey(key))
            Keys.isConfirmKey(key) -> {
                consumeConfirmRelease = true
                if (numberEntryController.isTyping()) numberEntryController.commitDigits() else openChannelListOverlay()
            }
            Keys.isNextChannelKey(key) -> zapNext()
            Keys.isPrevChannelKey(key) -> zapPrev()
            key == KeyEvent.KEYCODE_DPAD_UP -> openChannelListOverlay()
            key == KeyEvent.KEYCODE_DPAD_DOWN || Keys.isInfoKey(key) -> {
                if (Keys.isInfoKey(key) && osdController.isVisible() && osdController.controlsActive) osdController.toggleDetails()
                else { osdController.showOsd(channels[currentIndex], autoHideMs = 0); osdController.focusControls() }
            }
            key == KeyEvent.KEYCODE_MEDIA_PLAY -> { if (sleepExpired) { sleepExpired = false; playCurrentChannel() } else playerController?.play() }
            key == KeyEvent.KEYCODE_MEDIA_PAUSE -> playerController?.pause()
            Keys.isPlayPauseKey(key) -> togglePlayback()
        }
        return true
    }

    private fun cancelRowConfirm() {
        handler.removeCallbacks(longConfirm)
        confirmChannelId = null
        confirmLongHandled = false
    }

    private fun handleRowConfirm(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount == 0) {
                cancelRowConfirm()
                confirmChannelId = channelNavigation.targetChannel()?.stableId
                if (confirmChannelId != null) handler.postDelayed(longConfirm, ViewConfiguration.getLongPressTimeout().toLong())
            } else if (event.isLongPress && !confirmLongHandled) longConfirm.run()
        } else if (event.action == KeyEvent.ACTION_UP) {
            val channel = channelNavigation.targetChannel()
            val select = !event.isCanceled && !confirmLongHandled && confirmChannelId != null && channel?.stableId == confirmChannelId
            cancelRowConfirm()
            if (select && channel != null) selectChannel(channel)
        }
    }

    /** Search only inside the active panel; global traversal can reach background controls. */
    private fun dispatchPanelDirection(panel: ViewGroup, event: KeyEvent): Boolean {
        val direction = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }
        if (currentFocus is android.widget.EditText && direction in listOf(View.FOCUS_LEFT, View.FOCUS_RIGHT)) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            cancelRowConfirm()
            val focused = currentFocus?.takeIf { isWithin(it, panel) }
            if (focused == null) recordFocusState("focus_recovered", if (panel === binding.viewChannelList.root) "channels" else "settings")
            val next = FocusFinder.getInstance().findNextFocus(panel, focused, direction)
            if (panel === binding.viewChannelList.root) channelNavigation.cancel()
            if (next != null) {
                if (isTvMode) next.requestFocusFromTouch() else next.requestFocus()
            } else if (focused == null) {
                if (panel === binding.viewChannelList.root) focusCurrentChannel()
                else settingsPanel.focusFirst()
            }
        }
        return true
    }

    private fun isWithin(view: View, parent: ViewGroup): Boolean {
        var node: android.view.ViewParent? = view.parent
        while (node != null) { if (node === parent) return true; node = node.parent }
        return view === parent
    }

    private fun updatePlaybackErrorFocus() {
        val covered = binding.viewChannelList.root.visibility == View.VISIBLE || binding.layoutSettings.visibility == View.VISIBLE ||
            importScreen.isVisible() || fileBrowser.isVisible()
        binding.layoutPlaybackError.descendantFocusability = if (covered) ViewGroup.FOCUS_BLOCK_DESCENDANTS else ViewGroup.FOCUS_AFTER_DESCENDANTS
        binding.btnRetry.isFocusable = !covered
        binding.btnErrorNext.isFocusable = !covered
        binding.btnRetry.isEnabled = !covered
        binding.btnErrorNext.isEnabled = !covered
        if (!covered && binding.layoutPlaybackError.visibility == View.VISIBLE && !osdController.isVisible()) binding.btnRetry.requestFocus()
    }

    private fun recordFocusState(event: String, panel: String) {
        // Only fixed tags, numeric view/row positions and an enum: no channel names, IDs or URLs.
        DiagnosticLog.record("$event:p:$panel:f:${currentFocus?.id ?: 0}:r:${channelNavigation.focusedPosition()}:s:${playbackStatus.name.lowercase(java.util.Locale.ROOT)}")
    }

    private fun handleBack() {
        when {
            binding.layoutSettings.visibility == View.VISIBLE -> { if (importCommitting) notice(getString(R.string.finishing_import)) else if (fileBrowser.isVisible()) fileBrowser.handleBack() else if (languageGate) notice(getString(R.string.language_required)) else if (!settingsPanel.back()) closeSettingsMenu() }
            importInProgress -> Toast.makeText(this, getString(R.string.finishing_import), Toast.LENGTH_SHORT).show()
            fileBrowser.isVisible() -> fileBrowser.handleBack()
            numberEntryController.isTyping() -> numberEntryController.cancel()
            binding.viewChannelList.channelListContainer.visibility == View.VISIBLE -> closeChannelListOverlay()
            osdController.isVisible() -> osdController.hideOsd()
            binding.layoutPlaybackError.visibility == View.VISIBLE -> { binding.layoutPlaybackError.visibility = View.GONE; binding.root.requestFocus() }
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
        val detectedTv = (getSystemService(UI_MODE_SERVICE) as? UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        isTvMode = mode == "TV" || (mode == "Auto" && detectedTv)
        requestedOrientation = if (isTvMode) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        binding.tvSettingsModeValue.text = getString(R.string.interface_label, when (mode) { "Auto" -> getString(R.string.automatic); "Phone" -> getString(R.string.touch_mode); else -> "TV" })
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
            this.width = if (portrait) FrameLayout.LayoutParams.MATCH_PARENT else minOf(resources.getDimensionPixelSize(R.dimen.sidebar_width), width)
            gravity = Gravity.LEFT
            setMargins(0, 0, 0, 0)
        }
        binding.viewChannelList.root.setPadding(maxOf(dp(24), tvHorizontalInset), maxOf(dp(24), tvVerticalInset), dp(24), maxOf(dp(24), tvVerticalInset))
        binding.viewOsd.root.layoutParams = (binding.viewOsd.root.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = minOf(resources.getDimensionPixelSize(R.dimen.sidebar_width), width - 2 * maxOf(dp(8), tvHorizontalInset))
            gravity = if (portrait) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
            setMargins(maxOf(dp(8), tvHorizontalInset), maxOf(dp(8), tvVerticalInset), maxOf(dp(8), tvHorizontalInset), maxOf(dp(16), tvVerticalInset))
        }
        binding.layoutSettings.layoutParams = (binding.layoutSettings.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = if (portrait) FrameLayout.LayoutParams.MATCH_PARENT else minOf(resources.getDimensionPixelSize(R.dimen.sidebar_width), width)
            this.height = FrameLayout.LayoutParams.MATCH_PARENT
            gravity = Gravity.LEFT
            setMargins(0, 0, 0, 0)
        }
        binding.layoutSettings.setPadding(maxOf(dp(24), tvHorizontalInset), maxOf(dp(24), tvVerticalInset), dp(24), maxOf(dp(24), tvVerticalInset))
        if (::importScreen.isInitialized) importScreen.updateLayout(isTvMode)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::binding.isInitialized && ::prefs.isInitialized) {
            welcomeIntro.finish(); Anim.finishAll(binding.root)
            AppLanguage.apply(this, binding.root, prefs.getLanguage() ?: "en")
            AppLanguage.translate(this, binding.settingsRootScroll)
            updateAspectLabel(); updateSleepLabel(); updateSourceLabel()
            settingsPanel.header()
            if (languageGate) { binding.settingsBack.visibility = View.GONE; binding.btnSettingsClose.visibility = View.GONE }
            playerController?.refreshPlaybackInfo(); osdController.refresh()
        }
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
        } else notice(getString(R.string.no_channel_number, channelNum.toString()))
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
        val priorChannel = channels.getOrNull(currentIndex)
        if (currentIndex != pendingIndex) previousWatchedId = priorChannel?.stableId
        currentIndex = pendingIndex
        sleepExpired = false
        binding.layoutPlaybackError.visibility = View.GONE
        val channel = channels[currentIndex]
        crtSound.committed(priorChannel, channel)
        playbackStatus = ChannelPlaybackStatus.CONNECTING
        updateChannelListAdapter()

        playerController?.stop()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            playCurrentChannel()
        } else {
            osdController.showOsd(channel, autoHideMs = 5000L)
        }

        handler.removeCallbacks(savePrefsRunnable)
        handler.postDelayed(savePrefsRunnable, 1000L)
    }

    private fun playCurrentChannel() {
        if (channels.isEmpty()) return
        playbackStatus = ChannelPlaybackStatus.CONNECTING
        (binding.viewChannelList.rvChannels.adapter as ChannelListAdapter).updatePlayback(channels[currentIndex].stableId, playbackStatus)
        if (playerController == null) {
            playerController = PlayerController(this, binding.surfaceView, binding.videoFrame, this, binding.subtitleView)
            playerController?.setAspectRatioMode(prefs.getAspectRatioMode())
        }
        binding.layoutPlaybackError.visibility = View.GONE
        updatePlaybackErrorFocus()
        val channel = channels[currentIndex]
        playerController?.playChannel(channel)
        if (binding.viewChannelList.root.visibility != View.VISIBLE && binding.layoutSettings.visibility != View.VISIBLE) {
            osdController.showOsd(channel, autoHideMs = 5000L)
        }
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
        if (channel.stableId != channels.getOrNull(currentIndex)?.stableId || channel.url != channels.getOrNull(currentIndex)?.url) return
        onChannelPlaybackChanged(channel, ChannelPlaybackStatus.UNAVAILABLE)
        recordFocusState("playback_failed", when {
            binding.viewChannelList.root.visibility == View.VISIBLE -> "channels"
            binding.layoutSettings.visibility == View.VISIBLE -> "settings"
            else -> "playback"
        })
        Anim.fadeOutShutter(binding.vShutter)
        osdController.hideOsd()
        binding.layoutPlaybackError.visibility = View.VISIBLE
        updatePlaybackErrorFocus()
    }

    override fun onChannelPlaybackChanged(channel: Channel, status: ChannelPlaybackStatus) {
        if (channel.stableId != channels.getOrNull(currentIndex)?.stableId || channel.url != channels.getOrNull(currentIndex)?.url) return
        playbackStatus = status
        (binding.viewChannelList.rvChannels.adapter as? ChannelListAdapter)?.updatePlayback(channel.stableId, status)
    }

    override fun onPlaybackInfo(playWhenReady: Boolean, isLive: Boolean, status: String) {
        if (playWhenReady) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        osdController.setPlaybackInfo(playWhenReady, isLive, status)
    }
    private fun togglePlayback() {
        if (sleepExpired) { sleepExpired = false; playCurrentChannel() } else playerController?.togglePlayPause()
    }
    private fun zapPreviousWatched() {
        val index = channels.indexOfFirst { it.stableId == previousWatchedId }
        if (index >= 0) { pendingIndex = index; executeZap() } else notice(getString(R.string.no_previous))
    }
    private fun chooseTrack(type: Int) {
        val player = playerController
        val tracks = player?.choices(type).orEmpty()
        val title = if (type == C.TRACK_TYPE_AUDIO) R.string.audio else R.string.subtitles
        if (tracks.isEmpty() || (type == C.TRACK_TYPE_AUDIO && tracks.size < 2)) {
            settingsPanel.page("track-$type", title) { settingsPanel.text(it, getString(if (type == C.TRACK_TYPE_AUDIO) R.string.no_audio_choice else R.string.no_subtitle_choice)) }; return
        }
        val labels = listOf(getString(if (type == C.TRACK_TYPE_TEXT) R.string.off else R.string.automatic)) + tracks.map { it.label }
        settingsPanel.choices("track-$type", title, labels) { which ->
            player?.selectTrack(type, tracks.getOrNull(which - 1), type == C.TRACK_TYPE_TEXT && which == 0); settingsPanel.back()
        }
    }
    private fun guideMenu() {
        settingsPanel.page("guide", R.string.programme_guide, leave = { renderGuide = null }) { body ->
            val status = settingsPanel.text(body, "")
            renderGuide = {
                val message = guideMessage?.let { (id, count) -> getString(id, count) }
                    ?: getString(R.string.guide_status, getString(if (epg.sourceUrl() == null) R.string.guide_not_added else R.string.guide_added))
                status.text = message
            }
            renderGuide?.invoke()
            if (repository.playlistEpgUrls().isNotEmpty()) settingsPanel.button(body, getString(R.string.guide_playlist_sources)) { choosePlaylistGuide() }
            settingsPanel.button(body, getString(R.string.guide_link)) { playlistMenus.link(R.string.guide_link) { url -> importGuide(url) { HttpStreams.open(url) } } }
            settingsPanel.button(body, getString(R.string.guide_file)) { launchImportPicker(true) }
            settingsPanel.button(body, getString(R.string.guide_refresh)) {
                if (epg.sourceUrl() == null && repository.playlistEpgUrls().size > 1) choosePlaylistGuide()
                else (epg.sourceUrl() ?: repository.playlistEpgUrl())?.let { url -> importGuide(url) {
                    if (HttpStreams.validUrl(url)) HttpStreams.open(url) else contentResolver.openInputStream(Uri.parse(url)) ?: error("Could not read guide")
                } } ?: notice(getString(R.string.guide_not_added))
            }
            settingsPanel.button(body, getString(R.string.guide_clear)) { if (guideJob?.isActive != true) { epg.clear(); guideMessage = null; osdController.refresh(); renderGuide?.invoke() } }
        }
    }
    private fun choosePlaylistGuide() {
        val urls = repository.playlistEpgUrls()
        settingsPanel.choices("playlist-guides", R.string.guide_playlist_sources, urls.mapIndexed { i, url ->
            getString(R.string.guide_source_number, i + 1) + " · " + runCatching { java.net.URI(url).host }.getOrDefault("")
        }) { index -> val url = urls[index]; importGuide(url) {
            if (HttpStreams.validUrl(url)) HttpStreams.open(url) else contentResolver.openInputStream(Uri.parse(url)) ?: error("Could not read guide")
        } }
    }
    private fun importGuide(url: String? = null, open: () -> InputStream) {
        val ids = channels.mapNotNull { it.tvgId }.toSet()
        if (ids.isEmpty()) { notice(getString(R.string.guide_need_ids)); return }
        if (guideJob?.isActive == true) return
        guideMessage = R.string.guide_loading to 0; renderGuide?.invoke()
        notice(getString(R.string.guide_loading))
        guideJob = lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { epg.import(open(), ids, url) }
                guideMessage = R.string.guide_loaded to count; renderGuide?.invoke()
                osdController.refresh(); notice(getString(R.string.guide_loaded, count))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { guideMessage = R.string.guide_failed to 0; renderGuide?.invoke(); DiagnosticLog.record("guide_import_failed", error); notice(getString(R.string.guide_failed)) }
        }
    }
    private fun showImportError(error: Exception) {
        val causes = generateSequence<Throwable>(error) { it.cause?.takeUnless { cause -> cause === it } }.take(6).toList()
        val reason = when {
            causes.any { it is javax.net.ssl.SSLException || it is java.security.cert.CertificateException } -> R.string.import_error_tls
            causes.any { it is java.net.SocketTimeoutException } -> R.string.import_error_timeout
            causes.any { it is java.net.UnknownHostException || it is java.net.ConnectException } -> R.string.import_error_network
            else -> R.string.import_error_other
        }
        val message = getString(reason) + if (channels.isNotEmpty()) "\n\n" + getString(R.string.list_preserved) else ""
        if (binding.layoutSettings.visibility != View.VISIBLE) toggleSettingsMenu()
        settingsPanel.page("import-error", R.string.import_error_title) { body ->
            settingsPanel.text(body, message)
            settingsPanel.button(body, getString(R.string.diagnostic_log)) { diagnosticMenu.show(repository.source()?.kind, channels.size) }
            settingsPanel.button(body, getString(R.string.back)) { settingsPanel.back() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateMenu.isInitialized) updateMenu.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!::channelNavigation.isInitialized) return
        if (!hasFocus) {
            cancelRowConfirm()
            channelNavigation.cancel()
            return
        }
        if (binding.viewChannelList.root.visibility == View.VISIBLE &&
            (binding.viewChannelList.rvChannels.hasFocus() || !binding.viewChannelList.root.hasFocus())) focusCurrentChannel()
        else if (binding.layoutSettings.visibility == View.VISIBLE && !binding.layoutSettings.hasFocus()) settingsPanel.focusFirst()
    }

    private fun notice(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

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
        welcomeIntro.finish(); crtSound.close(); Anim.finishAll(binding.root)
        playerController?.release()
        playerController = null
        osdController.destroy()
        numberEntryController.cancel()
        handler.removeCallbacksAndMessages(null)
        if (::channelNavigation.isInitialized) channelNavigation.destroy()
        sleepHandler.removeCallbacksAndMessages(null)
    }
}
