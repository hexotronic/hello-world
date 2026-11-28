package com.hexotronic.fluxplayer

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.OrientationMode
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.data.UpscaleFactor
import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.player.AudioEffects
import com.hexotronic.fluxplayer.render.RenderStats
import com.hexotronic.fluxplayer.ui.player.FeedbackKind
import com.hexotronic.fluxplayer.ui.player.GestureFeedback
import com.hexotronic.fluxplayer.ui.player.PlayerActions
import com.hexotronic.fluxplayer.ui.player.PlayerDialog
import com.hexotronic.fluxplayer.ui.player.PlayerOverlay
import com.hexotronic.fluxplayer.ui.player.PlayerUiState
import com.hexotronic.fluxplayer.ui.player.TrackOption
import com.hexotronic.fluxplayer.ui.theme.FluxTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val effects = AudioEffects(Prefs(ApplicationProvider.getApplicationContext()))

    private val actions = object : PlayerActions {
        override fun userInteracted() {}
        override fun togglePlay() {}
        override fun seekBy(ms: Long) {}
        override fun scrubStart() {}
        override fun scrub(ms: Long) {}
        override fun scrubEnd(ms: Long) {}
        override fun next() {}
        override fun previous() {}
        override fun close() {}
        override fun toggleLock() {}
        override fun openDialog(dialog: PlayerDialog?) {}
        override fun tracks(type: Int): List<TrackOption> = when (type) {
            C.TRACK_TYPE_AUDIO -> listOf(TrackOption("Deutsch", "AAC · Stereo", true, null), TrackOption("Englisch", "Dolby Digital · 5.1", false, null))
            C.TRACK_TYPE_TEXT -> listOf(TrackOption("Deutsch", "SRT", false, null))
            else -> listOf(TrackOption("1080p", "1920×1080", true, null))
        }
        override fun selectTrack(type: Int, option: TrackOption?) {}
        override fun autoTrack(type: Int) {}
        override fun loadSubtitleFile() {}
        override fun setSubtitleDelay(ms: Long) {}
        override fun setSubtitleScale(scale: Float) {}
        override fun setSpeed(speed: Float) {}
        override fun setFrameGenMode(mode: FrameGenMode) {}
        override fun setFrameGenQuality(quality: FrameGenQuality) {}
        override fun setHighRefresh(enabled: Boolean) {}
        override fun setShowStats(enabled: Boolean) {}
        override fun setSlowMoBoost(enabled: Boolean) {}
        override fun setUpscaler(mode: UpscalerMode) {}
        override fun setSharpness(value: Float) {}
        override fun setUpscaleFactor(factor: UpscaleFactor) {}
        override fun setAspect(mode: AspectMode) {}
        override fun setZoomPercent(percent: Int) {}
        override fun resetZoom() {}
        override fun setAdjustments(values: FloatArray) {}
        override fun audioEffects(): AudioEffects = effects
        override fun setBoost(percent: Int) {}
        override fun setSleepTimer(minutes: Int) {}
        override fun cycleRepeat() {}
        override fun toggleShuffle() {}
        override fun abRepeat() {}
        override fun snapshot() {}
        override fun enterPip() {}
        override fun setOrientation(mode: OrientationMode) {}
        override fun toggleBackground() {}
        override fun setDecoder(pref: DecoderPreference) {}
        override fun playIndex(index: Int) {}
        override fun retry() {}
        override fun chooseCandidate(entry: MediaEntry) {}
        override fun dismissChoices() {}
        override fun mediaInfo(): List<Pair<String, String>> = listOf("Allgemein" to "", "Titel" to "Test")
        override fun toggleRemainingTime() {}
    }

    @Test
    fun everyDialogAndOverlayStateRenders() {
        val ui = PlayerUiState().apply {
            title = "Testvideo"
            duration = 600_000
            position = 120_000
            playlist = List(30) { "Titel $it" }
            stats = RenderStats(23.98f, 120f, 80, "Bewegung (bidirektional)", 1920, 1080, 960, 540, true, "Ultra (Zeitlupe)", true, true, "Kantenadaptiv", 1.33f, 900)
            showStats = true
            subtitleDelaySupported = true
            abStart = 1000
            speed = 0.05f
            slowMoMuted = true
            upscaleInfo = "854×480 → 1708×960 (×2,00)"
            upscaler = UpscalerMode.AI_M
        }
        rule.setContent { FluxTheme { PlayerOverlay(ui, actions) } }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Testvideo").fetchSemanticsNodes().isNotEmpty())

        for (d in PlayerDialog.entries) {
            rule.runOnIdle { ui.dialog = d }
            rule.waitForIdle()
            assertTrue("Dialog $d", rule.onAllNodesWithText("Fertig").fetchSemanticsNodes().isNotEmpty())
        }
        rule.runOnIdle {
            ui.dialog = null
            ui.feedback = GestureFeedback(FeedbackKind.VOLUME, "150 % (Boost)", 1.5f)
            ui.loadingMessage = "Adresse wird analysiert…"
            ui.error = "Testfehler"
            ui.choices = listOf(MediaEntry("https://a/b.m3u8", "HLS-Stream · b.m3u8"))
            ui.isLive = true
            ui.seekable = false
        }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("Testfehler").fetchSemanticsNodes().isNotEmpty())
        rule.runOnIdle {
            ui.choices = null
            ui.locked = true
        }
        rule.waitForIdle()
    }
}
