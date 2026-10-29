package io.github.hexotronic.lauscher

import android.app.Application
import io.github.hexotronic.lauscher.analysis.AnalysisManager
import io.github.hexotronic.lauscher.analysis.ModelManager
import io.github.hexotronic.lauscher.audio.SoundSettingsManager
import io.github.hexotronic.lauscher.data.BackupManager
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.SettingsStore
import io.github.hexotronic.lauscher.music.MusicSettingsStore
import io.github.hexotronic.lauscher.music.library.MusicLibrary
import io.github.hexotronic.lauscher.scan.LibraryScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app-wide singletons (a tiny manual dependency container). */
class LauscherApp : Application() {
    /** Outlives screens and the playback service, used for writes that must not be cancelled. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository by lazy { LibraryRepository(this) }
    val settings by lazy { SettingsStore(this) }
    val sound by lazy { SoundSettingsManager(this, repository, appScope) }
    val scanner by lazy { LibraryScanner(this, repository) }
    val backup by lazy { BackupManager(this, repository, settings, sound) }
    val musicSettings by lazy { MusicSettingsStore(this) }
    val models by lazy { ModelManager(this) }
    val analysis by lazy { AnalysisManager(this, repository, models, musicSettings, appScope) }
    val musicLibrary by lazy { MusicLibrary(this, musicSettings, appScope) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LauscherApp
            private set
    }
}
