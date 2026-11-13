package io.github.hexotronic.lauscher.analysis.core

import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicCue
import io.github.hexotronic.lauscher.music.synth.Pace
import io.github.hexotronic.lauscher.music.synth.Setting
import kotlin.math.abs

/** A transcribed stretch of the book (book-wide positions in ms). */
data class TranscriptWindow(val startMs: Long, val endMs: Long, val text: String)

/** The analysis result for one window. [mood] null means "no music here". */
data class WindowMood(
    val startMs: Long,
    val endMs: Long,
    val mood: Mood?,
    /** 1 = subtle, 2 = clear, 3 = strong */
    val level: Int,
    val title: String,
    val source: String,
    /** Where the window takes place, null when unclear. */
    val setting: Setting? = null,
    /** What the music should carry (fight, chase, night …), at most three. */
    val cues: List<MusicCue> = emptyList(),
    /** How fast the music should move, null when not known. */
    val pace: Pace? = null,
)

/** A scene the listener corrected or confirmed, shown to the model as an example of their taste. */
data class Correction(val text: String, val mood: Mood?, val level: Int, val setting: Setting?)

/** Consecutive windows with the same mood and place, as shown on the mood map and used for music. */
data class Scene(
    val startMs: Long,
    val endMs: Long,
    val mood: Mood?,
    val level: Int,
    val title: String,
    val manual: Boolean,
    val setting: Setting? = null,
    /** The cues of its windows, most frequent first (at most three). */
    val cues: List<MusicCue> = emptyList(),
    val pace: Pace? = null,
) {
    val intensity: Float get() = (level.coerceIn(1, 3) - 1) / 2f * 0.7f + 0.2f
}

/**
 * How the book is sampled: every [windowMs] one sample of [sampleMs] (or all of it), which the
 * speech recognition hears [speed] times faster.
 */
data class SamplingPlan(val windowMs: Long, val sampleMs: Long, val speed: Float) {
    val full: Boolean get() = sampleMs >= windowMs
    val heardMs: Long get() = minOf(sampleMs, windowMs)

    /** Share of the book that is listened to (0..1). */
    val coverage: Float get() = heardMs.toFloat() / windowMs

    /** Windows per hour of audiobook. */
    val windowsPerHour: Float get() = 3_600_000f / windowMs

    /**
     * Computing time per hour of audiobook in seconds, from [speechFactor] (seconds of computing
     * per second of audio handed to the recognition) and [modelSecondsPerWindow]. Without speech
     * recognition (book text available) only the language model counts.
     */
    fun secondsPerHour(speechFactor: Float, modelSecondsPerWindow: Float, withSpeech: Boolean = true): Float {
        val speech = if (withSpeech) heardMs / 1000f / speed * speechFactor else 0f
        return windowsPerHour * (speech + modelSecondsPerWindow)
    }
}

object WindowPlanner {
    /** Scenes rarely change faster than this; longer windows mean fewer model calls. */
    const val WINDOW_MS = 120_000L

    /**
     * Groups windows for the language model: [size] per call, but the first call only [first], so
     * the music for the current position is ready sooner.
     */
    fun batches(todo: List<LongRange>, first: Int, size: Int): List<List<LongRange>> {
        if (todo.size <= first) return listOf(todo).filter { it.isNotEmpty() }
        return listOf(todo.take(first)) + todo.drop(first).chunked(size)
    }

    /** Splits the book into fixed windows. */
    fun windows(durationMs: Long, windowMs: Long = WINDOW_MS): List<LongRange> {
        val out = ArrayList<LongRange>()
        var start = 0L
        while (start < durationMs) {
            val end = minOf(durationMs, start + windowMs)
            if (end - start >= 5_000 || out.isEmpty()) out.add(start until end)
            start = end
        }
        return out
    }

    /** Which part of a window gets transcribed: everything, or a sample from its middle. */
    fun sampleOf(window: LongRange, sampleMs: Long): LongRange {
        val length = window.last + 1 - window.first
        if (length <= sampleMs) return window
        val start = window.first + (length - sampleMs) / 2
        return start until start + sampleMs
    }

    /**
     * The windows of [planned] that still need an analysis: those whose middle no analysed
     * window covers. This keeps earlier results when the window length is changed.
     */
    fun pending(planned: List<LongRange>, analysed: List<LongRange>): List<LongRange> {
        if (analysed.isEmpty()) return planned
        val sorted = analysed.sortedBy { it.first }
        val starts = LongArray(sorted.size) { sorted[it].first }
        return planned.filter { w ->
            val mid = (w.first + w.last) / 2
            var idx = starts.binarySearch(mid)
            if (idx < 0) idx = -idx - 2
            idx < 0 || mid > sorted[idx].last
        }
    }

