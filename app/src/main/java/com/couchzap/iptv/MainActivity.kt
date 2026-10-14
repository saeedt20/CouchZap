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
    private var favouritesOnly = false
    private var selectedGroup: String? = null
    private var previousWatchedId: String? = null
    private var guidePicker = false
    private var wrappedKey = KeyEvent.KEYCODE_UNKNOWN
    private var sleepExpired = false
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        sleepExpired = true
        playerController?.pause()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) notice(getString(R.string.timer_done))
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
    private var importInProgress = false
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
                fileBrowser.openAtRoot()
            } else {
                if (channels.isEmpty()) importScreen.show()
                Toast.makeText(this, "Storage permission required for file picker", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLog.initialize(this)
        DiagnosticLog.record("app_start")
        updateMenu = UpdateMenu(this)
        diagnosticMenu = DiagnosticMenu(this, ::notice)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PlaylistRepository(this)
        prefs = Prefs(this)
        epg = EpgRepository(this)
        artwork = ChannelArtwork(lifecycleScope)
        playlistMenus = PlaylistMenus(this, repository, lifecycleScope,
            onFile = { closeSettingsMenu(); launchImportPicker() },
            onSource = { source, refresh -> closeSettingsMenu(); importSource(source, refresh) },
            onNotice = ::notice)
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
            totalChannelCount = { channels.size },
            onCommitChannelNumber = { channelNum ->
                jumpToChannelNumber(channelNum)
            }
        )

        importScreen = ImportFragmentlessScreen(
            binding = binding.viewImport,
            onImportButtonClicked = {
                launchImportPicker()
            },
            onPublicButtonClicked = { playlistMenus.publicChoices() }
        )

        fileBrowser = SimpleFileBrowser(
            container = binding.layoutFileBrowser,
            tvPath = binding.tvFileBrowserPath,
            rvFiles = binding.rvFileBrowser,
            onFileSelected = { file ->
                if (guidePicker) importGuide(Uri.fromFile(file).toString()) { file.inputStream() } else importFile(file)
            },
            onCloseRequested = {
                if (channels.isEmpty()) importScreen.show()
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
            importScreen.hide()
            loadChannelsAndStartPlayback()
        } else {
            importScreen.show()
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
        osdController.resume()
        // The welcome screen does not need codecs, audio focus or a player surface.
        if (channels.isNotEmpty() && !sleepExpired) {
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
                Toast.makeText(this@MainActivity, "Could not read the saved playlist. Please import it again.", Toast.LENGTH_LONG).show()
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
            fileBrowser.openAtRoot()
        }
    }

    private fun importUri(uri: Uri) = importPlaylist(PlaylistSource("file", uri.toString(), uri.lastPathSegment?.substringAfterLast('/') ?: "Playlist file")) {
        contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
    }

    private fun importFile(file: java.io.File) = importPlaylist(PlaylistSource("file", Uri.fromFile(file).toString(), file.name)) { file.inputStream() }

    private fun importSource(source: PlaylistSource, refresh: Boolean) = importPlaylist(source, refresh) {
        if (source.kind == "file") contentResolver.openInputStream(Uri.parse(source.location)) ?: error("The original file is no longer available")
        else HttpStreams.open(source.location)
    }

    private fun importPlaylist(source: PlaylistSource? = null, refresh: Boolean = false, openStream: () -> InputStream) {
        if (importInProgress) return
        if (refresh && channels.isNotEmpty()) repository.saveLastChannelIndex(currentIndex, channels[currentIndex])
        importInProgress = true
        DiagnosticLog.record("playlist_import_start")
        lifecycleScope.launch {
            importScreen.show()
            importScreen.showProgress(getString(if (source?.kind == "file") R.string.loading_channels else R.string.download_playlist, 0))
            try {
                val parsedChannels = withContext(Dispatchers.IO) {
                    repository.importPlaylistFromStream(openStream(), source, refresh)
                }
                DiagnosticLog.record("playlist_import_downloaded")
                if (parsedChannels.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.no_channels_found, Toast.LENGTH_LONG).show()
                } else {
                    handler.removeCallbacksAndMessages(null)
                    numberEntryController.cancel()
                    channels.clear()
                    channels.addAll(parsedChannels)
                    currentIndex = repository.getLastChannelIndex().coerceIn(channels.indices)
                    pendingIndex = currentIndex
                    previousWatchedId = null
                    favouritesOnly = false; selectedGroup = null
                    binding.viewChannelList.etChannelSearch.setText("")
                    updateChannelListAdapter()
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) playCurrentChannel()
                    notice(getString(R.string.loaded_channels, parsedChannels.size))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                DiagnosticLog.record("playlist_import_failed", error)
                showImportError(error)
            } finally {
                importInProgress = false
                importScreen.hideProgress()
                if (channels.isNotEmpty()) importScreen.hide()
                if (channels.isNotEmpty() && !refresh && source != null && source.kind != "file") openChannelListOverlay()
            }
        }
    }

    private fun setupChannelListOverlay() {
        binding.viewChannelList.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.viewChannelList.rvChannels.itemAnimator = null
        val adapter = ChannelListAdapter(artwork = artwork, onFavourite = ::toggleFavourite) { channel ->
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
        val query = binding.viewChannelList.etChannelSearch.text.toString().trim()
        val favourites = repository.favouriteIds()
        val filtered = channels.filter { (!favouritesOnly || it.stableId in favourites) &&
            (selectedGroup == null || it.group.orEmpty() == selectedGroup) &&
            (query.isEmpty() || it.name.contains(query, true) || it.number.toString().startsWith(query)) }
        val playingId = channels.getOrNull(currentIndex)?.stableId
        adapter?.setChannels(filtered, filtered.indexOfFirst { it.stableId == playingId }, favourites)
        binding.viewChannelList.tvEmptyChannels.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.viewChannelList.tvChannelListHeader.text = getString(R.string.channels) + " · " + filtered.size
        binding.viewChannelList.btnFilterAll.isSelected = !favouritesOnly && selectedGroup == null
        binding.viewChannelList.btnFilterFavourites.isSelected = favouritesOnly
        binding.viewChannelList.btnFilterGroups.isSelected = selectedGroup != null
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
        updateChannelListAdapter()
        focusCurrentChannel()
    }

    private fun focusCurrentChannel() {
        val adapter = binding.viewChannelList.rvChannels.adapter as ChannelListAdapter
        focusChannel(channels.getOrNull(currentIndex)?.let { adapter.indexOfId(it.stableId) }?.coerceAtLeast(0) ?: 0)
    }
    private fun focusChannel(position: Int) {
        val list = binding.viewChannelList.rvChannels
        if (list.adapter!!.itemCount == 0) { binding.viewChannelList.etChannelSearch.requestFocus(); return }
        val target = position.coerceIn(0, list.adapter!!.itemCount - 1)
        val holder = list.findViewHolderForAdapterPosition(target)
        if (holder == null) { binding.root.requestFocus(); (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(target, 0) }
        else if (isTvMode) holder.itemView.requestFocusFromTouch() else holder.itemView.requestFocus()
        list.doOnNextLayout {
            if (binding.viewChannelList.root.visibility == View.VISIBLE) {
                val view = list.findViewHolderForAdapterPosition(target)?.itemView
                if (isTvMode) view?.requestFocusFromTouch() else view?.requestFocus()
            }
        }
        list.requestLayout()
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
            playlistMenus.show()
        }

        binding.btnSettingsAspect.setOnClickListener {
            val labels = arrayOf(getString(R.string.aspect_fit), getString(R.string.aspect_fill), "16:9", "4:3")
            AlertDialog.Builder(this).setTitle(R.string.choose_aspect).setSingleChoiceItems(labels, currentAspectIndex) { dialog, which ->
                currentAspectIndex = which; prefs.setAspectRatioMode(aspectRatios[which]); playerController?.setAspectRatioMode(aspectRatios[which]); updateAspectLabel(); dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
        }

        binding.btnSettingsExit.setOnClickListener {
            hardExit()
        }
        binding.btnSettingsTracks.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.audio_subtitles).setItems(arrayOf(getString(R.string.audio), getString(R.string.subtitles))) { _, which ->
                chooseTrack(if (which == 0) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT)
            }.setNegativeButton(R.string.cancel, null).show()
        }
        binding.btnSettingsSleep.setOnClickListener {
            val times = intArrayOf(0, 15, 30, 60, 90)
            val labels = times.map { if (it == 0) getString(R.string.off) else "$it min" }.toTypedArray()
            AlertDialog.Builder(this).setTitle(R.string.sleep_timer).setItems(labels) { _, index ->
                sleepHandler.removeCallbacks(sleepRunnable); sleepExpired = false
                val minutes = times[index]
                if (minutes > 0) sleepHandler.postDelayed(sleepRunnable, minutes * 60000L)
                binding.tvSettingsSleepValue.text = if (minutes == 0) getString(R.string.off) else "$minutes min"
                notice(getString(if (minutes == 0) R.string.timer_off else R.string.timer_minutes, minutes))
            }.setNegativeButton(R.string.cancel, null).show()
        }
        binding.btnSettingsGuide.setOnClickListener { guideMenu() }
        binding.btnSettingsUpdate.setOnClickListener { updateMenu.show() }
        binding.btnSettingsLogs.setOnClickListener { diagnosticMenu.show(repository.source()?.kind, channels.size) }
        binding.btnSettingsAbout.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.about).setMessage(R.string.about_body).setPositiveButton(android.R.string.ok, null).show()
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
        binding.tvSettingsAspectValue.text = modeName
        binding.btnSettingsAspect.contentDescription = getString(R.string.settings_aspect, modeName)
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
            val source = repository.source()
            binding.tvSettingsSource.text = if (source == null) getString(R.string.playlist_not_added) else source.label + "\n" +
                getString(R.string.source_updated, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(repository.updatedAt())))
            if (isTvMode) binding.btnSettingsAspect.requestFocusFromTouch() else binding.btnSettingsAspect.requestFocus()
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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null && ::gestureDetector.isInitialized && gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (key == wrappedKey && event.action == KeyEvent.ACTION_UP) { wrappedKey = KeyEvent.KEYCODE_UNKNOWN; return true }
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
        if (binding.viewChannelList.root.visibility == View.VISIBLE && key in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            val list = binding.viewChannelList.rvChannels
            val row = list.findFocus()?.let { list.findContainingItemView(it) }
            val index = row?.let { list.getChildAdapterPosition(it) } ?: -1
            val count = list.adapter!!.itemCount
            val wraps = count > 0 && ((key == KeyEvent.KEYCODE_DPAD_UP && index == 0) || (key == KeyEvent.KEYCODE_DPAD_DOWN && index == count - 1))
            if (wraps && event.action == KeyEvent.ACTION_DOWN) { wrappedKey = key; focusChannel(if (key == KeyEvent.KEYCODE_DPAD_UP) count - 1 else 0); return true }
        }
        if (binding.viewChannelList.root.visibility == View.VISIBLE && key == KeyEvent.KEYCODE_DPAD_LEFT && binding.viewChannelList.rvChannels.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val filter = when { favouritesOnly -> binding.viewChannelList.btnFilterFavourites; selectedGroup != null -> binding.viewChannelList.btnFilterGroups; else -> binding.viewChannelList.btnFilterAll }
                filter.requestFocusFromTouch()
            }
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

    private fun handleBack() {
        when {
            importInProgress -> Toast.makeText(this, "Finishing playlist import…", Toast.LENGTH_SHORT).show()
            binding.layoutSettings.visibility == View.VISIBLE -> closeSettingsMenu()
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
        binding.tvSettingsModeValue.text = "$mode (${if (isTvMode) "TV" else "Phone"})"
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
            this.width = if (portrait) FrameLayout.LayoutParams.MATCH_PARENT else minOf(dp(420), width)
            gravity = Gravity.LEFT
            setMargins(0, 0, 0, 0)
        }
        binding.viewChannelList.root.setPadding(maxOf(dp(20), tvHorizontalInset), maxOf(dp(20), tvVerticalInset), dp(20), maxOf(dp(20), tvVerticalInset))
        binding.viewOsd.root.layoutParams = (binding.viewOsd.root.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = minOf(dp(400), width - dp(if (isTvMode) 48 else 16))
            gravity = if (portrait) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.TOP or Gravity.START
            setMargins(dp(if (isTvMode) 24 else 8), dp(8), dp(8), dp(16))
        }
        binding.layoutSettings.layoutParams = (binding.layoutSettings.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = minOf(dp(360), width)
            this.height = FrameLayout.LayoutParams.MATCH_PARENT
            gravity = Gravity.LEFT
            setMargins(0, 0, 0, 0)
        }
        binding.layoutSettings.setPadding(maxOf(dp(24), tvHorizontalInset), maxOf(dp(24), tvVerticalInset), dp(24), maxOf(dp(24), tvVerticalInset))
        binding.viewImport.importContent.layoutParams = binding.viewImport.importContent.layoutParams.apply { this.width = minOf(dp(620), width - dp(48)) }
        binding.viewImport.tvImportTitle.textSize = if (isTvMode) 38f else 27f
        binding.viewImport.tvImportRemote.visibility = if (isTvMode) View.VISIBLE else View.GONE
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
        if (currentIndex != pendingIndex) previousWatchedId = channels.getOrNull(currentIndex)?.stableId
        currentIndex = pendingIndex
        sleepExpired = false
        binding.layoutPlaybackError.visibility = View.GONE
        val channel = channels[currentIndex]

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
        if (playerController == null) {
            playerController = PlayerController(this, binding.surfaceView, binding.videoFrame, this, binding.subtitleView)
            playerController?.setAspectRatioMode(prefs.getAspectRatioMode())
        }
        binding.layoutPlaybackError.visibility = View.GONE
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
        Anim.fadeOutShutter(binding.vShutter)
        osdController.hideOsd()
        binding.layoutPlaybackError.visibility = View.VISIBLE
        if (binding.viewChannelList.root.visibility != View.VISIBLE && binding.layoutSettings.visibility != View.VISIBLE) binding.btnRetry.requestFocus()
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
        val player = playerController ?: return
        val tracks = player.choices(type)
        if (tracks.isEmpty() || (type == C.TRACK_TYPE_AUDIO && tracks.size < 2)) {
            notice(getString(if (type == C.TRACK_TYPE_AUDIO) R.string.no_audio_choice else R.string.no_subtitle_choice)); return
        }
        val labels = arrayOf(getString(if (type == C.TRACK_TYPE_TEXT) R.string.off else R.string.automatic)) + tracks.map { it.label }
        AlertDialog.Builder(this).setTitle(if (type == C.TRACK_TYPE_AUDIO) R.string.audio else R.string.subtitles).setItems(labels) { _, which ->
            player.selectTrack(type, tracks.getOrNull(which - 1), type == C.TRACK_TYPE_TEXT && which == 0)
        }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun guideMenu() {
        val labels = arrayOf(getString(R.string.guide_link), getString(R.string.guide_file), getString(R.string.guide_refresh), getString(R.string.guide_clear))
        AlertDialog.Builder(this).setTitle(R.string.programme_guide).setItems(labels) { _, which -> when (which) {
            0 -> playlistMenus.link(R.string.guide_link) { url -> importGuide(url) { HttpStreams.open(url) } }
            1 -> { closeSettingsMenu(); launchImportPicker(true) }
            2 -> (epg.sourceUrl() ?: repository.playlistEpgUrl())?.let { url -> importGuide(url) {
                if (HttpStreams.validUrl(url)) HttpStreams.open(url) else contentResolver.openInputStream(Uri.parse(url)) ?: error("Could not read guide")
            } } ?: notice(getString(R.string.guide_not_added))
            3 -> { epg.clear(); osdController.refresh(); notice(getString(R.string.guide_not_added)) }
        } }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun importGuide(url: String? = null, open: () -> InputStream) {
        val ids = channels.mapNotNull { it.tvgId }.toSet()
        if (ids.isEmpty()) { notice(getString(R.string.guide_need_ids)); return }
        notice(getString(R.string.guide_loading))
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { epg.import(open(), ids, url) }
                osdController.refresh(); notice(getString(R.string.guide_loaded, count))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { DiagnosticLog.record("guide_import_failed", error); notice(getString(R.string.guide_failed)) }
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
        AlertDialog.Builder(this).setTitle(R.string.import_error_title).setMessage(message)
            .setPositiveButton(R.string.diagnostic_log) { _, _ -> diagnosticMenu.show(repository.source()?.kind, channels.size) }
            .setNegativeButton(android.R.string.ok, null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::updateMenu.isInitialized) updateMenu.onResume()
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
        playerController?.release()
        playerController = null
        osdController.destroy()
        numberEntryController.cancel()
        handler.removeCallbacksAndMessages(null)
        sleepHandler.removeCallbacksAndMessages(null)
    }
}
