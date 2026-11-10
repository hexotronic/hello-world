package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicFollow
import io.github.hexotronic.lauscher.music.synth.Setting
import kotlin.math.abs
import kotlin.math.sqrt

enum class MusicLicense(val label: String, val url: String) {
    GPL2("GNU GPL v2 oder neuer", "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
    GPL("GNU GPL v2", "https://www.gnu.org/licenses/old-licenses/gpl-2.0.html"),
    GPL3("GNU GPL v3", "https://www.gnu.org/licenses/gpl-3.0.html"),
    GPL2_OR_3("GNU GPL v2 oder v3", "https://www.gnu.org/licenses/gpl-3.0.html"),
    GPL3_CC_BY_SA_3("GNU GPL v3 / CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/"),
    GPL2_CC_BY_SA("CC BY-SA / GNU GPL v2+", "https://creativecommons.org/licenses/by-sa/3.0/"),
    CC_BY_SA_3("CC BY-SA 3.0", "https://creativecommons.org/licenses/by-sa/3.0/"),
    CC_BY_SA_4("CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    CC_BY_2("CC BY 2.0", "https://creativecommons.org/licenses/by/2.0/"),
    CC_BY_3("CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/"),
    CC_BY_4("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
    OGA_BY_3("OGA-BY 3.0", "https://static.opengameart.org/OGA-BY-3.0.txt"),
    CC0("CC0 (gemeinfrei)", "https://creativecommons.org/publicdomain/zero/1.0/"),
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
    VALYRIA_TEAR(
        "Valyria Tear",
        "https://valyriatear.blogspot.com",
        "https://github.com/ValyriaTear/ValyriaTear",
        "Japanisch angehauchtes Fantasy-Rollenspiel: Stücke von Matthew Pablo, HorrorPen, Alexandr Zhelanov u. a.",
    ),
    WYRMSUN(
        "Wyrmsun",
        "https://andrettin.github.io",
        "https://github.com/Andrettin/Wyrmsun",
        "Zwergen- und Germanen-Themen von Marcelo Fernandez, aus mehreren Teilen zusammengesetzt",
    ),
    DUELYST(
        "Duelyst",
        "https://github.com/open-duelyst/duelyst",
        "https://github.com/open-duelyst/duelyst",
        "Orchestraler Soundtrack des Fantasy-Kartenspiels, von Counterplay Games unter CC0 freigegeben",
    ),
    HYPERROGUE(
        "HyperRogue",
        "https://roguetemple.com/z/hyper/",
        "https://github.com/zenorogue/hyperrogue",
        "Länder-Themen: Wüste, Eis, Höhlen, Dschungel, Friedhof, Hölle, Palast, Ozean",
    ),
    TUXEMON(
        "Tuxemon (JRPG Collection)",
        "https://www.tuxemon.org",
        "https://github.com/Tuxemon/Tuxemon",
        "Yubatakes JRPG-Sammlung: Felder, Stadt, Taverne, Tempel, Verlies, Kampf",
    ),
    MEGAGLEST(
        "MegaGlest",
        "https://megaglest.org",
        "https://github.com/MegaGlest/megaglest-data",
        "Strategie-Spiel: Magie, Ritter, Nordmänner, Römer, Ägypten, Persien",
    ),
    ARDENTRYST(
        "Ardentryst",
        "https://github.com/ardentryst/ardentryst",
        "https://github.com/ardentryst/ardentryst",
        "Action-Rollenspiel von Jordan Trudgett: Stadt, Schnee, Burg, Kampf, Weltkarte",
    ),
    MANA_WORLD(
        "The Mana World",
        "https://www.themanaworld.org",
        "https://github.com/themanaworld/tmw-music",
        "Musik des freien Online-Rollenspiels: Wald, Höhlen, Taverne, Schneedorf, Meer",
    ),
    MOUBOOTAUR(
        "Moubootaur Legends (TMW2)",
        "https://tmw2.org",
        "https://gitlab.com/TMW2/evol-music",
        "Sammlung des Rollenspiels mit Stücken vieler freier Komponisten (Lizenz je Stück in der Liste)",
    ),
    INVERTIKA(
        "Invertika",
        "https://github.com/invertika",
        "https://github.com/invertika/data",
        "Deutsches Fantasy-Rollenspiel: Burg, Wüste, Höhlen, Wald, Hafen, Kirche",
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
 * Chooses the next piece for a scene, at random among all pieces that suit it: every piece
 * written for the scene's place and mood (and roughly its tension) goes into a "bag", and the
 * pieces are drawn from it in random order, so all of them come up before one repeats.
 * Only when the place has too little do related places and pieces without a place step in.
 */
class TrackPicker(seed: Long = System.nanoTime()) {
    private var state = seed xor 0x5DEECE66DL

    /** Per scene kind ("mood/place/tension"): the pieces already drawn from its bag, oldest first. */
    private val drawn = HashMap<String, ArrayDeque<String>>()

    /**
     * [liked] pieces come up more often (they return to the bag sooner); [avoid] (e.g. the piece
     * the listener just skipped) is only taken when nothing else fits. "Genau" ([follow]) draws
     * from a smaller bag: only pieces that fit about as well as the best one.
     */
    @Synchronized
    fun pick(
        installed: Collection<LibraryTrack>,
        mood: Mood,
        setting: Setting?,
        intensity: Float,
        liked: Set<String> = emptySet(),
        avoid: String? = null,
        follow: MusicFollow = MusicFollow.BALANCED,
    ): LibraryTrack? {
        if (installed.isEmpty()) return null
        // Only reasonably fitting pieces: the mood itself or a close one, and mood and place together
        // not too far off. Otherwise the synthesizer is the better choice.
        val fitting = installed.filter { moodCost(it, mood) < MAX_MOOD_COST && moodCost(it, mood) + placeCost(it, setting) < MAX_FIT }
        if (fitting.isEmpty()) return null
        val precise = follow == MusicFollow.PRECISE
        val level = intensity.coerceIn(0f, 1f)
        val pool = bagFor(fitting, mood, setting, level, precise)
        val key = "$mood/$setting/${(level * 2.99f).toInt()}"
        val history = drawn.getOrPut(key) { ArrayDeque() }
        // The last few pieces never come right back (unless there is nothing else).
        val recent = history.takeLast((pool.size - 1).coerceIn(0, MIN_GAP)).toSet()
        var open = pool.filter { it.id !in history || (it.id in liked && it.id !in recent) }
        if (open.isEmpty()) {
            // Everything was played: refill the bag.
            history.retainAll(recent)
            open = pool.filter { it.id !in recent }
        }
        open.filter { it.id != avoid }.takeIf { it.isNotEmpty() }?.let { open = it }
        if (open.isEmpty()) open = pool
        // Mostly chance; pieces made for this very mood and tension are a bit more likely.
        val weights = open.map { t ->
            val moodWeight = 1f / (1f + 2f * moodCost(t, mood))
            val energyWeight = 1f / (1f + (if (precise) 6f else 2f) * abs(t.energy - level))
            moodWeight * energyWeight * (if (t.id in liked) LIKED_WEIGHT else 1f)
        }
        var r = nextFloat() * weights.sum()
        var chosen = open.last()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r <= 0f) {
                chosen = open[i]
                break
            }
        }
        history.remove(chosen.id)
        history.addLast(chosen.id)
        while (history.size > MAX_HISTORY) history.removeFirst()
        return chosen
    }

    /**
     * The pieces a scene draws from. Pieces written for its place (and its mood) come first, within
     * reach of its tension; without enough of them, the best fitting pieces of related places
     * and pieces without a place join in.
     */
    private fun bagFor(fitting: List<LibraryTrack>, mood: Mood, setting: Setting?, level: Float, precise: Boolean): List<LibraryTrack> {
        val band = if (precise) PRECISE_ENERGY_BAND else ENERGY_BAND
        // Own place first, then related places and pieces without a place, then anything.
        val tier = PLACE_TIERS.asSequence()
            .map { limit -> fitting.filter { placeCost(it, setting) <= limit && moodCost(it, mood) <= STRICT_MOOD } }
            .firstOrNull { it.size >= MIN_BAG }
        val inReach = tier?.filter { abs(it.energy - level) <= band }
        val base = when {
            inReach != null && inReach.size >= MIN_BAG -> inReach
            tier != null -> tier
            else -> {
                val energyWeight = if (precise) 1.2f else 0.8f
                fun cost(t: LibraryTrack) = moodCost(t, mood) + placeCost(t, setting) + abs(t.energy - level) * energyWeight
                val best = fitting.minOf(::cost)
                fitting.filter { cost(it) <= best + if (precise) PRECISE_MARGIN else MARGIN }
            }
        }
        if (!precise || base.size <= 1) return base
        // "Genau": only the pieces that fit about as well as the best one of the bag.
        val score = { t: LibraryTrack -> moodCost(t, mood) + placeCost(t, setting) + abs(t.energy - level) * 1.2f }
        val best = base.minOf(score)
        return base.filter { score(it) <= best + PRECISE_MARGIN }
    }

    private fun nextFloat(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        return ((state ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
    }

    companion object {
        private const val MAX_MOOD_COST = 1.4f
        private const val MAX_FIT = 1.7f
        private const val STRICT_MOOD = 0.5f

        /**
         * Place costs of the bags tried in turn: written for the place (also as second or third
         * place), then related places and pieces without a place, then everything.
         */
        private val PLACE_TIERS = floatArrayOf(0.3f, 0.6f, 1f)

        /** A piece's energy may be this far from the scene's tension and still be in its bag. */
        private const val ENERGY_BAND = 0.35f
        private const val PRECISE_ENERGY_BAND = 0.2f

        /** Without own pieces: how much worse than the best fitting piece a piece may be. */
        private const val MARGIN = 0.6f
        private const val PRECISE_MARGIN = 0.3f

        /** Fewer own pieces than this: related places join in. */
        private const val MIN_BAG = 2

        /** At least this many other pieces before one comes again (if there are that many). */
        private const val MIN_GAP = 4
        private const val MAX_HISTORY = 400
        private const val LIKED_WEIGHT = 2f

        /** "Ruhig": a playing piece continues while its fit for the new scene stays below this. */
        private const val KEEP_COST = 1.0f

        /** How well [t] suits a scene: 0 is perfect, the same measure [pick] uses (without variety). */
        fun fitCost(t: LibraryTrack, mood: Mood, setting: Setting?, intensity: Float): Float =
            moodCost(t, mood) + placeCost(t, setting) + abs(t.energy - intensity.coerceIn(0f, 1f)) * 0.8f

        /**
         * Whether [t], picked for an earlier scene, also suits this one well enough to keep
         * playing: written for this mood (or a close one), the place not foreign, energy not far off.
         */
        fun stillFits(t: LibraryTrack, mood: Mood, setting: Setting?, intensity: Float): Boolean =
            moodCost(t, mood) <= STRICT_MOOD && placeCost(t, setting) <= 0.55f && fitCost(t, mood, setting, intensity) <= KEEP_COST

        /** Why [t] suits (or only roughly suits) the scene, in words for the AI log. */
        fun describeFit(t: LibraryTrack, mood: Mood, setting: Setting?, intensity: Float): String {
            val m = t.moods.indexOf(mood)
            val moodPart = when {
                m == 0 -> "Gefühl „${mood.label}“ passt genau"
                m > 0 -> "passt auch zu „${mood.label}“ (eigentlich „${t.primaryMood.label}“)"
                else -> "„${t.primaryMood.label}“ statt „${mood.label}“ (kein besseres Stück geladen)"
            }
            val placePart = when {
                setting == null -> if (t.settings.isEmpty()) "Ort unklar, Stück ohne festen Ort" else "Ort unklar"
                t.settings.indexOf(setting) == 0 -> "für den Ort „${setting.label}“ geschrieben"
                setting in t.settings -> "passt auch zum Ort „${setting.label}“"
                t.settings.any { it in setting.related } -> "verwandter Ort „${t.settings.first { it in setting.related }.label}“ statt „${setting.label}“"
                t.settings.isEmpty() -> "ohne festen Ort (für „${setting.label}“ nichts geladen)"
                else -> "eigentlich für „${t.settings.first().label}“"
            }
            val diff = t.energy - intensity.coerceIn(0f, 1f)
            val energyPart = when {
                abs(diff) < 0.2f -> "Energie passt zur Spannung"
                diff > 0 -> "etwas lebhafter als die Szene"
                else -> "etwas ruhiger als die Szene"
            }
            return "$moodPart · $placePart · $energyPart"
        }

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

    /** How pleasant a mood is (-1 dark … 1 bright). */
    fun valence(m: Mood): Float = points.getValue(m).first

    /** How agitated a mood is (-1 calm … 1 excited). */
    fun arousal(m: Mood): Float = points.getValue(m).second

    fun distance(a: Mood, b: Mood): Float {
        val (va, aa) = points.getValue(a)
        val (vb, ab) = points.getValue(b)
        return sqrt((va - vb) * (va - vb) + (aa - ab) * (aa - ab))
    }
}
