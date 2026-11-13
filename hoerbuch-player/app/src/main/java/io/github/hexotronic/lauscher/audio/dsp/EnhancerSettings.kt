package io.github.hexotronic.lauscher.audio.dsp

/**
 * All parameters of the "Klangstudio" processing chain. Immutable: the UI publishes a new
 * instance and the audio thread picks it up at the start of the next block.
 * Strength values are 0..1, frequencies in Hz (0 = off), gains in dB.
 */
data class EnhancerSettings(
    val enabled: Boolean = true,
    val noiseReduction: Float = 0f,
    val freezeNoiseProfile: Boolean = false,
    val rumbleFilterHz: Int = 0,
    val hissFilterHz: Int = 0,
    val declick: Float = 0f,
    val noiseGate: Float = 0f,
    val deEsser: Float = 0f,
    val voiceClarity: Float = 0f,
    val equalizerEnabled: Boolean = false,
    val equalizerGains: List<Float> = List(EQ_FREQUENCIES.size) { 0f },
    val compressor: Float = 0f,
    val autoLevel: Boolean = false,
    val boostDb: Float = 0f,
    val mono: Boolean = false,
    val balance: Float = 0f,
) {
    fun eqGain(band: Int): Float = equalizerGains.getOrElse(band) { 0f }

    companion object {
        val EQ_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val RUMBLE_OPTIONS = intArrayOf(0, 60, 80, 120, 160, 220)
        val HISS_OPTIONS = intArrayOf(0, 12000, 10000, 8000, 6000, 4500)
        const val MAX_BOOST_DB = 15f
        const val EQ_RANGE_DB = 12f

        val OFF = EnhancerSettings(enabled = false)
    }
}

/** Ready-made combinations for typical problem recordings. */
enum class EnhancerPreset(val label: String, val description: String, val settings: EnhancerSettings) {
    NEUTRAL(
        "Neutral", "Originalklang ohne Bearbeitung",
        EnhancerSettings(),
    ),
    SPEECH(
        "Klare Sprache", "Leichte Rauschminderung und mehr Präsenz",
        EnhancerSettings(noiseReduction = 0.3f, rumbleFilterHz = 80, voiceClarity = 0.45f, deEsser = 0.2f),
    ),
    NOISY_RECORDING(
        "Verrauschte Aufnahme", "Starke Rauschunterdrückung für Hintergrundrauschen",
        EnhancerSettings(
            noiseReduction = 0.75f, rumbleFilterHz = 120, hissFilterHz = 10000,
            noiseGate = 0.35f, voiceClarity = 0.3f,
        ),
    ),
    CASSETTE(
        "Altes Tonband", "Gegen Bandrauschen, Brummen und Knacksen",
        EnhancerSettings(
            noiseReduction = 0.65f, rumbleFilterHz = 160, hissFilterHz = 8000,
            declick = 0.5f, noiseGate = 0.25f, voiceClarity = 0.4f,
        ),
    ),
    LOUD_ENVIRONMENT(
        "Laute Umgebung", "Für Auto, Zug und Straße: mehr Lautstärke, weniger Dynamik",
        EnhancerSettings(
            noiseReduction = 0.25f, rumbleFilterHz = 80, voiceClarity = 0.6f,
            compressor = 0.7f, autoLevel = true, boostDb = 6f,
        ),
    ),
    NIGHT(
        "Nachtmodus", "Gleichmäßige, leise Wiedergabe ohne Lautstärkesprünge",
        EnhancerSettings(noiseReduction = 0.3f, compressor = 0.85f, autoLevel = true, deEsser = 0.4f, hissFilterHz = 10000),
    ),
    ONE_EAR(
        "Ein Ohrhörer", "Mono-Mix, damit nichts verloren geht",
        EnhancerSettings(mono = true, voiceClarity = 0.3f),
    ),
}