    /** Whisper works on 30 s blocks; longer clips are cut into such chunks. */
    fun chunks(range: LongRange, chunkMs: Long = 30_000L): List<LongRange> {
        val out = ArrayList<LongRange>()
        var start = range.first
        while (start <= range.last) {
            val end = minOf(range.last + 1, start + chunkMs)
            out.add(start until end)
            start = end
        }
        return out
    }
}

/** Groups windows into scenes; manual corrections win. */
/** A refined scene change: the window planned to start at [afterMs] really begins at [cutMs]. */
data class SceneCut(val afterMs: Long, val cutMs: Long)

/**
 * Scene changes found more precisely than the window grid: between the samples of two windows
 * with different music, a few short probes decide where the second scene begins.
 */
object SceneCuts {
    /** Neighbouring windows whose music differs and whose edge was not refined yet ([done]: their starts). */
    fun candidates(windows: List<WindowMood>, done: Set<Long>): List<Pair<WindowMood, WindowMood>> =
        windows.sortedBy { it.startMs }.zipWithNext().filter { (a, b) ->
            a.endMs == b.startMs && b.startMs !in done &&
                a.source != SceneMerger.SOURCE_MANUAL && b.source != SceneMerger.SOURCE_MANUAL &&
                musicDiffers(a, b)
        }

    /** Different mood, or (with music) a different place or tension: the music would change here. */
    fun musicDiffers(a: WindowMood, b: WindowMood): Boolean =
        a.mood != b.mood || (a.mood != null && (a.setting != b.setting || a.level != b.level))

    /**
     * Narrows the change down between [low] (still the first scene) and [high] (already the
     * second): [steps] times the middle is probed with [isSecond] (null: undecidable, stop).
     * Returns the middle of the remaining interval.
     */
    inline fun bisect(low: Long, high: Long, steps: Int, isSecond: (Long) -> Boolean?): Long {
        var lo = low
        var hi = high
        for (i in 0 until steps) {
            val mid = (lo + hi) / 2
            when (isSecond(mid)) {
                true -> hi = mid
                false -> lo = mid
                null -> break
            }
        }
        return (lo + hi) / 2
    }

    /** Moves the shared edge of two windows to the refined cut. */
    fun apply(windows: List<WindowMood>, cuts: List<SceneCut>): List<WindowMood> {
        if (cuts.isEmpty()) return windows
        val byAfter = cuts.associateBy { it.afterMs }
        val sorted = windows.sortedBy { it.startMs }
        return sorted.mapIndexed { i, w ->
            var x = w
            val prev = sorted.getOrNull(i - 1)
            byAfter[w.startMs]?.let { c ->
                if (prev != null && prev.endMs == w.startMs && c.cutMs > prev.startMs && c.cutMs < w.endMs) x = x.copy(startMs = c.cutMs)
            }
            val next = sorted.getOrNull(i + 1)
            if (next != null) {
                byAfter[next.startMs]?.let { c ->
                    if (w.endMs == next.startMs && c.cutMs > w.startMs && c.cutMs < next.endMs) x = x.copy(endMs = c.cutMs)
                }
            }
            x
        }
    }

    /** The window grid position behind a (possibly moved) scene edge, for edits by scene. */
    fun planned(positionMs: Long, cuts: List<SceneCut>): Long = cuts.firstOrNull { it.cutMs == positionMs }?.afterMs ?: positionMs
}

object SceneMerger {
    fun merge(windows: List<WindowMood>): List<Scene> {
        val sorted = windows.sortedBy { it.startMs }
        val out = ArrayList<Scene>()
        // Cues of the windows of the scene being built, to pick the most frequent ones.
        val cueCounts = LinkedHashMap<MusicCue, Int>()
        val paces = ArrayList<Pace>()
        fun finish() {
            val last = out.lastOrNull() ?: return
            val cues = cueCounts.entries.sortedByDescending { it.value }.map { it.key }.take(MAX_SCENE_CUES)
            val pace = paces.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            out[out.lastIndex] = last.copy(cues = cues, pace = pace)
            cueCounts.clear()
            paces.clear()
        }
        for (w in sorted) {
            val last = out.lastOrNull()
            val manual = w.source == SOURCE_MANUAL
            if (last != null && last.mood == w.mood && last.manual == manual && w.startMs - last.endMs <= 1_000 &&
                (last.level == w.level || last.mood == null) && (last.setting == w.setting || last.mood == null)
            ) {
                out[out.lastIndex] = last.copy(endMs = w.endMs)
            } else {
                finish()
                out.add(Scene(w.startMs, w.endMs, w.mood, w.level, w.title, manual, w.setting))
            }
            w.cues.forEach { cueCounts[it] = (cueCounts[it] ?: 0) + 1 }
            w.pace?.let { paces += it }
        }
        finish()
        return out
    }

