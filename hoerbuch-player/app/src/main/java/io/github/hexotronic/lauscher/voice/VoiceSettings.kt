package io.github.hexotronic.lauscher.voice

import android.content.Context
import io.github.hexotronic.lauscher.voice.core.VoiceCommands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which microphone listens for commands. */
enum class VoiceInput(val label: String, val hint: String) {
    AUTO("Automatisch", "Mikrofon am Kabel- oder USB-Kopfhörer, sonst das Handy"),
    PHONE("Handy-Mikrofon", "Auch in der Tasche – dann etwas lauter sprechen"),
    HEADSET("Kabel-/USB-Headset", "Das Mikrofon am Kopfhörerkabel, am nächsten am Mund"),
    BLUETOOTH(
        "Bluetooth-Headset",
        "Achtung: Viele Bluetooth-Kopfhörer spielen dann nur in Telefonqualität, solange zugehört wird",
    ),
}

/** How a recognised command is confirmed. */
enum class VoiceFeedback(val label: String) {
    TONE("Kurzer Ton"),
    SPEECH("Ansage"),
    OFF("Keine"),
}

data class VoiceSettings(
    val enabled: Boolean = false,
    val input: VoiceInput = VoiceInput.AUTO,
    /** Filters the book out of the microphone signal when it plays over the loudspeaker. */
    val echoCancel: Boolean = true,
    /** Only listen while headphones are connected (over the loudspeaker the book itself could trigger commands). */
    val onlyWithHeadphones: Boolean = true,
    /** 0..1: higher hears quieter speech, and more noise. */
    val sensitivity: Float = 0.5f,
    /** The book gets quieter while a command is spoken. */
    val duckWhileSpeaking: Boolean = true,
    val feedback: VoiceFeedback = VoiceFeedback.TONE,
    /** Keeps listening this long while the book is paused (to hear "weiter"). */
    val idleMinutes: Int = 30,
    /** Language of the commands for the speech recogniser. */
    val language: String = "de",
    val commands: VoiceCommands = VoiceCommands(),
)

/** The voice control settings, kept in their own preferences file. */
class VoiceSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<VoiceSettings> = _settings.asStateFlow()
    val current: VoiceSettings get() = _settings.value

    fun update(change: (VoiceSettings) -> VoiceSettings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean("enabled", next.enabled)
            .putString("input", next.input.name)
            .putBoolean("echoCancel", next.echoCancel)
            .putBoolean("onlyWithHeadphones", next.onlyWithHeadphones)
            .putFloat("sensitivity", next.sensitivity)
            .putBoolean("duckWhileSpeaking", next.duckWhileSpeaking)
            .putString("feedback", next.feedback.name)
            .putInt("idleMinutes", next.idleMinutes)
            .putString("language", next.language)
            .putString("commands", next.commands.toJson())
            .apply()
    }

    private fun read(): VoiceSettings {
        val d = VoiceSettings()
        return VoiceSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            input = enumOr(prefs.getString("input", null), d.input),
            echoCancel = prefs.getBoolean("echoCancel", d.echoCancel),
            onlyWithHeadphones = prefs.getBoolean("onlyWithHeadphones", d.onlyWithHeadphones),
            sensitivity = prefs.getFloat("sensitivity", d.sensitivity),
            duckWhileSpeaking = prefs.getBoolean("duckWhileSpeaking", d.duckWhileSpeaking),
            feedback = enumOr(prefs.getString("feedback", null), d.feedback),
            idleMinutes = prefs.getInt("idleMinutes", d.idleMinutes),
            language = prefs.getString("language", null) ?: d.language,
            commands = VoiceCommands.fromJson(prefs.getString("commands", null)),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
