package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.synth.Mood
import kotlin.math.abs

enum class MusicLicense(val label: String, val url: String) {
    GPL2("GNU GPL v2 oder neuer", "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
    CC_BY_SA_3("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/"),
    CC_BY_SA_4("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CC_BY_4("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
}

/** An open-source game whose soundtrack the library draws from. */
enum class MusicCollection(val title: String, val homepage: String, val repository: String, val note: String) {
    WESNOTH(
        "The Battle for Wesnoth",
        "https://www.wesnoth.org",
        "https://github.com/wesnoth/wesnoth",
        "Orchestrale Fantasy-Musik, komponiert 2004–2012 für das Strategiespiel",
    ),
    WIDELANDS(
        "Widelands",
        "https://www.widelands.org",
        "https://github.com/widelands/widelands",
        "Mittelalterliche und folkige Stücke für das Aufbauspiel",
    ),
    FLARE(
        "Flare",
        "https://flarerpg.org",
        "https://github.com/flareteam/flare-game",
        "Soundtrack des Fantasy-Rollenspiels, 2012 aufgenommen",
    ),
}

/**
 * One downloadable piece. All pieces are composed and recorded by people, the files come
 * unchanged from the game's own repository at a fixed version.
 */
data class LibraryTrack(
    val id: String,
    val title: String,
    val composer: String,
    val year: Int?,
    val license: MusicLicense,
    val collection: MusicCollection,
    val url: String,
    val bytes: Long,
    val sha256: String,
    val seconds: Int,
    /** First entry is the mood the piece fits best. */
    val moods: List<Mood>,
    /** 0 = very calm, 1 = very driving. */
    val energy: Float,
    /** Level correction so all pieces sound equally loud. */
    val gainDb: Float,
) {
    val primaryMood: Mood get() = moods.first()
    val credit: String get() = "„$title“ – $composer" + (year?.let { " ($it)" } ?: "") + ", ${collection.title}, ${license.label}"
}

object MusicCatalog {
    val tracks: List<LibraryTrack> get() = FantasyCatalog.tracks

    fun byId(id: String): LibraryTrack? = FantasyCatalog.byId[id]

    /** Pieces that fit [mood], best fitting first. */
    fun forMood(mood: Mood): List<LibraryTrack> =
        tracks.filter { mood in it.moods }.sortedBy { it.moods.indexOf(mood) }

    fun totalBytes(list: List<LibraryTrack>): Long = list.sumOf { it.bytes }
}

/**
 * Chooses the next piece for a mood: prefers pieces made for that mood whose energy matches
 * the scene, and avoids repeating what played recently.
 */
class TrackPicker(seed: Long = System.nanoTime()) {
    private var state = seed xor 0x5DEECE66DL
    private val recent = HashMap<Mood, ArrayDeque<String>>()

    @Synchronized
    fun pick(installed: Collection<LibraryTrack>, mood: Mood, intensity: Float): LibraryTrack? {
        val candidates = installed.filter { mood in it.moods }
        if (candidates.isEmpty()) return null
        val history = recent.getOrPut(mood) { ArrayDeque() }
        val memory = (candidates.size - 1).coerceIn(0, MAX_MEMORY)
        val blocked = history.take(memory).toSet()
        val best = candidates.minBy { t ->
            val rank = t.moods.indexOf(mood) * 0.35f
            val energy = abs(t.energy - intensity.coerceIn(0f, 1f)) * 0.6f
            val repeat = if (t.id in blocked) 10f else 0f
            rank + energy + repeat + nextFloat() * 0.45f
        }
        history.remove(best.id)
        history.addFirst(best.id)
        while (history.size > MAX_MEMORY) history.removeLast()
        return best
    }

    private fun nextFloat(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
    }

    companion object {
        private const val MAX_MEMORY = 4
    }
}
