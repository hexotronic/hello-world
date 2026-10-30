package io.github.hexotronic.lauscher.music.synth

import io.github.hexotronic.lauscher.music.library.core.MoodSpace
import kotlin.math.abs

/** How fast the music follows a scene change. */
object Transitions {
    /** Short enough to land with the "Suddenly …" of the narration, long enough not to click. */
    const val FAST_FADE_SECONDS = 1.2f

    /** Cross-fade length from [from] to [to]: sudden changes cut fast, everything else uses [base]. */
    fun fadeSeconds(from: SoundSpec?, to: SoundSpec?, base: Float): Float =
        if (isSudden(from, to)) minOf(base, FAST_FADE_SECONDS) else base

    /**
     * Sudden: the scene gets clearly more agitated (calm → tense, tense → action), much more
     * intense, or flips from bright to dark or back (happy → sad, idyllic → eerie). Calming down
     * after action keeps the slow fade.
     */
    fun isSudden(from: SoundSpec?, to: SoundSpec?): Boolean {
        if (from == null || to == null) return false
        val a = from.mood ?: return false
        val b = to.mood ?: return false
        val rise = MoodSpace.arousal(b) - MoodSpace.arousal(a)
        return (rise >= 0.35f && MoodSpace.arousal(b) >= 0.2f) ||
            to.intensity - from.intensity >= 0.45f ||
            abs(MoodSpace.valence(b) - MoodSpace.valence(a)) >= 1.0f
    }
}
