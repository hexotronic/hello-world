package io.github.hexotronic.lauscher.analysis.core

import io.github.hexotronic.lauscher.music.synth.Mood

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
)

/** Consecutive windows with the same mood, as shown on the mood map and used for music. */
data class Scene(
    val startMs: Long,
    val endMs: Long,
    val mood: Mood?,
    val level: Int,
    val title: String,
    val manual: Boolean,
) {
    val intensity: Float get() = (level.coerceIn(1, 3) - 1) / 2f * 0.7f + 0.2f
}

object WindowPlanner {
    /** Scenes rarely change faster than this; longer windows mean fewer model calls. */
    const val WINDOW_MS = 120_000L

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
object SceneMerger {
    fun merge(windows: List<WindowMood>): List<Scene> {
        val sorted = windows.sortedBy { it.startMs }
        val out = ArrayList<Scene>()
        for (w in sorted) {
            val last = out.lastOrNull()
            val manual = w.source == SOURCE_MANUAL
            if (last != null && last.mood == w.mood && last.manual == manual && w.startMs - last.endMs <= 1_000 &&
                (last.level == w.level || last.mood == null)
            ) {
                out[out.lastIndex] = last.copy(endMs = w.endMs)
            } else {
                out.add(Scene(w.startMs, w.endMs, w.mood, w.level, w.title, manual))
            }
        }
        return out
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

    val SYSTEM = """
        You are the sound designer of an audiobook app. You receive consecutive excerpts ("windows") of an
        audiobook transcript, each about 90 seconds long, possibly with recognition errors. For EVERY window decide
        which background music fits the scene.

        mood values:
        NONE = no music (plain dialogue, explanations, lists, foreword, credits, table of contents)
        CALM = everyday life, reflection, quiet narration
        TENSE = suspense, danger approaching, uncertainty
        ACTION = fight, chase, escape, climax
        SAD = loss, grief, farewell, loneliness
        HAPPY = joy, celebration, humour
        ROMANTIC = love, tenderness, closeness
        MYSTERIOUS = secrets, magic, discovery
        EERIE = horror, ghosts, darkness, dread
        IDYLLIC = nature, landscape, peace
        NEUTRAL = soft background when the mood is unclear

        level: 1 = subtle, 2 = clear, 3 = strong.
        title: a short scene title in the language of the book, at most five words.
        Neighbouring windows of the same scene keep the same mood; only change it when the scene really changes.
        story: the running summary of the plot so far, updated with these windows, at most two short sentences, in the
        language of the book.
        Answer with JSON only.
    """.trimIndent()

    fun user(bookTitle: String, author: String?, story: String?, windows: List<TranscriptWindow>, maxCharsPerWindow: Int = 450): String =
        buildString {
            append("Book: \"").append(bookTitle).append('"')
            if (!author.isNullOrBlank()) append(" by ").append(author)
            append("\nStory so far: ").append(story?.takeIf { it.isNotBlank() } ?: "(beginning of the book)")
            append("\n\nWindows:\n")
            windows.forEachIndexed { i, w ->
                append('[').append(i).append("] (").append(clock(w.startMs)).append(") ")
                append(w.text.replace('\n', ' ').trim().take(maxCharsPerWindow).ifBlank { "(no speech)" })
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
            append("\"\\\"title\\\":\" ws title ws \"}\"\n")
        }
        append("mood ::= ").append(moods.joinToString(" | ") { "\"\\\"$it\\\"\"" }).append('\n')
        append("title ::= \"\\\"\" char{1,32} \"\\\"\"\n")
        append("story ::= \"\\\"\" char{0,300} \"\\\"\"\n")
        append("char ::= [^\"\\\\\\x00-\\x1F] | \"\\\\\" [\"\\\\/nt]\n")
        append("ws ::= [ \\n]{0,2}\n")
    }

    data class Result(val windows: List<WindowMood>, val story: String)

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
                title = (m?.get("title") as? String)?.trim().orEmpty().take(60),
                source = SceneMerger.SOURCE_LLM,
            )
        }
        return Result(result, (root["story"] as? String).orEmpty().trim())
    }

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
