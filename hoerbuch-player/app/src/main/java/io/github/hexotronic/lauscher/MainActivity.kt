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
    }
}