    /** A scene keeps the cues most of its windows share. */
    const val MAX_SCENE_CUES = 3

    /**
     * How far the analysis reaches without a gap from [positionMs] (gaps up to a second are
     * ignored); null if the position itself is not analysed yet. The last seconds of a book of
     * [durationMs], too short for a window of their own, count as analysed.
     */
    fun coveredUntil(scenes: List<Scene>, positionMs: Long, durationMs: Long = Long.MAX_VALUE): Long? {
        val sorted = scenes.sortedBy { it.startMs }
        val last = sorted.lastOrNull() ?: return null
        val reachesEnd = durationMs != Long.MAX_VALUE && durationMs - last.endMs < END_TAIL_MS
        val p = if (reachesEnd && positionMs >= last.endMs) last.endMs - 1 else positionMs
        var i = sorted.indexOfFirst { p >= it.startMs && p < it.endMs }
        if (i < 0) return null
        var end = sorted[i].endMs
        while (++i < sorted.size && sorted[i].startMs - end <= 1_000) end = maxOf(end, sorted[i].endMs)
        return if (reachesEnd && end == last.endMs) durationMs else end
    }

    /** Book endings shorter than this get no window of their own (see [WindowPlanner.windows]). */
    const val END_TAIL_MS = 5_000L

    /** Share of the analysed time per [key] (e.g. place or mood), largest first; null keys are left out. */
    fun <K> shares(scenes: List<Scene>, key: (Scene) -> K?): List<Pair<K, Float>> {
        val total = scenes.sumOf { it.endMs - it.startMs }.toFloat()
        if (total <= 0f) return emptyList()
        return scenes.mapNotNull { s -> key(s)?.let { it to (s.endMs - s.startMs) } }
            .groupBy({ it.first }, { it.second })
            .map { (k, v) -> k to v.sum() / total }
            .sortedByDescending { it.second }
    }

    fun sceneAt(scenes: List<Scene>, positionMs: Long): Scene? {
        // At the very end of the book the position equals the end of the last scene.
        val last = scenes.lastOrNull()
        if (last != null && positionMs >= last.endMs && positionMs - last.endMs < 2_000) return last
        var lo = 0
        var hi = scenes.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = scenes[mid]
            when {
                positionMs < s.startMs -> hi = mid - 1
                positionMs >= s.endMs -> lo = mid + 1
                else -> return s
            }
        }
        return null
    }

    const val SOURCE_LLM = "llm"
    const val SOURCE_KEYWORDS = "keywords"
    const val SOURCE_MANUAL = "manual"
    const val SOURCE_CHAPTERS = "chapters"
}

/**
 * Prompting for a small local instruct model (e.g. Qwen2.5 1.5B). The model sees a few
 * windows of transcript plus a running summary and returns one entry per window. A GBNF
 * grammar forces exactly that JSON shape, so even tiny models never break the format.
 */
object ScenePrompt {
    private val moods = listOf("NONE") + Mood.entries.map { it.name }
    private val places = Setting.entries.map { it.name } + "OTHER"

