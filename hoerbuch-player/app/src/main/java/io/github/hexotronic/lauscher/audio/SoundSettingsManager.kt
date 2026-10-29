package io.github.hexotronic.lauscher.audio

import android.content.Context
import io.github.hexotronic.lauscher.audio.dsp.EnhancerEngine
import io.github.hexotronic.lauscher.audio.dsp.EnhancerSettings
import io.github.hexotronic.lauscher.data.LibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared, in-process link between the UI and the audio thread. The audio processor reads
 * [settings] and [bypass] for every block, the UI reads [engine] for the live spectrum.
 */
object AudioEnhancerHub {
    @Volatile
    var settings: EnhancerSettings = EnhancerSettings()

    @Volatile
    var bypass: Boolean = false

    @Volatile
    var engine: EnhancerEngine? = null

    /** Smoothed level of the narration (dBFS) – lets the background music duck under speech. */
    @Volatile
    var speechLevelDb: Float = -90f
}

data class SoundState(
    val settings: EnhancerSettings,
    /** True when the current book has its own sound settings. */
    val perBook: Boolean,
    val bookId: Long?,
)

/** Loads and persists sound settings globally or per audiobook. */
class SoundSettingsManager(context: Context, private val repo: LibraryRepository, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("sound", Context.MODE_PRIVATE)
    private var global: EnhancerSettings = prefs.getString("global", null)?.let(::settingsFromJson) ?: EnhancerSettings()
    private val _state = MutableStateFlow(SoundState(global, perBook = false, bookId = null))
    val state: StateFlow<SoundState> = _state.asStateFlow()

    init {
        AudioEnhancerHub.settings = global
    }

    /** Called by the player whenever another book is loaded. */
    fun onBookLoaded(bookId: Long, perBookJson: String?) {
        val own = perBookJson?.let(::settingsFromJson)
        publish(SoundState(own ?: global, perBook = own != null, bookId = bookId))
    }

    fun update(transform: (EnhancerSettings) -> EnhancerSettings) {
        val current = _state.value
        val next = transform(current.settings)
        publish(current.copy(settings = next))
        persist(current.copy(settings = next))
    }

    /** Switches between "applies to all books" and "only this book". */
    fun setPerBook(enabled: Boolean) {
        val current = _state.value
        val bookId = current.bookId ?: return
        if (enabled) {
            val next = current.copy(perBook = true)
            publish(next)
            persist(next)
        } else {
            persistJob?.cancel()
            scope.launch { repo.setAudioSettings(bookId, null) }
            publish(current.copy(settings = global, perBook = false))
        }
    }

    fun setBypass(bypass: Boolean) {
        AudioEnhancerHub.bypass = bypass
    }

    private fun publish(state: SoundState) {
        AudioEnhancerHub.settings = state.settings
        _state.value = state
    }

    private var persistJob: Job? = null

    /** Slider drags produce many updates; only the final value is written. */
    private fun persist(state: SoundState) {
        if (!state.perBook || state.bookId == null) global = state.settings
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(400)
            val json = settingsToJson(state.settings).toString()
            if (state.perBook && state.bookId != null) {
                repo.setAudioSettings(state.bookId, json)
            } else {
                prefs.edit().putString("global", json).apply()
            }
        }
    }

    fun globalJson(): String = settingsToJson(global).toString()

    fun restoreGlobal(json: String) {
        val s = settingsFromJson(json) ?: return
        global = s
        prefs.edit().putString("global", json).apply()
        if (!_state.value.perBook) publish(_state.value.copy(settings = s))
    }

    companion object {
        fun settingsToJson(s: EnhancerSettings): JSONObject = JSONObject().apply {
            put("enabled", s.enabled)
            put("noiseReduction", s.noiseReduction.toDouble())
            put("freezeNoiseProfile", s.freezeNoiseProfile)
            put("rumbleFilterHz", s.rumbleFilterHz)
            put("hissFilterHz", s.hissFilterHz)
            put("declick", s.declick.toDouble())
            put("noiseGate", s.noiseGate.toDouble())
            put("deEsser", s.deEsser.toDouble())
            put("voiceClarity", s.voiceClarity.toDouble())
            put("equalizerEnabled", s.equalizerEnabled)
            put("equalizerGains", JSONArray().apply { s.equalizerGains.forEach { put(it.toDouble()) } })
            put("compressor", s.compressor.toDouble())
            put("autoLevel", s.autoLevel)
            put("boostDb", s.boostDb.toDouble())
            put("mono", s.mono)
            put("balance", s.balance.toDouble())
        }

        fun settingsFromJson(text: String): EnhancerSettings? = runCatching {
            val j = JSONObject(text)
            val d = EnhancerSettings()
            val gains = j.optJSONArray("equalizerGains")
            EnhancerSettings(
                enabled = j.optBoolean("enabled", d.enabled),
                noiseReduction = j.optDouble("noiseReduction", 0.0).toFloat(),
                freezeNoiseProfile = j.optBoolean("freezeNoiseProfile", false),
                rumbleFilterHz = j.optInt("rumbleFilterHz", 0),
                hissFilterHz = j.optInt("hissFilterHz", 0),
                declick = j.optDouble("declick", 0.0).toFloat(),
                noiseGate = j.optDouble("noiseGate", 0.0).toFloat(),
                deEsser = j.optDouble("deEsser", 0.0).toFloat(),
                voiceClarity = j.optDouble("voiceClarity", 0.0).toFloat(),
                equalizerEnabled = j.optBoolean("equalizerEnabled", false),
                equalizerGains = List(EnhancerSettings.EQ_FREQUENCIES.size) { i ->
                    gains?.optDouble(i, 0.0)?.toFloat() ?: 0f
                },
                compressor = j.optDouble("compressor", 0.0).toFloat(),
                autoLevel = j.optBoolean("autoLevel", false),
                boostDb = j.optDouble("boostDb", 0.0).toFloat(),
                mono = j.optBoolean("mono", false),
                balance = j.optDouble("balance", 0.0).toFloat(),
            )
        }.getOrNull()
    }
}
