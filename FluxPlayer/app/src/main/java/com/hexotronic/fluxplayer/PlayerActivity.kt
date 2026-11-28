package com.hexotronic.fluxplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Rational
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Format as MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.google.common.util.concurrent.ListenableFuture
import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.data.HistoryStore
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.MediaRepository
import com.hexotronic.fluxplayer.data.OrientationMode
import com.hexotronic.fluxplayer.data.PlaylistHandoff
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.net.StreamResolver
import com.hexotronic.fluxplayer.player.AudioEffects
import com.hexotronic.fluxplayer.player.HeaderStore
import com.hexotronic.fluxplayer.player.PlaybackService
import com.hexotronic.fluxplayer.player.PlayerHolder
import com.hexotronic.fluxplayer.render.FrameGenRenderer
import com.hexotronic.fluxplayer.render.RenderStats
import com.hexotronic.fluxplayer.render.VideoSurfaceView
import com.hexotronic.fluxplayer.render.ZoomState
import com.hexotronic.fluxplayer.ui.player.FeedbackKind
import com.hexotronic.fluxplayer.ui.player.GestureFeedback
import com.hexotronic.fluxplayer.ui.player.PlayerActions
import com.hexotronic.fluxplayer.ui.player.PlayerDialog
import com.hexotronic.fluxplayer.ui.player.PlayerOverlay
import com.hexotronic.fluxplayer.ui.player.PlayerUiState
import com.hexotronic.fluxplayer.ui.player.TrackOption
import com.hexotronic.fluxplayer.ui.theme.FluxTheme
import com.hexotronic.fluxplayer.util.Format
import io.github.anilbeesetti.nextlib.media3ext.renderer.OffsetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(),
    VideoSurfaceView.GestureListener,
    FrameGenRenderer.Listener,
    PlayerActions {

    companion object {
        private const val EXTRA_URL = "flux.url"
        private const val EXTRA_HEADER_KEYS = "flux.hk"
        private const val EXTRA_HEADER_VALUES = "flux.hv"
        private const val EXTRA_HANDOFF = "flux.handoff"
        private const val MIN_SPEED = 0.05f
        private const val MAX_SPEED = 4f
        private const val SLOWMO_AUDIO_LIMIT = 0.1f

        fun playEntries(context: Context, entries: List<MediaEntry>, index: Int) {
            PlaylistHandoff.put(entries, index)
            context.startActivity(Intent(context, PlayerActivity::class.java).putExtra(EXTRA_HANDOFF, true))
        }

        fun playUrl(context: Context, url: String, headers: Map<String, String> = emptyMap()) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .putExtra(EXTRA_URL, url)
                    .putExtra(EXTRA_HEADER_KEYS, headers.keys.toTypedArray())
                    .putExtra(EXTRA_HEADER_VALUES, headers.values.toTypedArray())
            )
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var history: HistoryStore
    private lateinit var player: ExoPlayer
    private lateinit var glView: VideoSurfaceView
    private lateinit var subtitleView: SubtitleView
    private lateinit var audioManager: AudioManager
    private lateinit var effects: AudioEffects
    private val ui = PlayerUiState()
    private val zoom = ZoomState()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentSurface: Surface? = null
    private var hideJob: Job? = null
    private var feedbackJob: Job? = null
    private var resolveJob: Job? = null
    private var lastResolve: Pair<String, Map<String, String>>? = null
    private var lastHistorySave = 0L

    // Gesten-Zustand
    private var swipeIgnored = false
    private var swipeStartPos = 0L
    private var swipeStartBrightness = 0.5f
    private var swipeStartVolume = 0
    private var seekTarget = -1L
    private var lastLiveSeek = 0L
    private var longPressActive = false
    private var audioMutedForSlowMo = false
    private var speedBeforeLongPress = 1f

    // Fehlerbehandlung
    private val triedFfmpegExtractor = HashSet<String>()
    private var triedSoftwareDecoder = false
    private var keepPlayingInBackground = false

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            updateDisplayRate()
        }
    }

    // ============================================================================== Lebenszyklus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        history = HistoryStore(this)
        effects = AudioEffects(prefs)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC
        setupWindow()

        player = PlayerHolder.get(this)

        glView = VideoSurfaceView(this)
        glView.gestureListener = this
        glView.renderer.listener = this
        subtitleView = SubtitleView(this)
        val overlay = ComposeView(this).apply {
            setContent { FluxTheme { PlayerOverlay(ui, this@PlayerActivity) } }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            val mp = ViewGroup.LayoutParams.MATCH_PARENT
            addView(glView, FrameLayout.LayoutParams(mp, mp))
            addView(subtitleView, FrameLayout.LayoutParams(mp, mp))
            addView(overlay, FrameLayout.LayoutParams(mp, mp))
        }
        setContentView(root)
        glView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            zoom.setViewSize(v.width.toFloat(), v.height.toFloat())
            applyZoom()
        }

        loadSettings()
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        effects.attach(player.audioSessionId)
        syncFromPlayer()
        connectSession()
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).registerDisplayListener(displayListener, mainHandler)

        onBackPressedDispatcher.addCallback(this) {
            when {
                ui.dialog != null -> ui.dialog = null
                ui.locked -> showControls()
                else -> close()
            }
        }

        startTicker()
        handleIntent(intent, fromCreate = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val hasContent = intent.data != null || intent.hasExtra(EXTRA_URL) ||
            intent.getBooleanExtra(EXTRA_HANDOFF, false) || intent.action == Intent.ACTION_SEND
        if (hasContent) {
            setIntent(intent)
            saveHistory()
            handleIntent(intent, fromCreate = false)
        }
    }

    override fun onStart() {
        super.onStart()
        glView.onResume()
        keepPlayingInBackground = false
        currentSurface?.let { player.setVideoSurface(it) }
        hideSystemBars()
        updateDisplayRate()
    }

    override fun onStop() {
        super.onStop()
        saveHistory()
        val background = ui.backgroundPlayback && player.isPlaying && !isFinishing
        if (background) {
            keepPlayingInBackground = true
            player.clearVideoSurface()
        } else if (!isChangingConfigurations) {
            player.pause()
        }
        glView.onPause()
    }

    override fun onDestroy() {
        saveHistory()
        player.removeListener(playerListener)
        player.removeAnalyticsListener(analyticsListener)
        val keep = isChangingConfigurations || (keepPlayingInBackground && player.isPlaying)
        currentSurface?.let { player.clearVideoSurface(it) }
        if (!keep) {
            player.stop()
            player.clearMediaItems()
            HeaderStore.headers = emptyMap()
        }
        glView.renderer.releaseSurface()
        if (!keep) effects.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        if (!keep) stopService(Intent(this, PlaybackService::class.java))
        try {
            (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && prefs.autoPip && player.isPlaying && ui.hasVideo) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ui.inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            ui.controlsVisible = false
            ui.dialog = null
            ui.feedback = null
        }
        applySubtitleStyle()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!ui.controlsVisible) {
                    togglePlay(); return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!ui.controlsVisible) {
                seekBy(-ui.seekStepSeconds * 1000L); return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!ui.controlsVisible) {
                seekBy(ui.seekStepSeconds * 1000L); return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> if (!ui.controlsVisible) {
                showControls(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ============================================================================== Einrichtung

    private fun setupWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun loadSettings() {
        ui.frameGenMode = prefs.frameGenMode
        ui.frameGenQuality = prefs.frameGenQuality
        ui.highRefresh = prefs.highRefreshRate
        ui.slowMoBoost = prefs.slowMoBoost
        ui.upscaler = prefs.upscaler
        ui.sharpness = prefs.sharpness
        ui.showStats = prefs.showStats
        ui.backgroundPlayback = prefs.backgroundPlayback
        ui.orientation = prefs.orientation
        ui.decoder = prefs.decoder
        ui.subtitleScale = prefs.subtitleScale
        ui.seekStepSeconds = prefs.seekStepSeconds
        zoom.setAspect(prefs.defaultAspect)
        val r = glView.renderer
        r.mode = ui.frameGenMode
        r.quality = ui.frameGenQuality
        r.statsEnabled = ui.showStats
        r.adjustments = ui.adjustments
        r.slowMoBoost = ui.slowMoBoost
        r.upscaler = ui.upscaler
        r.sharpness = ui.sharpness
        applyHighRefresh(ui.highRefresh)
        applySubtitleStyle()
        applyZoom()
        if (ui.orientation != OrientationMode.AUTO) applyOrientation()
    }

    private fun connectSession() {
        try {
            val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(this, token).buildAsync()
        } catch (_: Exception) {
        }
    }

    private fun currentDisplay(): Display? {
        try {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display
            else @Suppress("DEPRECATION") windowManager.defaultDisplay
        } catch (_: Exception) {
        }
        return try {
            (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        } catch (_: Exception) {
            null
        }
    }

    private fun applyHighRefresh(enabled: Boolean) {
        val d = currentDisplay() ?: return
        val current = d.mode
        val modeId = if (enabled) {
            d.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }?.modeId ?: 0
        } else 0
        window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
        mainHandler.postDelayed({ updateDisplayRate() }, 600)
        updateDisplayRate()
    }

    private fun updateDisplayRate() {
        val hz = currentDisplay()?.refreshRate ?: 60f
        if (hz > 1f) {
            ui.displayHz = hz
            glView.renderer.nominalVsyncNs = (1e9 / hz).toLong()
        }
    }

    // ============================================================================== Intents & Start

    private fun handleIntent(intent: Intent, fromCreate: Boolean) {
        val handoff = if (intent.getBooleanExtra(EXTRA_HANDOFF, false)) PlaylistHandoff.take() else null
        val data = intent.data
        when {
            handoff != null -> startPlayback(handoff.first, handoff.second)
            intent.hasExtra(EXTRA_URL) -> {
                val keys = intent.getStringArrayExtra(EXTRA_HEADER_KEYS) ?: emptyArray()
                val values = intent.getStringArrayExtra(EXTRA_HEADER_VALUES) ?: emptyArray()
                val headers = keys.indices.associate { keys[it] to (values.getOrNull(it) ?: "") }
                resolveAndPlay(intent.getStringExtra(EXTRA_URL) ?: "", headers)
            }
            intent.action == Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) {
                    startPlayback(listOf(MediaEntry(stream.toString(), MediaRepository.displayName(this, stream))), 0)
                } else {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    resolveAndPlay(StreamResolver.extractUrl(text) ?: text.trim(), emptyMap())
                }
            }
            data != null -> {
                val scheme = data.scheme?.lowercase()
                val type = intent.type
                val ext = StreamResolver.extensionOf(data.toString())
                val isWeb = scheme == "http" || scheme == "https"
                if ((isWeb && (type == null || type.startsWith("text/"))) || ext in StreamResolver.PLAYLIST_EXT && !isHlsType(type)) {
                    resolveAndPlay(data.toString(), emptyMap())
                } else {
                    val mime = when {
                        type == null -> StreamResolver.mimeFor(data.toString())
                        type.contains("mpegurl", true) -> MimeTypes.APPLICATION_M3U8
                        type.contains("dash", true) -> MimeTypes.APPLICATION_MPD
                        else -> null
                    }
                    val title = if (isWeb) com.hexotronic.fluxplayer.net.PlaylistParser.nameFromUrl(data.toString())
                    else MediaRepository.displayName(this, data)
                    startPlayback(listOf(MediaEntry(data.toString(), title, mime)), 0)
                }
            }
            player.mediaItemCount > 0 -> restoreFromPlayer()
            fromCreate -> finish()
        }
    }

    private fun isHlsType(type: String?) = type != null && type.contains("mpegurl", true)

    private fun resolveAndPlay(url: String, headers: Map<String, String>) {
        if (url.isBlank()) {
            ui.error = "Keine Adresse übergeben."
            return
        }
        lastResolve = url to headers
        resolveJob?.cancel()
        ui.error = null
        ui.title = url
        ui.loadingMessage = "Adresse wird analysiert…"
        resolveJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                StreamResolver(applicationContext, prefs.userAgent).resolve(url, headers)
            }
            ui.loadingMessage = null
            when (result) {
                is StreamResolver.Result.Play -> startPlayback(result.entries, result.startIndex)
                is StreamResolver.Result.Choose -> {
                    ui.choicesTitle = result.title
                    ui.choices = result.entries
                }
                is StreamResolver.Result.Failed -> ui.error = result.message
            }
        }
    }

    private fun startPlayback(list: List<MediaEntry>, index: Int) {
        if (list.isEmpty()) return
        val start = index.coerceIn(0, list.lastIndex)
        triedFfmpegExtractor.clear()
        triedSoftwareDecoder = false
        ui.error = null
        ui.ended = false
        ui.abStart = null
        ui.abEnd = null
        ui.choices = null
        ui.playlist = list.map { it.title }
        ui.currentIndex = start
        ui.title = list[start].title
        val entry = list[start]
        HeaderStore.headers = entry.headers
        val resume = if (prefs.resumePlayback) resumePositionFor(entry.uri) else 0L
        glView.renderer.markDiscontinuity()
        player.setMediaItems(list.map { PlayerHolder.buildMediaItem(it) }, start, resume)
        player.prepare()
        player.playWhenReady = true
        if (resume > 0) showFeedback(FeedbackKind.INFO, "Fortgesetzt bei ${Format.time(resume)}", autoHideMs = 1800)
        showControls()
    }

    private fun restoreFromPlayer() {
        ui.playlist = List(player.mediaItemCount) { i ->
            player.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "Titel ${i + 1}"
        }
        syncFromPlayer()
        val vs = player.videoSize
        if (vs.width > 0) playerListener.onVideoSizeChanged(vs)
    }

    private fun syncFromPlayer() {
        ui.isPlaying = player.isPlaying
        ui.speed = player.playbackParameters.speed
        glView.renderer.playbackSpeed = ui.speed
        audioMutedForSlowMo = ui.speed < SLOWMO_AUDIO_LIMIT - 1e-4f &&
            player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)
        ui.slowMoMuted = audioMutedForSlowMo
        ui.repeatMode = player.repeatMode
        ui.shuffle = player.shuffleModeEnabled
        ui.currentIndex = player.currentMediaItemIndex
        ui.title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ui.title
        glView.renderer.realtime = player.isPlaying
        glView.setContinuous(player.isPlaying)
        updateSeekable()
    }

    private fun resumePositionFor(uri: String): Long {
        val h = history.get(uri) ?: return 0L
        if (h.position < 5000) return 0L
        if (h.duration > 0 && h.position > h.duration - 8000) return 0L
        return h.position
    }

    private fun saveHistory() {
        if (!::player.isInitialized) return
        val item = player.currentMediaItem ?: return
        val title = item.mediaMetadata.title?.toString() ?: item.mediaId
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val pos = if (ui.ended) 0L else player.currentPosition
        history.save(item.mediaId, title, pos, dur)
        lastHistorySave = System.currentTimeMillis()
    }

    private fun isLocalMedia(): Boolean {
        val s = player.currentMediaItem?.localConfiguration?.uri?.scheme?.lowercase()
        return s == "content" || s == "file" || s == null
    }

    // ============================================================================== Player-Callbacks

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ui.isPlaying = isPlaying
            glView.renderer.realtime = isPlaying
            glView.setContinuous(isPlaying)
            if (isPlaying) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                scheduleHide()
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (!longPressActive) showControls(autoHide = false)
            }
            updatePipParams()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            ui.buffering = playbackState == Player.STATE_BUFFERING
            ui.ended = playbackState == Player.STATE_ENDED
            if (playbackState == Player.STATE_READY) ui.error = null
            if (playbackState == Player.STATE_ENDED) {
                saveHistory()
                if (ui.sleepAtEnd) ui.sleepAtEnd = false
                if (prefs.closeAtEnd) finish() else showControls(autoHide = false)
            }
            updateSeekable()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            val par = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
            glView.renderer.setVideoSize(videoSize.width, videoSize.height)
            updateContentFrameRate()
            zoom.setVideoSize(videoSize.width * par, videoSize.height.toFloat())
            applyZoom()
            ui.videoSize = "${videoSize.width}×${videoSize.height}" +
                if (abs(par - 1f) > 0.01f) String.format(Locale.GERMANY, " (PAR %.2f)", par) else ""
            applyOrientation()
            updatePipParams()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            HeaderStore.headers = PlayerHolder.headersOf(mediaItem)
            ui.title = mediaItem?.mediaMetadata?.title?.toString() ?: ""
            ui.currentIndex = player.currentMediaItemIndex
            ui.abStart = null
            ui.abEnd = null
            ui.decoderName = null
            glView.renderer.markDiscontinuity()
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && ui.sleepAtEnd) {
                player.pause()
                ui.sleepAtEnd = false
                showFeedback(FeedbackKind.INFO, "Sleep-Timer: pausiert", autoHideMs = 2500)
            }
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null && prefs.resumePlayback) {
                val pos = resumePositionFor(mediaItem.mediaId)
                if (pos > 0) player.seekTo(pos)
            }
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            glView.renderer.markDiscontinuity()
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            ui.tracksVersion++
            ui.hasVideo = tracks.isEmpty || tracks.containsType(C.TRACK_TYPE_VIDEO)
            ui.subtitleDelaySupported = textOffsetRenderer() != null
            updateContentFrameRate()
        }

        override fun onCues(cueGroup: CueGroup) {
            subtitleView.setCues(cueGroup.cues)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            effects.attach(audioSessionId)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            ui.repeatMode = repeatMode
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            ui.shuffle = shuffleModeEnabled
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            ui.speed = playbackParameters.speed
            glView.renderer.playbackSpeed = playbackParameters.speed
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            updateSeekable()
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            ui.decoderName = decoderName
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            ui.audioDecoderName = decoderName
        }
    }

    private fun updateSeekable() {
        ui.seekable = player.isCurrentMediaItemSeekable
        ui.isLive = player.isCurrentMediaItemLive
    }

    private fun handleError(e: PlaybackException) {
        val item = player.currentMediaItem
        val index = player.currentMediaItemIndex
        val code = e.errorCode
        if (code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val containerProblem = code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
        if (item != null && containerProblem && !PlayerHolder.usesFfmpeg(item) && triedFfmpegExtractor.add(item.mediaId)) {
            val pos = player.currentPosition
            player.replaceMediaItem(index, PlayerHolder.withFfmpeg(item))
            player.seekTo(index, pos)
            player.prepare()
            player.play()
            showFeedback(FeedbackKind.INFO, "Wechsle auf FFmpeg-Demuxer…", autoHideMs = 2000)
            return
        }
        val decoderProblem = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
        if (decoderProblem && !triedSoftwareDecoder && ui.decoder != DecoderPreference.SOFTWARE) {
            triedSoftwareDecoder = true
            PlayerHolder.setDecoder(DecoderPreference.SOFTWARE)
            val pos = player.currentPosition
            player.prepare()
            player.seekTo(pos)
            player.play()
            showFeedback(FeedbackKind.INFO, "Wechsle auf Software-Decoder (FFmpeg)…", autoHideMs = 2000)
            return
        }
        ui.error = describeError(e)
        showControls(autoHide = false)
    }

    private fun describeError(e: PlaybackException): String {
        val base = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Keine Verbindung zum Server möglich."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Zeitüberschreitung der Verbindung."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "Der Server hat die Anfrage abgelehnt (z. B. 403/404). Der Link ist evtl. abgelaufen oder geschützt."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Datei nicht gefunden."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Keine Berechtigung für diese Datei."
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "Unverschlüsselte Verbindung blockiert."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Dateiformat wird nicht unterstützt."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "Die Datei bzw. der Stream ist beschädigt."
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Codec wird nicht unterstützt."
            PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM-geschützte Inhalte können nicht abgespielt werden."
            else -> "Fehler bei der Wiedergabe."
        }
        val detail = (e.cause?.message ?: e.message)?.take(200)
        return "$base\n(${e.errorCodeName}${if (detail != null) ": $detail" else ""})"
    }

    // ============================================================================== Renderer-Callbacks

    override fun onVideoSurfaceCreated(surface: Surface, previous: Surface?, previousTexture: SurfaceTexture?) {
        mainHandler.post {
            currentSurface = surface
            if (!keepPlayingInBackground) player.setVideoSurface(surface)
            previous?.release()
            previousTexture?.release()
        }
    }

    override fun onRenderStats(stats: RenderStats) {
        mainHandler.post { ui.stats = stats }
    }

    override fun onRendererError(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    // ============================================================================== Ticker

    private fun startTicker() {
        lifecycleScope.launch {
            while (isActive) {
                tick()
                delay(250)
            }
        }
    }

    private fun tick() {
        val pos = player.currentPosition
        if (seekTarget < 0) ui.position = pos
        ui.duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        ui.bufferedPosition = player.bufferedPosition
        val a = ui.abStart
        val b = ui.abEnd
        if (a != null && b != null && pos >= b) player.seekTo(a)
        val sleep = ui.sleepEndsAt
        if (sleep != null && System.currentTimeMillis() >= sleep) {
            ui.sleepEndsAt = null
            player.pause()
            showFeedback(FeedbackKind.INFO, "Sleep-Timer: pausiert", autoHideMs = 2500)
        }
        if (player.isPlaying && System.currentTimeMillis() - lastHistorySave > 5000) saveHistory()
    }

    // ============================================================================== Steuerelemente

    private fun showControls(autoHide: Boolean = true) {
        ui.controlsVisible = true
        if (autoHide) scheduleHide() else hideJob?.cancel()
    }

    private fun hideControls() {
        hideJob?.cancel()
        ui.controlsVisible = false
        hideSystemBars()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = lifecycleScope.launch {
            delay(3500)
            if (player.isPlaying && ui.dialog == null && ui.error == null) {
                ui.controlsVisible = false
                hideSystemBars()
            }
        }
    }

    private fun showFeedback(kind: FeedbackKind, text: String, progress: Float? = null, autoHideMs: Long = 900) {
        feedbackJob?.cancel()
        ui.feedback = GestureFeedback(kind, text, progress)
        if (autoHideMs > 0) {
            feedbackJob = lifecycleScope.launch {
                delay(autoHideMs)
                ui.feedback = null
            }
        }
    }

    private fun hideFeedbackSoon() {
        feedbackJob?.cancel()
        feedbackJob = lifecycleScope.launch {
            delay(450)
            ui.feedback = null
        }
    }

    private fun applyZoom() {
        glView.renderer.displayRect = zoom.normalizedRect()
        ui.zoomPercent = (zoom.scale * 100).roundToInt()
        ui.aspect = zoom.aspect
        glView.requestRender()
    }

    private fun applyOrientation() {
        requestedOrientation = when (ui.orientation) {
            OrientationMode.AUTO -> {
                val vs = player.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    val w = vs.width * (if (vs.pixelWidthHeightRatio > 0) vs.pixelWidthHeightRatio else 1f)
                    if (vs.height > w * 1.05f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun applySubtitleStyle() {
        val scale = ui.subtitleScale * (if (ui.inPip) 0.6f else 1f)
        subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * scale)
        subtitleView.setApplyEmbeddedStyles(true)
        subtitleView.setApplyEmbeddedFontSizes(true)
        subtitleView.setBottomPaddingFraction(0.06f)
        subtitleView.setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                if (prefs.subtitleBackground) 0x99000000.toInt() else Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                null
            )
        )
    }

    private fun pipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val vs = player.videoSize
        if (vs.width > 0 && vs.height > 0) {
            val par = if (vs.pixelWidthHeightRatio > 0) vs.pixelWidthHeightRatio else 1f
            val ratio = (vs.width * par / vs.height).coerceIn(0.42f, 2.38f)
            builder.setAspectRatio(Rational((ratio * 1000).roundToInt(), 1000))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(prefs.autoPip && player.isPlaying && ui.hasVideo)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        try {
            setPictureInPictureParams(pipParams())
        } catch (_: Exception) {
        }
    }

    private fun textOffsetRenderer(): OffsetRenderer? {
        for (i in 0 until player.rendererCount) {
            val r = player.getRenderer(i)
            if (r is OffsetRenderer) return r
        }
        return null
    }

    // ============================================================================== Gesten

    override fun isControlsLocked(): Boolean = ui.locked
    override fun isVideoZoomed(): Boolean = zoom.isZoomed && prefs.gestureZoom

    override fun onSingleTap() {
        if (ui.controlsVisible) hideControls() else showControls()
    }

    override fun onDoubleTap(x: Float, width: Int) {
        if (!prefs.gestureDoubleTap) {
            togglePlay()
            return
        }
        val step = ui.seekStepSeconds * 1000L
        when {
            x < width / 3f && ui.seekable -> seekBy(-step)
            x > width * 2f / 3f && ui.seekable -> seekBy(step)
            else -> togglePlay()
        }
    }

    override fun onLongPressStart() {
        if (!prefs.longPressSpeed || !player.isPlaying) return
        longPressActive = true
        speedBeforeLongPress = player.playbackParameters.speed
        applySpeed(2f)
        showFeedback(FeedbackKind.SPEED, "2x", autoHideMs = 0)
    }

    override fun onLongPressEnd() {
        if (!longPressActive) return
        longPressActive = false
        applySpeed(speedBeforeLongPress)
        hideFeedbackSoon()
    }

    override fun onSwipeStart(kind: VideoSurfaceView.SwipeKind) {
        swipeIgnored = false
        when (kind) {
            VideoSurfaceView.SwipeKind.SEEK -> {
                if (!prefs.gestureSeek || !ui.seekable) {
                    swipeIgnored = true
                    return
                }
                swipeStartPos = player.currentPosition
                seekTarget = swipeStartPos
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
            VideoSurfaceView.SwipeKind.BRIGHTNESS -> {
                if (!prefs.gestureBrightness) swipeIgnored = true
                swipeStartBrightness = currentBrightness()
            }
            VideoSurfaceView.SwipeKind.VOLUME -> {
                if (!prefs.gestureVolume) swipeIgnored = true
                swipeStartVolume = currentVolumePercent()
            }
            VideoSurfaceView.SwipeKind.PAN -> Unit
        }
    }

    override fun onSwipe(kind: VideoSurfaceView.SwipeKind, totalDx: Float, totalDy: Float) {
        if (swipeIgnored) return
        val h = glView.height.coerceAtLeast(1) * 0.75f
        when (kind) {
            VideoSurfaceView.SwipeKind.SEEK -> {
                val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: return
                val range = min(dur, 120_000L).coerceAtLeast(30_000L)
                val delta = (totalDx / glView.width.coerceAtLeast(1) * range).toLong()
                val target = (swipeStartPos + delta).coerceIn(0L, dur)
                seekTarget = target
                ui.position = target
                showFeedback(
                    if (delta >= 0) FeedbackKind.SEEK_FORWARD else FeedbackKind.SEEK_BACK,
                    "${Format.signedTime(target - swipeStartPos)}\n${Format.time(target)} / ${Format.time(dur)}",
                    autoHideMs = 0
                )
                val now = System.currentTimeMillis()
                if (isLocalMedia() && now - lastLiveSeek > 120) {
                    lastLiveSeek = now
                    player.seekTo(target)
                }
            }
            VideoSurfaceView.SwipeKind.BRIGHTNESS -> {
                val v = (swipeStartBrightness - totalDy / h).coerceIn(0.01f, 1f)
                window.attributes = window.attributes.apply { screenBrightness = v }
                showFeedback(FeedbackKind.BRIGHTNESS, "${(v * 100).roundToInt()} %", v, autoHideMs = 0)
            }
            VideoSurfaceView.SwipeKind.VOLUME -> {
                val max = if (prefs.volumeBoost) 200 else 100
                val p = (swipeStartVolume - totalDy / h * 100).roundToInt().coerceIn(0, max)
                setVolumePercent(p)
                val label = if (p > 100) "$p % (Boost)" else "$p %"
                showFeedback(FeedbackKind.VOLUME, label, p / 100f, autoHideMs = 0)
            }
            VideoSurfaceView.SwipeKind.PAN -> Unit
        }
    }

    override fun onSwipeEnd(kind: VideoSurfaceView.SwipeKind) {
        if (kind == VideoSurfaceView.SwipeKind.SEEK && !swipeIgnored && seekTarget >= 0) {
            player.setSeekParameters(SeekParameters.DEFAULT)
            player.seekTo(seekTarget)
        }
        seekTarget = -1
        swipeIgnored = false
        hideFeedbackSoon()
    }

    override fun onPan(dx: Float, dy: Float) {
        if (!prefs.gestureZoom) return
        zoom.panBy(dx, dy)
        applyZoom()
    }

    override fun onScaleStart() {}

    override fun onScale(factor: Float, focusX: Float, focusY: Float) {
        if (!prefs.gestureZoom) return
        zoom.zoomBy(factor, focusX, focusY)
        applyZoom()
        showFeedback(FeedbackKind.ZOOM, "${ui.zoomPercent} %", autoHideMs = 0)
    }

    override fun onScaleEnd() {
        hideFeedbackSoon()
    }

    private fun currentBrightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0f) return b
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            0.5f
        }
    }

    private fun currentVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val base = cur * 100 / max
        return if (base >= 100) 100 + (effects.boostPercent - 100) else base
    }

    private fun setVolumePercent(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = (min(percent, 100) * max / 100f).roundToInt()
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
        } catch (_: SecurityException) {
        }
        setBoost(if (percent > 100) percent else 100)
    }

    // ============================================================================== PlayerActions

    override fun userInteracted() {
        if (player.isPlaying) scheduleHide()
    }

    override fun togglePlay() {
        when {
            ui.error != null -> retry()
            ui.ended || player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        }
        userInteracted()
    }

    override fun seekBy(ms: Long) {
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val target = (player.currentPosition + ms).coerceIn(0L, dur)
        player.seekTo(target)
        showFeedback(
            if (ms >= 0) FeedbackKind.SEEK_FORWARD else FeedbackKind.SEEK_BACK,
            "${if (ms >= 0) "+" else "−"}${abs(ms) / 1000} s"
        )
        userInteracted()
    }

    override fun scrubStart() {
        hideJob?.cancel()
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    override fun scrub(ms: Long) {
        val now = System.currentTimeMillis()
        if (isLocalMedia() && now - lastLiveSeek > 150) {
            lastLiveSeek = now
            player.seekTo(ms)
        }
    }

    override fun scrubEnd(ms: Long) {
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(ms)
        userInteracted()
    }

    override fun next() {
        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        userInteracted()
    }

    override fun previous() {
        if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) player.seekTo(0)
        else player.seekToPreviousMediaItem()
        userInteracted()
    }

    override fun close() {
        saveHistory()
        finish()
    }

    override fun toggleLock() {
        ui.locked = !ui.locked
        if (ui.locked) {
            ui.dialog = null
            showFeedback(FeedbackKind.INFO, "Bedienung gesperrt", autoHideMs = 1200)
        }
        showControls()
    }

    override fun openDialog(dialog: PlayerDialog?) {
        ui.dialog = dialog
        if (dialog != null) hideJob?.cancel() else {
            hideSystemBars()
            userInteracted()
        }
    }

    override fun tracks(type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        for (group in player.currentTracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i, true)) continue
                val f = group.getTrackFormat(i)
                out += TrackOption(
                    trackLabel(f, type, out.size),
                    trackDetail(f, type),
                    group.isTrackSelected(i),
                    group.mediaTrackGroup to i
                )
            }
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    override fun selectTrack(type: Int, option: TrackOption?) {
        val b = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            b.setTrackTypeDisabled(type, true)
        } else {
            val (group, index) = option.key as Pair<TrackGroup, Int>
            b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group, index))
        }
        player.trackSelectionParameters = b.build()
        ui.tracksVersion++
    }

    override fun autoTrack(type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, false)
            .build()
    }

    private fun trackLabel(f: MediaFormat, type: Int, n: Int): String {
        val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale.GERMAN).ifBlank { it }
        }
        return when (type) {
            C.TRACK_TYPE_VIDEO -> if (f.height > 0) "${f.height}p" else "Videospur ${n + 1}"
            else -> listOfNotNull(f.label, lang).distinct().joinToString(" · ").ifEmpty { "Spur ${n + 1}" }
        }
    }

    private fun trackDetail(f: MediaFormat, type: Int): String {
        val parts = ArrayList<String>()
        codecName(f.sampleMimeType ?: f.codecs)?.let { parts += it }
        when (type) {
            C.TRACK_TYPE_AUDIO -> {
                if (f.channelCount > 0) parts += when (f.channelCount) {
                    1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> "${f.channelCount} Kanäle"
                }
                if (f.sampleRate > 0) parts += "${f.sampleRate / 1000} kHz"
            }
            C.TRACK_TYPE_VIDEO -> {
                if (f.width > 0) parts += "${f.width}×${f.height}"
                if (f.frameRate > 0) parts += String.format(Locale.GERMANY, "%.3g fps", f.frameRate)
            }
        }
        if (f.bitrate > 0) parts += "${f.bitrate / 1000} kbit/s"
        return parts.joinToString(" · ")
    }

    private fun codecName(mime: String?): String? = when (mime) {
        null -> null
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "H.265/HEVC"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.TEXT_SSA -> "ASS/SSA"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        else -> mime.substringAfter('/').uppercase()
    }

    override fun loadSubtitleFile() {
        ui.dialog = null
        try {
            subtitlePicker.launch(arrayOf("*/*"))
        } catch (_: Exception) {
            Toast.makeText(this, "Kein Dateimanager verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSubtitle(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val item = player.currentMediaItem ?: return
        val name = MediaRepository.displayName(this, uri)
        val mime = when (name.substringAfterLast('.', "").lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLabel(name)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val existing = item.localConfiguration?.subtitleConfigurations ?: emptyList()
        val index = player.currentMediaItemIndex
        val pos = player.currentPosition
        val wasPlaying = player.playWhenReady
        player.replaceMediaItem(index, item.buildUpon().setSubtitleConfigurations(existing + sub).build())
        player.seekTo(index, pos)
        player.prepare()
        player.playWhenReady = wasPlaying
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        showFeedback(FeedbackKind.INFO, "Untertitel geladen: $name", autoHideMs = 2000)
    }

    override fun setSubtitleDelay(ms: Long) {
        val r = textOffsetRenderer() ?: return
        r.syncOffsetMilliseconds = ms
        ui.subtitleDelayMs = ms
    }

    override fun setSubtitleScale(scale: Float) {
        ui.subtitleScale = scale
        prefs.subtitleScale = scale
        applySubtitleStyle()
    }

    override fun setSpeed(speed: Float) {
        applySpeed(speed)
    }

    /**
     * Die Audio-Ausgabe von ExoPlayer unterstützt mindestens 0,1x. Darunter wird die Tonspur
     * deaktiviert, damit die Video-Uhr frei mit bis zu 0,05x laufen kann.
     */
    private fun applySpeed(speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val mute = s < SLOWMO_AUDIO_LIMIT - 1e-4f
        if (mute != audioMutedForSlowMo) {
            audioMutedForSlowMo = mute
            ui.slowMoMuted = mute
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, mute)
                .build()
            showFeedback(
                FeedbackKind.INFO,
                if (mute) "Unter 0,1x ohne Ton" else "Ton wieder an",
                autoHideMs = 1500
            )
        }
        player.setPlaybackSpeed(s)
        glView.renderer.playbackSpeed = s
    }

    private fun updateContentFrameRate() {
        val fps = player.videoFormat?.frameRate ?: -1f
        glView.renderer.contentFrameRate = if (fps > 0f) fps else 0f
    }

    override fun setSlowMoBoost(enabled: Boolean) {
        ui.slowMoBoost = enabled
        prefs.slowMoBoost = enabled
        glView.renderer.slowMoBoost = enabled
    }

    override fun setUpscaler(mode: UpscalerMode) {
        ui.upscaler = mode
        prefs.upscaler = mode
        glView.renderer.upscaler = mode
        glView.requestRender()
    }

    override fun setSharpness(value: Float) {
        ui.sharpness = value
        prefs.sharpness = value
        glView.renderer.sharpness = value
        glView.requestRender()
    }

    override fun setFrameGenMode(mode: FrameGenMode) {
        ui.frameGenMode = mode
        prefs.frameGenMode = mode
        glView.renderer.mode = mode
    }

    override fun setFrameGenQuality(quality: FrameGenQuality) {
        ui.frameGenQuality = quality
        prefs.frameGenQuality = quality
        glView.renderer.quality = quality
    }

    override fun setHighRefresh(enabled: Boolean) {
        ui.highRefresh = enabled
        prefs.highRefreshRate = enabled
        applyHighRefresh(enabled)
    }

    override fun setShowStats(enabled: Boolean) {
        ui.showStats = enabled
        prefs.showStats = enabled
        glView.renderer.statsEnabled = enabled
    }

    override fun setAspect(mode: AspectMode) {
        zoom.setAspect(mode)
        applyZoom()
    }

    override fun setZoomPercent(percent: Int) {
        zoom.setScale(percent / 100f)
        applyZoom()
    }

    override fun resetZoom() {
        zoom.reset()
        applyZoom()
    }

    override fun setAdjustments(values: FloatArray) {
        ui.adjustments = values
        glView.renderer.adjustments = values
        glView.requestRender()
    }

    override fun audioEffects(): AudioEffects = effects

    override fun setBoost(percent: Int) {
        effects.setBoost(percent)
        ui.boostPercent = effects.boostPercent
    }

    override fun setSleepTimer(minutes: Int) {
        when {
            minutes == 0 -> {
                ui.sleepEndsAt = null
                ui.sleepAtEnd = false
                showFeedback(FeedbackKind.INFO, "Sleep-Timer aus")
            }
            minutes < 0 -> {
                ui.sleepEndsAt = null
                ui.sleepAtEnd = true
                showFeedback(FeedbackKind.INFO, "Pause am Ende des Videos", autoHideMs = 1500)
            }
            else -> {
                ui.sleepAtEnd = false
                ui.sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
                showFeedback(FeedbackKind.INFO, "Sleep-Timer: $minutes Min.", autoHideMs = 1500)
            }
        }
    }

    override fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    override fun abRepeat() {
        val pos = player.currentPosition
        when {
            ui.abStart == null -> {
                ui.abStart = pos
                showFeedback(FeedbackKind.INFO, "Punkt A: ${Format.time(pos)}", autoHideMs = 1500)
            }
            ui.abEnd == null -> {
                val a = ui.abStart ?: 0L
                if (pos > a + 500) {
                    ui.abEnd = pos
                    player.seekTo(a)
                    showFeedback(FeedbackKind.INFO, "A-B: ${Format.time(a)} – ${Format.time(pos)}", autoHideMs = 1500)
                } else {
                    showFeedback(FeedbackKind.INFO, "B muss nach A liegen", autoHideMs = 1500)
                }
            }
            else -> {
                ui.abStart = null
                ui.abEnd = null
                showFeedback(FeedbackKind.INFO, "A-B-Wiederholung aus")
            }
        }
    }

    override fun snapshot() {
        glView.renderer.requestSnapshot { bmp ->
            mainHandler.post {
                if (bmp == null) {
                    Toast.makeText(this, "Kein Bild verfügbar", Toast.LENGTH_SHORT).show()
                } else {
                    saveSnapshot(bmp)
                }
            }
        }
    }

    private fun saveSnapshot(raw: Bitmap) {
        val vs = player.videoSize
        val par = if (vs.pixelWidthHeightRatio > 0) vs.pixelWidthHeightRatio else 1f
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val bmp = if (abs(par - 1f) > 0.01f) {
                        Bitmap.createScaledBitmap(raw, (raw.width * par).roundToInt(), raw.height, true)
                    } else raw
                    val name = "FluxPlayer_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".png"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FluxPlayer")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            ?: return@withContext null
                        contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                        "Bilder/FluxPlayer/$name"
                    } else {
                        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return@withContext null
                        val file = java.io.File(dir, name)
                        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        file.absolutePath
                    }
                } catch (e: Exception) {
                    null
                }
            }
            Toast.makeText(
                this@PlayerActivity,
                if (result != null) "Schnappschuss gespeichert: $result" else "Speichern fehlgeschlagen",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun enterPip() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(this, "Bild-in-Bild wird nicht unterstützt", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            enterPictureInPictureMode(pipParams())
        } catch (e: Exception) {
            Toast.makeText(this, "Bild-in-Bild nicht möglich", Toast.LENGTH_SHORT).show()
        }
    }

    override fun setOrientation(mode: OrientationMode) {
        ui.orientation = mode
        prefs.orientation = mode
        applyOrientation()
    }

    override fun toggleBackground() {
        val enabled = !ui.backgroundPlayback
        ui.backgroundPlayback = enabled
        prefs.backgroundPlayback = enabled
        if (enabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        showFeedback(FeedbackKind.INFO, if (enabled) "Hintergrundwiedergabe an" else "Hintergrundwiedergabe aus", autoHideMs = 1500)
    }

    override fun setDecoder(pref: DecoderPreference) {
        ui.decoder = pref
        prefs.decoder = pref
        PlayerHolder.setDecoder(pref)
        if (player.mediaItemCount > 0) {
            val pos = player.currentPosition
            val playing = player.playWhenReady
            player.stop()
            player.prepare()
            player.seekTo(pos)
            player.playWhenReady = playing
        }
    }

    override fun playIndex(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        val id = player.getMediaItemAt(index).mediaId
        player.seekTo(index, if (prefs.resumePlayback) resumePositionFor(id) else 0L)
        player.play()
    }

    override fun retry() {
        ui.error = null
        if (player.mediaItemCount == 0) {
            lastResolve?.let { (u, h) -> resolveAndPlay(u, h) }
            return
        }
        triedFfmpegExtractor.clear()
        triedSoftwareDecoder = false
        player.prepare()
        player.play()
    }

    override fun chooseCandidate(entry: MediaEntry) {
        ui.choices = null
        startPlayback(listOf(entry), 0)
    }

    override fun dismissChoices() {
        ui.choices = null
        if (player.mediaItemCount == 0) finish()
    }

    override fun mediaInfo(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val item = player.currentMediaItem
        out += "Allgemein" to ""
        out += "Titel" to (item?.mediaMetadata?.title?.toString() ?: "–")
        out += "Adresse" to (item?.mediaId ?: "–")
        out += "Dauer" to if (ui.duration > 0) Format.time(ui.duration) else if (ui.isLive) "Live" else "–"
        player.videoFormat?.let { f ->
            out += "Video" to ""
            out += "Auflösung" to ui.videoSize
            out += "Codec" to (codecName(f.sampleMimeType) ?: "–") + (f.codecs?.let { " ($it)" } ?: "")
            if (f.frameRate > 0) out += "Bildrate" to String.format(Locale.GERMANY, "%.3f fps", f.frameRate)
            if (f.bitrate > 0) out += "Bitrate" to "${f.bitrate / 1000} kbit/s"
            out += "Decoder" to (ui.decoderName ?: "–")
        }
        player.audioFormat?.let { f ->
            out += "Audio" to ""
            out += "Codec" to (codecName(f.sampleMimeType) ?: "–")
            if (f.channelCount > 0) out += "Kanäle" to f.channelCount.toString()
            if (f.sampleRate > 0) out += "Abtastrate" to "${f.sampleRate} Hz"
            f.language?.let { out += "Sprache" to it }
            out += "Decoder" to (ui.audioDecoderName ?: "–")
        }
        out += "Frame-Generierung" to ""
        out += "Modus" to ui.frameGenMode.label + " · " + ui.frameGenQuality.label
        out += "Display" to String.format(Locale.GERMANY, "%.0f Hz", ui.displayHz)
        out += "Upscaler" to ui.upscaler.label + String.format(Locale.GERMANY, " · Schärfe %d %%", (ui.sharpness * 100).roundToInt())
        ui.stats?.let { s -> out += "Aktive Stufe" to s.qualityLabel + if (s.boosted) " (Zeitlupen-Boost)" else "" }
        ui.stats?.let { s ->
            out += "Quelle" to String.format(Locale.GERMANY, "%.2f fps", s.sourceFps)
            out += "Ausgabe" to String.format(Locale.GERMANY, "%.1f fps (%d %% generiert)", s.outputFps, s.generatedPercent)
        }
        return out
    }

    override fun toggleRemainingTime() {
        ui.showRemaining = !ui.showRemaining
    }
}