    val SYSTEM = """
        You are the sound designer of an audiobook app. You receive consecutive excerpts ("windows") of an
        audiobook transcript, each about two minutes long, possibly with recognition errors. For EVERY window decide
        which background music fits it. Judge each window by what happens in it, not by the tone of the whole book.

        mood values:
        NONE = no music: foreword, credits, table of contents, chapter announcements, lists, plain explanations
        CALM = everyday life, reflection, quiet conversation
        TENSE = suspense: danger approaches, threats, warnings, sneaking, waiting for something bad
        ACTION = fight, chase, escape, battle – characters attack, run or flee
        SAD = death, loss, grief, farewell, loneliness
        HAPPY = joy, celebration, victory, humour
        ROMANTIC = love, tenderness, closeness between two people
        MYSTERIOUS = secrets, magic, ancient places, discovering the unknown
        EERIE = horror, ghosts, the dead, dread in the dark
        IDYLLIC = peaceful nature, landscapes, calm travel
        NEUTRAL = only when none of the others fits

        level: 1 = subtle, 2 = clear, 3 = strong (how tense or intense the window is); fights and chases are 3.

        place: where the characters ARE in this window, not places they only talk about. Take the most specific value:
        CAVE = any cave, den, grotto, mine or dragon's lair, also inside a mountain or a glacier
        DUNGEON = prison, crypt, tomb, catacomb, vault, sewer
        CASTLE = castle, palace, royal hall, throne room
        TAVERN = inn, pub, feast hall
        TOWN = village, market, street, square
        HOME = inside a house: kitchen, parlour, bedroom
        TEMPLE = temple, church, shrine, monastery
        HAUNTED = graveyard, haunted house, underworld, hell
        MAGIC = enchanted places, fairy realms
        MOUNTAINS = peaks, passes, cliffs; WINTER = snow and ice fields
        OPEN_LAND = fields, meadows, roads, river banks, travelling
        FOREST, JUNGLE, DESERT, SEA (also ship, coast), UNDERWATER, BATTLEFIELD, ORIENT, ASIA, ANCIENT (Greece, Rome),
        NORTH (Celts, Vikings), WESTERN, SPACE, CITY (modern); OTHER only when the text does not say.
        When the characters stay where they were, keep the place of the previous window.

        pace: how fast the music should move: SLOW, MEDIUM or FAST.
        cues: one to three things the music should carry in this window, from:
        ${MusicCue.entries.joinToString("\n        ") { "${it.name} = ${it.hint}" }}
        Every window with music gets at least one cue; [] only for NONE.

        title: a short scene title in the language of the book, at most five words.
        Neighbouring windows of the same scene keep the same mood and place; only change them when the scene really
        changes.
        story: the running summary of the plot so far, updated with these windows, at most two short sentences, in the
        language of the book.
        Answer with JSON only.
    """.trimIndent()

    fun user(
        bookTitle: String,
        author: String?,
        story: String?,
        windows: List<TranscriptWindow>,
        maxCharsPerWindow: Int = 450,
        corrections: List<Correction> = emptyList(),
    ): String =
        buildString {
            append("Book: \"").append(bookTitle).append('"')
            if (!author.isNullOrBlank()) append(" by ").append(author)
            append("\nStory so far: ").append(story?.takeIf { it.isNotBlank() } ?: "(beginning of the book)")
            if (corrections.isNotEmpty()) {
                append("\n\nThe listener corrected these earlier passages; judge similar passages the same way:\n")
                for (c in corrections.take(MAX_CORRECTIONS)) {
                    append("- \"").append(c.text.replace('\n', ' ').trim().take(CORRECTION_CHARS)).append("\" -> mood ")
                    append(c.mood?.name ?: "NONE").append(", place ").append(c.setting?.name ?: "OTHER")
                    append(", level ").append(c.level.coerceIn(1, 3)).append('\n')
                }
            }
            append("\n\nWindows:\n")
            windows.forEachIndexed { i, w ->
                append('[').append(i).append("] (").append(clock(w.startMs)).append(") ")
                append(excerpt(w.text, maxCharsPerWindow).ifBlank { "(no speech)" })
                append('\n')
            }
        }

    /** GBNF grammar that only allows a JSON object with exactly [count] window entries. */
    fun grammar(count: Int): String = buildString {
        append("root ::= \"{\" ws \"\\\"windows\\\":\" ws \"[\" ws ")
        for (i in 0 until count) {
            if (i > 0) append(" ws \",\" ws ")
            append("item").append(i)
        }
        append(" ws \"]\" ws \",\" ws \"\\\"story\\\":\" ws story ws \"}\"\n")
        for (i in 0 until count) {
            append("item").append(i).append(" ::= \"{\" ws \"\\\"w\\\":\" ws \"").append(i)
            append("\" ws \",\" ws \"\\\"mood\\\":\" ws mood ws \",\" ws \"\\\"level\\\":\" ws [1-3] ws \",\" ws ")
            append("\"\\\"place\\\":\" ws place ws \",\" ws \"\\\"pace\\\":\" ws pace ws \",\" ws ")
            append("\"\\\"cues\\\":\" ws cues ws \",\" ws \"\\\"title\\\":\" ws title ws \"}\"\n")
        }
        append("mood ::= ").append(moods.joinToString(" | ") { "\"\\\"$it\\\"\"" }).append('\n')
        append("place ::= ").append(places.joinToString(" | ") { "\"\\\"$it\\\"\"" }).append('\n')
        append("pace ::= ").append(Pace.entries.joinToString(" | ") { "\"\\\"${it.name}\\\"\"" }).append('\n')
        append("cues ::= \"[\" ws (cue (ws \",\" ws cue){0,2})? ws \"]\"\n")
        append("cue ::= ").append(MusicCue.entries.joinToString(" | ") { "\"\\\"${it.name}\\\"\"" }).append('\n')
        append("title ::= \"\\\"\" char{1,32} \"\\\"\"\n")
        append("story ::= \"\\\"\" char{0,300} \"\\\"\"\n")
        append("char ::= [^\"\\\\\\x00-\\x1F] | \"\\\\\" [\"\\\\/nt]\n")
        append("ws ::= [ \\n]{0,2}\n")
    }

