package io.github.hexotronic.lauscher.music.synth

/**
 * How closely the music follows the story: whether a piece that is playing continues into the
 * next scene, and how strictly the next piece has to fit.
 */
enum class MusicFollow(val label: String, val hint: String) {
    CALM(
        "Ruhig",
        "Ein Stück läuft weiter, solange es auch zur neuen Szene passt – wenige Wechsel, mehr Zusammenhang. " +
            "Nur ein deutlicher Umschwung (z. B. ruhig → Kampf) bringt ein neues Stück.",
    ),
    BALANCED(
        "Ausgewogen",
        "Ein neues Stück, sobald sich Gefühl, Ort oder Spannung ändern; bei der Wahl zählt auch Abwechslung.",
    ),
    PRECISE(
        "Genau",
        "Folgt jeder Szene: schon kleine Änderungen der Spannung bringen ein neues Stück, und es gewinnt das " +
            "Stück, das am besten passt (Gefühl, Ort, Energie) – Abwechslung zählt weniger.",
    ),
}

/** What the mixer did for a new target, reported for the AI log (on the audio thread). */
data class MusicChoice(val spec: SoundSpec?, val what: What, val trackId: String? = null) {
    enum class What {
        /** A recorded piece of the music library starts for the scene. */
        PIECE,

        /** The playing piece (or synthesizer mood) continues into the new scene. */
        KEPT,

        /** The piece ended; the next one for the same scene starts. */
        NEXT_PIECE,

        /** No fitting recording: the synthesizer plays the mood. */
        SYNTH,

        /** Nothing to play (no scene, or a scene without music). */
        OFF,
    }
}

/** Tension level 1..3 of a scene's [intensity] (see the analysis' scene levels). */
fun tensionLevel(intensity: Float): Int = (Math.round((intensity - 0.2f) / 0.35f) + 1).coerceIn(1, 3)

/** "Spannend · Spannung 2 von 3 · Ort „Wald“" – what a target asks for, for the AI log. */
fun describeScene(spec: SoundSpec): String {
    val parts = ArrayList<String>()
    spec.mood?.let {
        parts += it.label
        parts += "Spannung ${tensionLevel(spec.intensity)} von 3"
    }
    parts += spec.setting?.let { "Ort „${it.label}“" } ?: "Ort unklar"
    spec.atmosphere?.let { parts += "Geräusche „${it.label}“" }
    return parts.joinToString(" · ")
}
