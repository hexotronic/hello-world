package io.github.hexotronic.lauscher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.audio.dsp.EnhancerPreset
import io.github.hexotronic.lauscher.music.MusicMode
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.Setting
import io.github.hexotronic.lauscher.ui.LauscherRoot
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.theme.LauscherTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            LauscherTheme(settings.theme, settings.dynamicColor) {
                LauscherRoot(vm)
            }
        }
        if (savedInstanceState == null) {
            handleIntent(intent)
            if (vm.settings.value.autoRescan) vm.rescan(quiet = true)
        }
        requestNotificationPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.connect()
        // Downloads stopped by the app being closed go on now.
        (application as LauscherApp).downloads.resumeInterrupted()
    }

    override fun onStop() {
        // Releasing the controller lets the service stop when nothing is playing.
        vm.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_DEMO, false)) vm.createDemo(play = true)
        applyMusicExtras(intent)
        intent.getStringExtra(EXTRA_IMPORT_PATH)?.let { vm.importLocalFile(it, intent.getBooleanExtra(EXTRA_ANALYZE, false)) }
        intent.getStringExtra(EXTRA_BOOK_TEXT_PATH)?.let { vm.importBookTextFile(it, intent.getBooleanExtra(EXTRA_ANALYZE, false)) }
        intent.getStringExtra(EXTRA_SPEECH_CHECK)?.let { vm.speechCheck(it) }
        // Test aid: download or convert without tapping through the screen.
        val limit = intent.getStringExtra(EXTRA_DOWNLOAD_LIMIT)?.toIntOrNull() ?: 1
        val original = intent.getStringExtra(EXTRA_DOWNLOAD_FORMAT) == "original"
        val pauseTest = intent.getStringExtra(EXTRA_DOWNLOAD_PAUSE_TEST)?.toLongOrNull()
        intent.getStringExtra(EXTRA_DOWNLOAD_AUDIOBOOK)?.let { vm.downloads.automate("audiobook", it, limit, original, pauseTest) }
        intent.getStringExtra(EXTRA_DOWNLOAD_MEDIA)?.let { vm.downloads.automate("media", it, limit, original) }
        intent.getStringExtra(EXTRA_DOWNLOAD_CONVERT)?.let { vm.downloads.automate("convert", it, limit, original) }
        // "Teilen → Lauscher" from an e-book app: text or a TXT/EPUB/HTML file becomes a book text.
        if (intent.action == Intent.ACTION_SEND) {
            val stream = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            vm.receiveSharedText(intent.getStringExtra(Intent.EXTRA_TEXT), stream)
            return
        }
        intent.getStringExtra(EXTRA_PRESET)?.let { name ->
            EnhancerPreset.entries.firstOrNull { it.name == name }?.let(vm::applyPreset)
        }
        intent.getStringExtra(EXTRA_SCREEN)?.let { screen ->
            vm.openScreen(screen)
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            vm.navigate(Screen.Player)
            return
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val uri: Uri = intent.data ?: return
            vm.addFiles(listOf(uri), play = true)
        }
    }

    /** Optional extras to configure background music, e.g. from adb or a launcher shortcut. */
    private fun applyMusicExtras(intent: Intent) {
        if (intent.hasExtra(EXTRA_MUSIC)) {
            val on = intent.getBooleanExtra(EXTRA_MUSIC, true)
            vm.updateMusic { it.copy(enabled = on, mode = MusicMode.SCENES) }
        }
        intent.getStringExtra(EXTRA_WHISPER_MODEL)?.let { id -> vm.updateMusic { it.copy(whisperModel = id) } }
        intent.getStringExtra(EXTRA_LLM_MODEL)?.let { id -> vm.updateMusic { it.copy(llmModel = id) } }
        intent.getStringExtra(EXTRA_LANGUAGE)?.let { lang -> vm.updateMusic { it.copy(language = lang) } }
        intent.getStringExtra(EXTRA_SPEECH_SPEED)?.toFloatOrNull()?.let { v -> vm.updateMusic { it.copy(speechSpeed = v.coerceIn(1f, 2f)) } }
        intent.getStringExtra(EXTRA_SAMPLE_EVERY)?.toFloatOrNull()?.let { v -> vm.updateMusic { it.copy(sampleEveryMinutes = v.coerceIn(0.25f, 10f)) } }
        intent.getStringExtra(EXTRA_MUSIC_DOWNLOAD)?.let { ids -> vm.downloadMusic(ids.split(',').map { it.trim() }) }
        // "TENSE" or "TENSE@DUNGEON": a fixed mood, optionally at a place.
        intent.getStringExtra(EXTRA_MUSIC_FIXED)?.let { value ->
            val mood = Mood.parse(value.substringBefore('@')) ?: return@let
            val place = Setting.parse(value.substringAfter('@', ""))
            vm.updateMusic {
                it.copy(
                    enabled = true, mode = MusicMode.FIXED, fixedMood = mood, fixedAtmosphere = null, fixedSetting = place,
                    source = MusicSource.LIBRARY,
                )
            }
        }
        intent.getStringExtra(EXTRA_MUSIC_PREVIEW)?.let { id -> MusicCatalog.byId(id)?.let(vm::previewTrack) }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_DEMO = "demo"
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_PRESET = "preset"
        const val EXTRA_MUSIC = "music"
        const val EXTRA_WHISPER_MODEL = "whisper_model"
        const val EXTRA_LLM_MODEL = "llm_model"
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_IMPORT_PATH = "import_path"
        const val EXTRA_ANALYZE = "analyze"
        const val EXTRA_MUSIC_DOWNLOAD = "music_download"
        const val EXTRA_MUSIC_FIXED = "music_fixed"
        const val EXTRA_MUSIC_PREVIEW = "music_preview"
        const val EXTRA_BOOK_TEXT_PATH = "book_text_path"
        const val EXTRA_SPEECH_SPEED = "speech_speed"
        const val EXTRA_SAMPLE_EVERY = "sample_every"
        const val EXTRA_SPEECH_CHECK = "speech_check"
        const val EXTRA_DOWNLOAD_AUDIOBOOK = "download_audiobook"
        const val EXTRA_DOWNLOAD_MEDIA = "download_media"
        const val EXTRA_DOWNLOAD_CONVERT = "download_convert"
        const val EXTRA_DOWNLOAD_LIMIT = "download_limit"
        const val EXTRA_DOWNLOAD_FORMAT = "download_format"
        const val EXTRA_DOWNLOAD_PAUSE_TEST = "download_pause_test"
    }
}