    data class Result(val windows: List<WindowMood>, val story: String)

    /**
     * Shortens a long passage for the prompt: beginning, middle and end, so the model sees how the
     * whole window develops and not only its first seconds.
     */
    fun excerpt(text: String, maxChars: Int): String {
        val t = text.replace('\n', ' ').replace(Regex(" {2,}"), " ").trim()
        if (t.length <= maxChars) return t
        val part = (maxChars - 6) / 3
        fun cut(from: Int): String {
            var a = from.coerceIn(0, t.length)
            while (a > 0 && t[a - 1] != ' ' && from - a < 20) a--
            var b = (a + part).coerceAtMost(t.length)
            while (b < t.length && t[b] != ' ' && b - a < part + 20) b++
            return t.substring(a, b).trim()
        }
        return cut(0) + " … " + cut(t.length / 2 - part / 2) + " … " + cut(t.length - part)
    }

    const val MAX_CORRECTIONS = 3

    /** Cues per window the model may name. */
    const val MAX_CUES = 3
    private const val CORRECTION_CHARS = 160

    /** The corrections closest before [positionMs] (or else after it), at most [MAX_CORRECTIONS]. */
    fun nearestCorrections(all: List<Pair<Long, Correction>>, positionMs: Long): List<Correction> =
        all.sortedWith(compareBy<Pair<Long, Correction>> { if (it.first <= positionMs) 0 else 1 }.thenBy { abs(positionMs - it.first) })
            .take(MAX_CORRECTIONS)
            .map { it.second }

    /** Parses the model output; tolerant of missing entries (they fall back to NEUTRAL). */
    fun parse(output: String, windows: List<TranscriptWindow>): Result? {
        val root = runCatching { MiniJson.parse(output.trim()) }.getOrNull() as? Map<*, *> ?: return null
        val items = root["windows"] as? List<*> ?: return null
        val byIndex = HashMap<Int, Map<*, *>>()
        for (item in items) {
            val m = item as? Map<*, *> ?: continue
            val idx = (m["w"] as? Number)?.toInt() ?: continue
            byIndex[idx] = m
        }
        val result = windows.mapIndexed { i, w ->
            val m = byIndex[i]
            val moodName = m?.get("mood") as? String
            val mood = if (moodName == "NONE") null else Mood.parse(moodName) ?: Mood.NEUTRAL
            WindowMood(
                startMs = w.startMs,
                endMs = w.endMs,
                mood = mood,
                level = ((m?.get("level") as? Number)?.toInt() ?: 2).coerceIn(1, 3),
                title = ((m?.get("title") as? String)?.trim().orEmpty().take(60)).takeUnless { echoesInstructions(it) }.orEmpty(),
                source = SceneMerger.SOURCE_LLM,
                setting = Setting.parse(m?.get("place") as? String),
                cues = (m?.get("cues") as? List<*>).orEmpty().mapNotNull { MusicCue.parse(it as? String) }.distinct().take(MAX_CUES),
                pace = Pace.parse(m?.get("pace") as? String),
            )
        }
        return Result(result, (root["story"] as? String).orEmpty().trim())
    }

    /** Small models sometimes copy a phrase of their instructions ("a short scene title in the") instead of a title. */
    private fun echoesInstructions(title: String): Boolean =
        title.split(' ').count { it.isNotBlank() } >= 3 && SYSTEM.replace(Regex("\\s+"), " ").contains(title, ignoreCase = true)

    val CUT_SYSTEM = """
        You compare a short passage of an audiobook with two consecutive scenes. Scene A comes first, scene B
        follows. Answer A if the passage still belongs to scene A, B if scene B has already begun.
    """.trimIndent()

    /** Only "A" or "B". */
    const val CUT_GRAMMAR = "root ::= \"A\" | \"B\"\n"

    fun cutUser(a: WindowMood, b: WindowMood, textA: String?, textB: String?, passage: String): String = buildString {
        fun scene(label: String, w: WindowMood, text: String?) {
            append("Scene ").append(label).append(": ").append(w.mood?.name ?: "NONE")
            append(", place ").append(w.setting?.name ?: "OTHER").append(", level ").append(w.level)
            if (w.title.isNotBlank()) append(" – ").append(w.title)
            if (!text.isNullOrBlank()) append("\n  e.g. \"").append(excerpt(text, CUT_CONTEXT_CHARS)).append('"')
            append('\n')
        }
        scene("A", a, textA)
        scene("B", b, textB)
        append("\nPassage: \"").append(excerpt(passage, CUT_PASSAGE_CHARS)).append("\"\n")
        append("Does the passage belong to scene A or B?")
    }

