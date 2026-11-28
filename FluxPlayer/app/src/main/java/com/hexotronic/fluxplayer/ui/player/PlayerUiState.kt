package com.hexotronic.fluxplayer.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.OrientationMode
import com.hexotronic.fluxplayer.data.UpscaleFactor
import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.player.AudioEffects
import com.hexotronic.fluxplayer.render.RenderStats

enum class PlayerDialog {
    MENU, AUDIO, SUBTITLES, VIDEO_QUALITY, SPEED, FRAME_GEN, UPSCALER, ZOOM, ADJUST, EQUALIZER,
    SLEEP, PLAYLIST, INFO, DECODER, ORIENTATION
}

enum class FeedbackKind { SEEK_FORWARD, SEEK_BACK, BRIGHTNESS, VOLUME, ZOOM, SPEED, INFO }

data class GestureFeedback(val kind: FeedbackKind, val text: String, val progress: Float? = null)

/** Eine wählbare Spur. [key] ist für die Activity bestimmt (TrackGroup + Index). */
data class TrackOption(val label: String, val detail: String, val selected: Boolean, val key: Any?)

/** Beobachtbarer Zustand des Player-Bildschirms (Compose). */
class PlayerUiState {
    var title by mutableStateOf("")
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var bufferedPosition by mutableLongStateOf(0L)
    var seekable by mutableStateOf(true)
    var isLive by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var feedback by mutableStateOf<GestureFeedback?>(null)
    var speed by mutableFloatStateOf(1f)
    var repeatMode by mutableIntStateOf(0)
    var shuffle by mutableStateOf(false)
    var aspect by mutableStateOf(AspectMode.FIT)
    var zoomPercent by mutableIntStateOf(100)
    var frameGenMode by mutableStateOf(FrameGenMode.MOTION)
    var frameGenQuality by mutableStateOf(FrameGenQuality.BALANCED)
    var highRefresh by mutableStateOf(true)
    var slowMoBoost by mutableStateOf(true)
    var upscaler by mutableStateOf(UpscalerMode.EDGE)
    var sharpness by mutableFloatStateOf(0.3f)
    var upscaleFactor by mutableStateOf(UpscaleFactor.AUTO)
    var upscalerEnabled by mutableStateOf(true)
    var upscalerAuto by mutableStateOf(true)
    var upscaleInfo by mutableStateOf("")
    var slowMoMuted by mutableStateOf(false)
    var displayHz by mutableFloatStateOf(60f)
    var stats by mutableStateOf<RenderStats?>(null)
    var showStats by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loadingMessage by mutableStateOf<String?>(null)
    var choices by mutableStateOf<List<MediaEntry>?>(null)
    var choicesTitle by mutableStateOf<String?>(null)
    var dialog by mutableStateOf<PlayerDialog?>(null)
    var abStart by mutableStateOf<Long?>(null)
    var abEnd by mutableStateOf<Long?>(null)
    var sleepEndsAt by mutableStateOf<Long?>(null)
    var sleepAtEnd by mutableStateOf(false)
    var inPip by mutableStateOf(false)
    var playlist by mutableStateOf<List<String>>(emptyList())
    var currentIndex by mutableIntStateOf(0)
    var decoderName by mutableStateOf<String?>(null)
    var audioDecoderName by mutableStateOf<String?>(null)
    var decoder by mutableStateOf(DecoderPreference.AUTO)
    var tracksVersion by mutableIntStateOf(0)
    var showRemaining by mutableStateOf(false)
    var subtitleDelayMs by mutableLongStateOf(0L)
    var subtitleDelaySupported by mutableStateOf(false)
    var subtitleScale by mutableFloatStateOf(1f)
    var backgroundPlayback by mutableStateOf(false)
    var orientation by mutableStateOf(OrientationMode.AUTO)
    var adjustments by mutableStateOf(floatArrayOf(0f, 1f, 1f, 1f))
    var boostPercent by mutableIntStateOf(100)
    var videoSize by mutableStateOf("")
    var seekStepSeconds by mutableIntStateOf(10)
    var hasVideo by mutableStateOf(true)
}

/** Aktionen, die das Overlay auslösen kann (implementiert von der PlayerActivity). */
interface PlayerActions {
    fun userInteracted()
    fun togglePlay()
    fun seekBy(ms: Long)
    fun scrubStart()
    fun scrub(ms: Long)
    fun scrubEnd(ms: Long)
    fun next()
    fun previous()
    fun close()
    fun toggleLock()
    fun openDialog(dialog: PlayerDialog?)

    fun tracks(type: Int): List<TrackOption>
    fun selectTrack(type: Int, option: TrackOption?)
    fun autoTrack(type: Int)
    fun loadSubtitleFile()
    fun setSubtitleDelay(ms: Long)
    fun setSubtitleScale(scale: Float)

    fun setSpeed(speed: Float)
    fun setFrameGenMode(mode: FrameGenMode)
    fun setFrameGenQuality(quality: FrameGenQuality)
    fun setHighRefresh(enabled: Boolean)
    fun setShowStats(enabled: Boolean)
    fun setSlowMoBoost(enabled: Boolean)
    fun setUpscaler(mode: UpscalerMode)
    fun setSharpness(value: Float)
    fun setUpscaleFactor(factor: UpscaleFactor)
    fun setUpscalerEnabled(enabled: Boolean)
    fun setUpscalerAuto(auto: Boolean)

    fun setAspect(mode: AspectMode)
    fun setZoomPercent(percent: Int)
    fun resetZoom()
    fun setAdjustments(values: FloatArray)

    fun audioEffects(): AudioEffects
    fun setBoost(percent: Int)

    fun setSleepTimer(minutes: Int)
    fun cycleRepeat()
    fun toggleShuffle()
    fun abRepeat()
    fun snapshot()
    fun enterPip()
    fun setOrientation(mode: OrientationMode)
    fun toggleBackground()
    fun setDecoder(pref: DecoderPreference)
    fun playIndex(index: Int)
    fun retry()
    fun chooseCandidate(entry: MediaEntry)
    fun dismissChoices()
    fun mediaInfo(): List<Pair<String, String>>
    fun toggleRemainingTime()
}
