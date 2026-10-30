package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.Setting
import kotlin.math.abs
import kotlin.math.sqrt

enum class MusicLicense(val label: String, val url: String) {
    GPL2("GNU GPL v2 oder neuer", "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
    GPL("GNU GPL v2", "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
    GPL3("GNU GPL v3", "https://www.gnu.org/licenses/gpl-3.0.html"),
    GPL2_CC_BY_SA("CC BY-SA / GNU GPL v2+", "https://creativecommons.org/licenses/by-sa/3.0/"),
    CC_BY_SA("CC BY-SA", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CC_BY_SA_3("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/"),
    CC_BY_SA_4("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CC_BY_3("CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/"),
    CC_BY_4("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    OGA_BY_3("OGA-BY 3.0", "https://static.opengameart.org/OGA-BY-3.0.txt"),
    CC0("CC0 (gemeinfrei)", "https://creativecommons.org/publicdomain/zero/1.0/"),
    GFDL("GNU FDL 1.2", "https://www.gnu.org/licenses/old-licenses/fdl-1.2.html"),
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
    VELOREN(
        "Veloren",
        "https://veloren.net",
        "https://github.com/veloren/veloren",
        "Landschaften, Siedlungen, Höhlen und Verliese, Kampfmusik in drei Teilen; KI-Inhalte sind dort verboten",
    ),
    SHATTERED_PD(
        "Shattered Pixel Dungeon",
        "https://shatteredpixel.com",
        "https://github.com/00-Evan/shattered-pixel-dungeon",
        "Kerker, Höhlen und Dämonenhallen – je Gebiet ruhig, angespannt und Bosskampf",
    ),
    SUPERTUX(
        "SuperTux",
        "https://www.supertux.org",
        "https://github.com/SuperTux/supertux",
        "Wald, Geisterwald, Eis, Höhlen, Festungen und Unterwasserwelten",
    ),
    STENDHAL(
        "Stendhal",
        "https://stendhalgame.org",
        "https://github.com/arianne/stendhal",
        "Mittelalterliches Rollenspiel: Taverne, Markt, Kirche, Kerker, Mine, Spukwald",
    ),
    ZERO_AD(
        "0 A.D. (Wildfire Games)",
        "https://play0ad.com",
        "https://github.com/0ad/0ad",
        "Antike Völker: Griechen, Römer, Kelten, Ägypten, Persien, Indien, China",
    ),
    PIONEER(
        "Pioneer",
        "https://pioneerspacesim.net",
        "https://github.com/pioneerspacesim/pioneer",
        "Weltraum-Musik: Sternenflug, Raumstation, Begegnung und Gefecht",
    ),
    HEDGEWARS(
        "Hedgewars",
        "https://hedgewars.org",
        "https://github.com/hedgewars/hw",
        "Themenmusik: Orient, Piraten, Schnee, Dschungel, Stadt, Weltraum",
    ),
}

/** One audio file of a piece; most pieces have exactly one. */
data class TrackFile(val url: String, val bytes: Long, val sha256: String)

/**
 * A stretch of one of the piece's files, played [repeat] times. [toSec] < 0 means "to the end".
 * Loops of game music are played a few times so short pieces still carry a scene.
 */
data class Segment(val file: Int = 0, val fromSec: Float = 0f, val toSec: Float = -1f, val repeat: Int = 1)

object PlayPlan {
    /** The plan as single steps in playing order (repeats unrolled). */
    fun expand(plan: List<Segment>): List<Segment> =
        plan.flatMap { s -> List(s.repeat.coerceAtLeast(1)) { s.copy(repeat = 1) } }
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
    val files: List<TrackFile>,
    /** Length as played (including loops). */
    val seconds: Int,
    /** First entry is the mood the piece fits best. */
    val moods: List<Mood>,
    /** Places the piece was written for, best first; empty = fits anywhere. */
    val settings: List<Setting>,
    /** 0 = very calm, 1 = very driving. */
    val energy: Float,
    /** Level correction so all pieces sound equally loud. */
    val gainDb: Float,
    val plan: List<Segment> = listOf(Segment()),
) {
    val primaryMood: Mood get() = moods.first()
    val bytes: Long get() = files.sumOf { it.bytes }
    val credit: String get() = "„$title“ – $composer" + (year?.let { " ($it)" } ?: "") + ", ${collection.title}, ${license.label}"
}

object MusicCatalog {
    val tracks: List<LibraryTrack> get() = FantasyCatalog.tracks

    fun byId(id: String): LibraryTrack? = FantasyCatalog.byId[id]

    /** Pieces that fit [mood], best fitting first. */
    fun forMood(mood: Mood): List<LibraryTrack> =
        tracks.filter { mood in it.moods }.sortedBy { it.moods.indexOf(mood) }

    /** Pieces written for [setting], best fitting first. */
    fun forSetting(setting: Setting): List<LibraryTrack> =
        tracks.filter { setting in it.settings }.sortedBy { it.settings.indexOf(setting) }

    fun totalBytes(list: List<LibraryTrack>): Long = list.sumOf { it.bytes }

    /**
     * A small start: for every place and every mood the two pieces that fit best, so each
     * scene finds something fitting without downloading everything.
     */
    val starterPack: List<LibraryTrack> by lazy {
        val picked = LinkedHashSet<LibraryTrack>()
        for (s in Setting.entries) {
            forSetting(s).sortedBy { it.settings.indexOf(s) * 10 + it.moods.size }.distinctBy { it.primaryMood }.take(2).forEach(picked::add)
        }
        for (m in Mood.entries) forMood(m).filter { it.primaryMood == m }.take(2).forEach(picked::add)
        picked.toList()
    }
}

/**
 * Chooses the next piece for a scene: first the mood, then the place and the tension; pieces
 * that played recently are avoided.
 */
class TrackPicker(seed: Long = System.nanoTime()) {
    private var state = seed xor 0x5DEECE66DL
    private val recent = HashMap<String, ArrayDeque<String>>()
    private val lastPlayed = ArrayDeque<String>()

    @Synchronized
    fun pick(installed: Collection<LibraryTrack>, mood: Mood, setting: Setting?, intensity: Float): LibraryTrack? {
        if (installed.isEmpty()) return null
        // Only reasonably fitting pieces: the mood itself or a close one, and mood and place together
        // not too far off. Otherwise the synthesizer is the better choice.
        val candidates = installed.filter { moodCost(it, mood) < MAX_MOOD_COST && moodCost(it, mood) + placeCost(it, setting) < MAX_FIT }
        if (candidates.isEmpty()) return null
        val history = recent.getOrPut("$mood/$setting") { ArrayDeque() }
        val fitting = candidates.count { mood in it.moods && placeCost(it, setting) < 0.3f }
        val memory = (fitting - 1).coerceIn(0, MAX_MEMORY)
        val blocked = history.take(memory).toSet()
        val best = candidates.minBy { t ->
            val energy = abs(t.energy - intensity.coerceIn(0f, 1f)) * 0.8f
            val repeat = when {
                t.id in blocked -> 10f
                t.id in lastPlayed -> 0.4f
                else -> 0f
            }
            moodCost(t, mood) + placeCost(t, setting) + energy + repeat + nextFloat() * 0.3f
        }
        history.remove(best.id)
        history.addFirst(best.id)
        while (history.size > MAX_MEMORY) history.removeLast()
        lastPlayed.remove(best.id)
        lastPlayed.addFirst(best.id)
        while (lastPlayed.size > 8) lastPlayed.removeLast()
        return best
    }

    private fun nextFloat(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
    }

    companion object {
        private const val MAX_MEMORY = 4
        private const val MAX_MOOD_COST = 1.4f
        private const val MAX_FIT = 1.7f

        /** 0 for the piece's own mood, growing with the distance between moods. */
        fun moodCost(t: LibraryTrack, mood: Mood): Float {
            val idx = t.moods.indexOf(mood)
            if (idx >= 0) return idx * 0.25f
            return 0.5f + MoodSpace.distance(t.primaryMood, mood) * 0.7f
        }

        /** 0 for the place itself, then related places, then pieces without a place. */
        fun placeCost(t: LibraryTrack, setting: Setting?): Float {
            if (setting == null) {
                // Without a known place, avoid strongly coloured music (space, Asia …).
                return if (t.settings.firstOrNull() in Exotic) 0.7f else 0f
            }
            val idx = t.settings.indexOf(setting)
            return when {
                idx >= 0 -> idx * 0.15f
                t.settings.any { it in setting.related } -> 0.45f
                t.settings.isEmpty() -> 0.55f
                else -> 0.9f
            }
        }

        private val Exotic = setOf(Setting.SPACE, Setting.ASIA, Setting.ORIENT, Setting.WESTERN, Setting.UNDERWATER, Setting.CITY)
    }
}

/** Moods as points of valence (sad → happy) and arousal (calm → excited). */
object MoodSpace {
    private val points = mapOf(
        Mood.CALM to (0.4f to -0.5f),
        Mood.IDYLLIC to (0.6f to -0.4f),
        Mood.HAPPY to (0.8f to 0.5f),
        Mood.ROMANTIC to (0.7f to -0.1f),
        Mood.NEUTRAL to (0.1f to 0f),
        Mood.MYSTERIOUS to (-0.1f to -0.1f),
        Mood.SAD to (-0.7f to -0.5f),
        Mood.EERIE to (-0.7f to 0.2f),
        Mood.TENSE to (-0.5f to 0.5f),
        Mood.ACTION to (-0.2f to 0.9f),
    )

    fun distance(a: Mood, b: Mood): Float {
        val (va, aa) = points.getValue(a)
        val (vb, ab) = points.getValue(b)
        return sqrt((va - vb) * (va - vb) + (aa - ab) * (aa - ab))
    }
}