    /** true: the passage belongs to the second scene; null: no usable answer. */
    fun parseCut(output: String?): Boolean? = when (output?.trim()?.firstOrNull()?.uppercaseChar()) {
        'A' -> false
        'B' -> true
        else -> null
    }

    private const val CUT_CONTEXT_CHARS = 220
    private const val CUT_PASSAGE_CHARS = 400

    val RECAP_SYSTEM = """
        You summarise audiobooks for listeners who return after a break. Write in the language of the book.
        Retell what has happened so far in 4 to 7 short sentences: main characters, where they are, what they want
        and the last events before the current position. Never reveal anything beyond the given text.
        No headings, no lists.
    """.trimIndent()

    fun recapUser(bookTitle: String, story: String?, recent: List<TranscriptWindow>, maxChars: Int = 5000): String = buildString {
        append("Book: \"").append(bookTitle).append("\"\n")
        if (!story.isNullOrBlank()) append("Summary of earlier parts: ").append(story).append('\n')
        append("\nMost recent passages before the current position:\n")
        var budget = maxChars
        for (w in recent.takeLast(12)) {
            val text = w.text.replace('\n', ' ').trim()
            if (text.isEmpty()) continue
            val part = text.take(budget)
            append("(").append(clock(w.startMs)).append(") ").append(part).append('\n')
            budget -= part.length
            if (budget <= 0) break
        }
    }

    fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

/**
 * Offline mood guess from keywords (German and English) for when no language model is
 * installed. Coarse, but fully local and instant.
 */
object KeywordMoods {
    private val words: Map<Mood, List<String>> = mapOf(
        Mood.TENSE to listOf(
            "gefahr", "angst", "fürcht", "lauer", "schatten", "plötzlich", "verdächtig", "zitter", "herz klopf", "atem",
            "nervös", "beobacht", "spur", "warnung", "danger", "afraid", "fear", "suddenly", "shadow", "watch", "nervous",
        ),
        Mood.ACTION to listOf(
            "kampf", "kämpf", "schwert", "schuss", "schoss", "explosion", "rannte", "renn", "schlug", "angriff", "flucht",
            "floh", "verfolg", "stürzt", "fight", "sword", "shot", "explosion", "ran ", "attack", "chase", "escape", "fled",
        ),
        Mood.SAD to listOf(
            "träne", "weinte", "weinen", "starb", "gestorben", "tod ", "beerdig", "begräb", "abschied", "einsam", "trauer",
            "verlor", "vermiss", "tears", "cried", "died", "death", "funeral", "goodbye", "lonely", "grief", "miss ",
        ),
        Mood.HAPPY to listOf(
            "lachte", "lachen", "freude", "freute", "fest ", "feier", "glücklich", "jubel", "spaß", "grinste", "witz",
            "laughed", "laugh", "joy", "party", "happy", "celebrat", "grinned", "fun ",
        ),
        Mood.ROMANTIC to listOf(
            "kuss", "küsste", "liebe", "zärtlich", "umarmt", "verliebt", "hand hielt", "kiss", "love", "embrace", "tender",
            "in love", "held her hand", "held his hand",
        ),
        Mood.MYSTERIOUS to listOf(
            "geheimnis", "rätsel", "magie", "magisch", "zauber", "seltsam", "uralt", "verborgen", "schatz", "prophez",
            "mystery", "magic", "spell", "strange", "ancient", "secret", "hidden", "treasure",
        ),
        Mood.EERIE to listOf(
            "geist", "gespenst", "grab", "friedhof", "blut", "dunkelheit", "schrei", "grauen", "unheimlich", "leiche",
            "ghost", "grave", "cemetery", "blood", "darkness", "scream", "horror", "corpse", "creepy",
        ),
        Mood.IDYLLIC to listOf(
            "wald", "wiese", "sonne", "vögel", "vogel", "bach", "garten", "blume", "meer", "himmel", "berg", "see ",
            "forest", "meadow", "sunshine", "birds", "stream", "garden", "flower", "sea ", "sky", "mountain", "lake",
        ),
        Mood.CALM to listOf(
            "ruhig", "stille", "tee", "kaffee", "frühstück", "gemütlich", "dachte nach", "erinner", "quiet", "calm", "tea",
            "coffee", "breakfast", "cozy", "remember",
        ),
    )

