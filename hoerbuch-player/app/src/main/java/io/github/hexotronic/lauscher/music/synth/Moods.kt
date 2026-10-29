package io.github.hexotronic.lauscher.music.synth

/** Musical moods the scene analysis can choose from. Keys are stable (stored in the database). */
enum class Mood(val label: String, val hint: String) {
    CALM("Ruhig", "Gelassene Erzählung, Alltag, Nachdenken"),
    TENSE("Spannend", "Gefahr naht, Verfolgung, Unsicherheit"),
    SAD("Traurig", "Abschied, Verlust, Einsamkeit"),
    HAPPY("Fröhlich", "Freude, Feier, Humor"),
    MYSTERIOUS("Geheimnisvoll", "Rätsel, Magie, Entdeckung"),
    ACTION("Dramatisch", "Kampf, Flucht, Höhepunkt"),
    ROMANTIC("Romantisch", "Liebe, Zärtlichkeit, Nähe"),
    EERIE("Gruselig", "Grauen, Spuk, Dunkelheit"),
    IDYLLIC("Idyllisch", "Natur, Landschaft, Frieden"),
    NEUTRAL("Neutral", "Dezente Begleitung ohne klare Stimmung");

    companion object {
        fun parse(name: String?): Mood? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/** Fixed background atmospheres without music. */
enum class Atmosphere(val label: String) {
    RAIN("Regen"),
    FIRE("Kaminfeuer"),
    FOREST("Wald"),
    SEA("Meeresrauschen"),
    NIGHT("Sommernacht"),
    WIND("Sturm"),
}

/** What the mixer should play: a mood, an atmosphere or both. */
data class SoundSpec(
    val mood: Mood? = null,
    val atmosphere: Atmosphere? = null,
    /** 0..1, more density and brightness when higher. */
    val intensity: Float = 0.5f,
    /** Varies melodies between scenes. */
    val seed: Long = 1L,
)

/**
 * Data-driven description of a mood: chords, rhythm layers and colour.
 * All notes are MIDI numbers.
 */
internal data class MoodStyle(
    val chords: List<IntArray>,
    val chordSeconds: Float,
    val padLevel: Float,
    val padCutoff: Float,
    val padAttack: Float = 2.5f,
    val arpStep: Float = 0f,
    val arpChance: Float = 0f,
    val arpLevel: Float = 0f,
    val arpOctave: Int = 12,
    val arpBrightness: Float = 0.5f,
    val arpUpDown: Boolean = false,
    val bellChancePerSec: Float = 0f,
    val bellLevel: Float = 0f,
    val bellRatio: Float = 3.5f,
    val bellIndex: Float = 2.5f,
    val bellOctave: Int = 24,
    val bellScale: IntArray? = null,
    val melodyStep: Float = 0f,
    val melodyChance: Float = 0f,
    val melodyLevel: Float = 0f,
    val heartbeatBpm: Float = 0f,
    val heartbeatLevel: Float = 0f,
    val ostinatoStep: Float = 0f,
    val ostinatoLevel: Float = 0f,
    val kickBpm: Float = 0f,
    val kickLevel: Float = 0f,
    val shakerStep: Float = 0f,
    val shakerLevel: Float = 0f,
    val drone: IntArray? = null,
    val droneLevel: Float = 0f,
    val shimmer: IntArray? = null,
    val shimmerLevel: Float = 0f,
    val atmosphere: Atmosphere? = null,
    val atmosphereLevel: Float = 0f,
    val reverb: Float = 0.25f,
)

internal object MoodStyles {
    private fun c(vararg n: Int) = n

    fun of(mood: Mood): MoodStyle = when (mood) {
        Mood.CALM -> MoodStyle(
            chords = listOf(c(50, 57, 61, 66), c(55, 59, 62, 66), c(47, 54, 57, 62), c(45, 52, 59, 61)),
            chordSeconds = 8f, padLevel = 0.16f, padCutoff = 1800f,
            arpStep = 0.5f, arpChance = 0.3f, arpLevel = 0.12f, arpBrightness = 0.45f,
            bellChancePerSec = 0.03f, bellLevel = 0.05f, reverb = 0.3f,
        )
        Mood.TENSE -> MoodStyle(
            chords = listOf(c(50, 51, 57), c(50, 53, 56), c(49, 50, 57)),
            chordSeconds = 10f, padLevel = 0.12f, padCutoff = 700f, padAttack = 4f,
            drone = c(38, 45), droneLevel = 0.16f,
            heartbeatBpm = 62f, heartbeatLevel = 0.35f,
            ostinatoStep = 0.34f, ostinatoLevel = 0.1f,
            shimmer = c(86, 87), shimmerLevel = 0.012f, reverb = 0.22f,
        )
        Mood.SAD -> MoodStyle(
            chords = listOf(c(45, 52, 57, 60, 64), c(41, 53, 57, 60, 65), c(48, 55, 60, 64, 67), c(43, 55, 59, 62, 67)),
            chordSeconds = 7f, padLevel = 0.14f, padCutoff = 1400f,
            melodyStep = 1.75f, melodyChance = 0.6f, melodyLevel = 0.09f,
            bellRatio = 1f, bellIndex = 1.2f, reverb = 0.35f,
        )
        Mood.HAPPY -> MoodStyle(
            chords = listOf(c(55, 59, 62, 67), c(50, 57, 62, 66), c(52, 55, 59, 64), c(48, 55, 60, 64)),
            chordSeconds = 2.4f, padLevel = 0.07f, padCutoff = 2600f, padAttack = 0.6f,
            arpStep = 0.3f, arpChance = 0.9f, arpLevel = 0.16f, arpBrightness = 0.8f, arpUpDown = true,
            shakerStep = 0.3f, shakerLevel = 0.025f, reverb = 0.18f,
        )
        Mood.MYSTERIOUS -> MoodStyle(
            chords = listOf(c(52, 56, 58, 62), c(50, 54, 58, 60)),
            chordSeconds = 9f, padLevel = 0.13f, padCutoff = 1100f, padAttack = 4f,
            bellChancePerSec = 0.3f, bellLevel = 0.06f, bellRatio = 3.5f, bellIndex = 2.8f,
            bellScale = c(76, 78, 80, 82, 84, 86, 88), reverb = 0.45f,
        )
        Mood.ACTION -> MoodStyle(
            chords = listOf(c(36, 43, 48, 55), c(32, 39, 44, 51), c(34, 41, 46, 53), c(31, 38, 43, 50)),
            chordSeconds = 1.94f, padLevel = 0.08f, padCutoff = 2400f, padAttack = 0.3f,
            ostinatoStep = 0.121f, ostinatoLevel = 0.14f,
            kickBpm = 124f, kickLevel = 0.4f, reverb = 0.15f,
        )
        Mood.ROMANTIC -> MoodStyle(
            chords = listOf(c(53, 57, 60, 64), c(50, 57, 60, 65), c(46, 53, 57, 62), c(48, 55, 58, 64)),
            chordSeconds = 6f, padLevel = 0.15f, padCutoff = 1600f,
            arpStep = 0.4f, arpChance = 0.7f, arpLevel = 0.1f, arpBrightness = 0.4f,
            melodyStep = 2.4f, melodyChance = 0.35f, melodyLevel = 0.06f, bellRatio = 1f, bellIndex = 1f,
            reverb = 0.35f,
        )
        Mood.EERIE -> MoodStyle(
            chords = listOf(c(37, 43), c(36, 42)),
            chordSeconds = 12f, padLevel = 0.14f, padCutoff = 420f, padAttack = 5f,
            shimmer = c(84, 90), shimmerLevel = 0.018f,
            bellChancePerSec = 0.08f, bellLevel = 0.05f, bellRatio = 2.76f, bellIndex = 4f,
            bellScale = c(61, 62, 67, 68, 73),
            atmosphere = Atmosphere.WIND, atmosphereLevel = 0.5f, reverb = 0.5f,
        )
        Mood.IDYLLIC -> MoodStyle(
            chords = listOf(c(48, 55, 62, 64), c(53, 57, 60, 67)),
            chordSeconds = 10f, padLevel = 0.11f, padCutoff = 1700f,
            arpStep = 0.6f, arpChance = 0.2f, arpLevel = 0.09f, arpBrightness = 0.55f,
            atmosphere = Atmosphere.FOREST, atmosphereLevel = 0.7f, reverb = 0.25f,
        )
        Mood.NEUTRAL -> MoodStyle(
            chords = listOf(c(48, 55, 62, 64), c(45, 52, 60, 64), c(41, 53, 57, 64)),
            chordSeconds = 9f, padLevel = 0.12f, padCutoff = 1300f, padAttack = 3.5f,
            reverb = 0.3f,
        )
    }
}
