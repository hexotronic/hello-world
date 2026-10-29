package io.github.hexotronic.lauscher.music

import android.content.Context
import io.github.hexotronic.lauscher.music.synth.Atmosphere
import io.github.hexotronic.lauscher.music.synth.Mood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MusicMode(val label: String, val hint: String) {
    SCENES("Passend zur Szene", "Die lokale KI liest mit und wählt Musik für jede Szene"),
    CHAPTERS("Kapitel-Intros", "Kurze Musik am Anfang und Ende jedes Kapitels, ohne KI"),
    FIXED("Feste Atmosphäre", "Immer dieselbe Stimmung oder Geräuschkulisse"),
}

enum class AnalysisDepth(val label: String, val hint: String, val sampleMs: Long) {
    TURBO("Turbo", "12 Sekunden je 2 Minuten – etwa zehnmal schneller als vollständig", 12_000),
    SAMPLES("Stichproben", "25 Sekunden je 2 Minuten – guter Kompromiss", 25_000),
    FULL("Vollständig", "Jede Sekunde wird transkribiert – am genauesten, aber langsam", Long.MAX_VALUE),
}

data class MusicSettings(
    val enabled: Boolean = false,
    val mode: MusicMode = MusicMode.SCENES,
    val fixedMood: Mood? = Mood.CALM,
    val fixedAtmosphere: Atmosphere? = null,
    val volume: Float = 0.5f,
    val duckDepthDb: Float = 8f,
    val crossfadeSeconds: Float = 4f,
    val chapterChime: Boolean = false,
    /** Play soft neutral music where no analysis exists yet (instead of silence). */
    val neutralFallback: Boolean = false,
    /** Analyse the upcoming minutes in the background while listening. */
    val analyzeAhead: Boolean = true,
    val depth: AnalysisDepth = AnalysisDepth.SAMPLES,
    /** Language spoken in the audiobooks, for speech recognition. */
    val language: String = "de",
    val whisperModel: String? = null,
    val llmModel: String? = null,
)

class MusicSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("music", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<MusicSettings> = _settings.asStateFlow()
    val current: MusicSettings get() = _settings.value

    fun update(transform: (MusicSettings) -> MusicSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean("enabled", next.enabled)
            .putString("mode", next.mode.name)
            .putString("fixedMood", next.fixedMood?.name)
            .putString("fixedAtmosphere", next.fixedAtmosphere?.name)
            .putFloat("volume", next.volume)
            .putFloat("duckDepthDb", next.duckDepthDb)
            .putFloat("crossfadeSeconds", next.crossfadeSeconds)
            .putBoolean("chapterChime", next.chapterChime)
            .putBoolean("neutralFallback", next.neutralFallback)
            .putBoolean("analyzeAhead", next.analyzeAhead)
            .putString("depth", next.depth.name)
            .putString("language", next.language)
            .putString("whisperModel", next.whisperModel)
            .putString("llmModel", next.llmModel)
            .apply()
    }

    private fun read(): MusicSettings {
        val d = MusicSettings()
        return MusicSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            mode = MusicMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: d.mode,
            fixedMood = if (prefs.contains("fixedMood")) Mood.parse(prefs.getString("fixedMood", null)) else d.fixedMood,
            fixedAtmosphere = Atmosphere.entries.firstOrNull { it.name == prefs.getString("fixedAtmosphere", null) },
            volume = prefs.getFloat("volume", d.volume),
            duckDepthDb = prefs.getFloat("duckDepthDb", d.duckDepthDb),
            crossfadeSeconds = prefs.getFloat("crossfadeSeconds", d.crossfadeSeconds),
            chapterChime = prefs.getBoolean("chapterChime", d.chapterChime),
            neutralFallback = prefs.getBoolean("neutralFallback", d.neutralFallback),
            analyzeAhead = prefs.getBoolean("analyzeAhead", d.analyzeAhead),
            depth = AnalysisDepth.entries.firstOrNull { it.name == prefs.getString("depth", null) } ?: d.depth,
            language = prefs.getString("language", d.language) ?: d.language,
            whisperModel = prefs.getString("whisperModel", null),
            llmModel = prefs.getString("llmModel", null),
        )
    }
}