    fun classify(text: String): Pair<Mood?, Int> {
        val t = " " + text.lowercase() + " "
        if (t.isBlank()) return null to 1
        val scores = words.mapValues { (_, list) -> list.sumOf { w -> occurrences(t, w) } }
        val best = scores.maxByOrNull { it.value } ?: return Mood.NEUTRAL to 1
        val perWords = best.value * 100.0 / (t.length / 6.0).coerceAtLeast(10.0)
        return when {
            best.value == 0 -> Mood.NEUTRAL to 1
            perWords > 3 -> best.key to 3
            perWords > 1.5 -> best.key to 2
            else -> best.key to 1
        }
    }

    private val places: Map<Setting, List<String>> = mapOf(
        Setting.FOREST to listOf("wald", "wälder", "bäume", "lichtung", "unterholz", "forest", "woods", "trees", "clearing"),
        Setting.JUNGLE to listOf("dschungel", "urwald", "lianen", "sumpf", "tropen", "jungle", "swamp", "tropical"),
        Setting.OPEN_LAND to listOf("wiese", "feld", "felder", "landstraße", "weg ", "hügel", "meadow", "field", "road", "hill"),
        Setting.MOUNTAINS to listOf("gebirge", " berg", "berge ", "gipfel", "felsen", "mountain", "peak", "cliff"),
        Setting.WINTER to listOf("schnee", "eis ", "frost", "gletscher", "winter", "snow", "ice ", "frozen", "glacier"),
        Setting.DESERT to listOf("wüste", " sand", "düne", "oase", "desert", "dune", "oasis"),
        Setting.SEA to listOf("meer", "schiff", "hafen", "küste", "strand", "segel", "welle", "sea ", "ship", "harbo", "coast", "beach", "waves"),
        Setting.UNDERWATER to listOf("unter wasser", "tauch", "meeresgrund", "underwater", "dived", "seabed"),
        Setting.CAVE to listOf("höhle", "stollen", "mine ", "tunnel", "cave", "cavern", "mine"),
        Setting.DUNGEON to listOf("kerker", "verlies", "zelle", "gefängnis", "gruft", "kanalisation", "dungeon", "prison", "cell ", "crypt", "sewer"),
        Setting.CASTLE to listOf("burg", "schloss", "palast", "thron", "festung", "könig", "castle", "palace", "throne", "fortress", "king"),
        Setting.TOWN to listOf("dorf", "stadt", "markt", "gasse", "platz", "village", "town", "market", "street"),
        Setting.TAVERN to listOf("taverne", "wirtshaus", "gasthaus", "schänke", "bier", "krug", "tavern", "inn ", "ale ", "pub "),
        Setting.HOME to listOf("küche", "stube", "zuhause", "wohnzimmer", "kamin", " bett", "kitchen", "at home", "living room", "fireplace"),
        Setting.BATTLEFIELD to listOf("schlacht", "krieg", "heer", "armee", "soldat", "belager", "battle", "war ", "army", "soldier", "siege"),
        Setting.TEMPLE to listOf("tempel", "kirche", "kloster", "altar", "priester", "mönch", "temple", "church", "monastery", "priest", "monk"),
        Setting.MAGIC to listOf("zauber", "magie", "fee ", "feen", "hexe", "magier", "zauberer", "magic", "wizard", "witch", "fairy", "spell"),
        Setting.HAUNTED to listOf("friedhof", "geister", "gespenst", "spuk", "hölle", "dämon", "graveyard", "ghost", "haunted", " hell ", "demon"),
        Setting.ORIENT to listOf("basar", "sultan", "pharao", "pyramide", "kalif", "bazaar", "pharaoh", "pyramid", "caliph", "arabia"),
        Setting.ASIA to listOf("samurai", "kaiser von china", "tempel in kyoto", "shogun", "dynastie", "geisha", "ninja", "emperor of china", "dynasty"),
        Setting.ANCIENT to listOf("römer", "rom ", "legion", "athen", "sparta", "gladiator", "senat", "roman", "rome", "athens", "senate"),
        Setting.NORTH to listOf("wikinger", "drachenboot", "kelten", "druide", "clan", "viking", "longship", "celtic", "druid", "highlands"),
        Setting.WESTERN to listOf("saloon", "sheriff", "cowboy", "revolver", "prärie", "colt", "ranch", "prairie", "outlaw"),
        Setting.SPACE to listOf("raumschiff", "weltall", "planet", "galaxie", "astronaut", "spaceship", "space", "galaxy", "orbit"),
        Setting.CITY to listOf("auto ", "autos", "büro", "handy", "u-bahn", "hochhaus", "taxi", "polizei", "car ", "office", "phone", "subway", "police"),
    )

    /** The most likely place of a passage, or null when no place word stands out. */
    fun settingOf(text: String): Setting? {
        val t = " " + text.lowercase() + " "
        val scores = places.mapValues { (_, list) -> list.sumOf { w -> occurrences(t, w) } }
        val best = scores.maxByOrNull { it.value } ?: return null
        return best.key.takeIf { best.value >= 2 }
    }

    // Word starts with a leading space where a shorter piece would also match inside other words
    // ("king" in "looking", "leiche" in "gleiche", "regen" in "aufregend", German "war" = was).
    private val cueWords: Map<MusicCue, List<String>> = mapOf(
        MusicCue.BATTLE to listOf("kampf", "kämpf", "schwert", "schlacht", "krieg", "angriff", "duell", "fight", "sword", "battle", "warrior", "attack", " duel"),
        MusicCue.CHASE to listOf("verfolg", "flucht", " floh ", "rannte", "jagte", "entkam", "chase", "escape", " fled", "pursu", "ran after"),
        MusicCue.DANGER to listOf("gefahr", "bedroh", "hinterhalt", " lauert", "monster", "bestie", "danger", "threat", "ambush", " beast"),
        MusicCue.HORROR to listOf(" geist", "gespenst", "untot", " leiche", " blut", "albtraum", "ghost", "undead", "corpse", "blood", "nightmare"),
        MusicCue.MYSTERY to listOf("geheimnis", "rätsel", " ruine", "verborgen", "entdeck", "secret", "riddle", " ruin", "hidden", "discover"),
        MusicCue.MAGIC to listOf("magie", "zauber", "magier", " hexe", " feen", "elfen", "magic", " spell", "wizard", " witch", "fairy", "fairies"),
        MusicCue.GRIEF to listOf("trauer", "starb", " tod ", "beerdig", "begräb", "weinte", "verzweif", "grief", " died", "death", "funeral", " wept", "despair"),
        MusicCue.LOVE to listOf(" liebe ", " liebte", " kuss", "küsste", "zärtlich", "sehnsucht", " love", " kiss", "tenderly", "longing"),
        MusicCue.FEAST to listOf(" feier", "festmahl", "taverne", "gasthaus", " tanz", " markt", "musikanten", "feast", "celebrat", "tavern", " inn ", " danc", "market"),
        MusicCue.VICTORY to listOf(" sieg", "triumph", "gerettet", "jubel", "helden", "victory", "rescued", "cheer", " hero"),
        MusicCue.JOURNEY to listOf(" reise", "aufbruch", "wanderte", "unterwegs", "straße", " pfad", "journey", "travel", "set out", " road", " path "),
        MusicCue.NATURE to listOf(" wald", " wiese", " feld", " tiere", "vögel", "blumen", "forest", "meadow", " field", "animals", " birds", "flowers"),
        MusicCue.NIGHT to listOf("nacht", "dunkel", " mond", "sterne", "schlief", " traum", "night", " dark", " moon", " stars", " slept", " dream"),
        MusicCue.WATER to listOf(" meer", " fluss", "schiff", " regen ", " hafen", " welle", " sea ", "river", " ship", " rain", "harbour", "harbor", " waves"),
        MusicCue.ROYAL to listOf("könig", "prinz", " am hof", "palast", " thron", " king", "queen", "prince", " court", "palace", "throne"),
        MusicCue.SACRED to listOf("tempel", "kirche", "gebet", "priester", "götter", "heilig", "temple", "church", "prayer", "priest", " gods", " holy"),
        MusicCue.HOME to listOf("zuhause", "familie", " kamin", " stube", " mutter", " vater", " home", "family", "fireplace", "mother", "father"),
        MusicCue.HUMOR to listOf("lachte", " witz", " streich", "kicher", "grinste", "laughed", " joke", " prank", "giggl", "grinned"),
    )

    /**
     * Up to three cues whose words occur in [text] (fallback when the language model names none):
     * in a short passage once is enough, in a long one twice.
     */
    fun cuesOf(text: String): List<MusicCue> {
        val t = " " + text.lowercase() + " "
        val needed = if (text.length < SHORT_TEXT) 1 else 2
        return cueWords.mapValues { (_, list) -> list.sumOf { w -> occurrences(t, w) } }
            .filter { it.value >= needed }
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(ScenePrompt.MAX_CUES)
    }

    /** Shorter passages (a 25-second sample, a short book-text slice) need only one hit per cue. */
    private const val SHORT_TEXT = 1200

    private fun occurrences(text: String, word: String): Int {
        var count = 0
        var i = text.indexOf(word)
        while (i >= 0) {
            count++
            i = text.indexOf(word, i + word.length)
        }
        return count
    }
}
