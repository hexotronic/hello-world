package io.github.hexotronic.lauscher.analysis.core

import io.github.hexotronic.lauscher.voice.core.CommandMatcher
import io.github.hexotronic.lauscher.voice.core.VoiceAction
import io.github.hexotronic.lauscher.voice.core.VoiceCommands
import kotlin.math.ln

/** What the book assistant can do on the player besides answering. [hint] is for the language model. */
enum class AssistantAction(val label: String, val hint: String) {
    NONE("", "only answer the question"),
    PLAY("Weiter", "resume playback"),
    PAUSE("Pause", "pause playback"),
    REWIND("Zurück", "go back, value = seconds"),
    FORWARD("Vor", "skip ahead, value = seconds"),
    JUMP_TIME("Zu einer Zeit", "jump to a time in the book"),
    JUMP_CHAPTER("Zu einem Kapitel", "jump to a chapter, value = chapter number"),
    NEXT_CHAPTER("Nächstes Kapitel", "jump to the next chapter"),
    PREVIOUS_CHAPTER("Voriges Kapitel", "jump to the previous chapter"),
    FIND("Stelle suchen", "jump to the passage where something happens, value = 1 to 4 words that occur there"),
    SLEEP_TIMER("Schlaf-Timer", "start the sleep timer, value = minutes"),
    SLEEP_OFF("Schlaf-Timer aus", "stop the sleep timer"),
    BOOKMARK("Lesezeichen", "set a bookmark here, value = short name"),
    SPEED("Tempo", "set the playback speed, value = number like 1.25"),
    MUSIC_ON("Musik an", "switch the background music on"),
    MUSIC_OFF("Musik aus", "switch the background music off"),
    ;

    companion object {
        fun parse(name: String?): AssistantAction? = entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

/** An answer of the assistant and what it wants to do on the player ([value] depends on the action). */
data class AssistantReply(val answer: String, val action: AssistantAction = AssistantAction.NONE, val value: String = "") {
    /** The value as a number ("1,5" → 1.5), null if it is none. */
    val number: Float? get() = Regex("-?\\d+([.,]\\d+)?").find(value)?.value?.replace(',', '.')?.toFloatOrNull()
}

/** One question and answer of the conversation, given to the model for follow-up questions. */
data class AssistantTurn(val question: String, val answer: String)

/**
 * The side window "Frag das Buch": answers questions about the book from what was transcribed
 * (or the imported text) – by default only up to the listening position, so it never spoils – and
 * carries out commands. Simple commands are recognised without a language model.
 */
object BookAssistant {
    val SYSTEM = """
        You are the companion of an audiobook app. The listener asks about the book or tells the player what to do.
        Questions: answer only from the given excerpts and summary, in one to four full sentences in the language of
        the question (usually German). If they do not say it, say so honestly. Never reveal anything that happens after
        the listener's position unless excerpts after it are given.

        action: NONE for every question about the book (who, what, why, how …), even when it names a passage.
        Use another action only when the listener asks the player to do something:
        ${AssistantAction.entries.filter { it != AssistantAction.NONE }.joinToString("\n        ") { "${it.name} = ${it.hint}" }}
        value: what the action needs, else "". Times as h:mm:ss (an hour and a half = 1:30:00, ten minutes = 0:10:00);
        REWIND and FORWARD in seconds (minutes × 60); SLEEP_TIMER in minutes; SPEED as a number (normal = 1.0);
        JUMP_CHAPTER as the chapter number; FIND as one to four words that occur in the wanted passage.
        answer: the answer to the question, or for an action a short confirmation of what you do.
        Examples: "Mach in einer Viertelstunde aus" = SLEEP_TIMER "15"; "Geh zwei Minuten zurück" = REWIND "120";
        "Etwas langsamer bitte" = SPEED "0.9"; "Wer ist Mira?" = NONE "".
        Answer with JSON only.
    """.trimIndent()

    /** The prompt: book, summary, position, matching excerpts (with times), the last turns and the question. */
    fun user(
        bookTitle: String,
        author: String?,
        story: String?,
        positionMs: Long,
        chapterTitle: String?,
        chapterCount: Int,
        passages: List<TranscriptWindow>,
        history: List<AssistantTurn>,
        question: String,
        spoilerFree: Boolean,
    ): String = buildString {
        append("Book: \"").append(bookTitle).append('"')
        if (!author.isNullOrBlank()) append(" by ").append(author)
        append("\nListener's position: ").append(ScenePrompt.clock(positionMs))
        if (!chapterTitle.isNullOrBlank()) append(" (chapter \"").append(chapterTitle).append("\")")
        if (chapterCount > 1) append(", the book has ").append(chapterCount).append(" chapters")
        append(if (spoilerFree) "\nOnly what happened up to the position may be told." else "\nThe listener allows spoilers.")
        append("\nSummary so far: ").append(story?.takeIf { it.isNotBlank() } ?: "(none yet)")
        append("\n\nExcerpts:\n")
        if (passages.isEmpty()) append("(none found)\n")
        for (p in passages) {
            append('[').append(ScenePrompt.clock(p.startMs)).append("] ")
            append(ScenePrompt.excerpt(p.text, PASSAGE_CHARS)).append('\n')
        }
        if (history.isNotEmpty()) {
            append("\nConversation so far:\n")
            for (t in history.takeLast(MAX_HISTORY)) {
                append("Listener: ").append(t.question.take(200)).append('\n')
                append("You: ").append(t.answer.take(300)).append('\n')
            }
        }
        append("\nListener: ").append(question.trim())
    }

    /** GBNF grammar: {"action": ..., "value": "...", "answer": "..."}. */
    fun grammar(): String = buildString {
        append("root ::= \"{\" ws \"\\\"action\\\":\" ws action ws \",\" ws \"\\\"value\\\":\" ws value ws \",\" ws ")
        append("\"\\\"answer\\\":\" ws answer ws \"}\"\n")
        append("action ::= ").append(AssistantAction.entries.joinToString(" | ") { "\"\\\"${it.name}\\\"\"" }).append('\n')
        // Values are times, numbers or a few words: no braces or other scripts a confused model drifts into.
        append("value ::= \"\\\"\" [0-9A-Za-zÄÖÜäöüß :.,-]{0,32} \"\\\"\"\n")
        append("answer ::= \"\\\"\" char{1,600} \"\\\"\"\n")
        append("char ::= [^\"\\\\\\x00-\\x1F] | \"\\\\\" [\"\\\\/nt]\n")
        append("ws ::= [ \\n]{0,2}\n")
    }

    /** Words that start a question about the book (not "wo", which may mean "take me where …"). */
    private val ASKING = Regex("^(wer|wen|wem|wessen|was|warum|wieso|weshalb|wie|welche[rsmn]?|who|what|why|how|which)\\b", RegexOption.IGNORE_CASE)

    /**
     * A question about the book ("Wer …?", "Warum …?") does nothing in the player, whatever the model
     * chose (models sometimes answer "Wer ist Aelrin?" with a search, a small one even with "next chapter").
     */
    fun sanitize(question: String, reply: AssistantReply): AssistantReply {
        val q = question.trim()
        val asks = q.endsWith("?") && ASKING.containsMatchIn(q)
        return if (asks && reply.action != AssistantAction.NONE) reply.copy(action = AssistantAction.NONE, value = "") else reply
    }

    fun parse(output: String): AssistantReply? {
        val root = runCatching { MiniJson.parse(output.trim()) }.getOrNull() as? Map<*, *> ?: return null
        val answer = (root["answer"] as? String)?.trim().orEmpty()
        val action = AssistantAction.parse(root["action"] as? String) ?: AssistantAction.NONE
        val value = (root["value"] as? String)?.trim().orEmpty()
        if (answer.isEmpty() && action == AssistantAction.NONE) return null
        return AssistantReply(answer, action, value)
    }

    // ------------------------------------------------------------------ commands without a model

    private val voice = VoiceCommands(wakeWordEnabled = false)

    /**
     * A command the listener typed, recognised without the language model ("Kapitel 3", "zu 1:20:00",
     * "Schlaftimer 20 Minuten", "Tempo 1,5", "Musik aus", "Lesezeichen", "30 Sekunden zurück"), else null.
     */
    fun direct(text: String): AssistantReply? {
        val t = text.lowercase().trim().trimEnd('.', '!', '?')
        Regex("^(spring|springe|geh|gehe|zu|zum|zur)?\\s*(zum|zu|in)?\\s*kapitel\\s+(\\d+)$").find(t)?.let {
            return AssistantReply("Ich springe zu Kapitel ${it.groupValues[3]}.", AssistantAction.JUMP_CHAPTER, it.groupValues[3])
        }
        Regex("^(spring|springe|geh|gehe)?\\s*(zu|zur|nach|auf)?\\s*(minute\\s+)?(\\d{1,2}(:\\d{2}){1,2}|\\d{1,3})(\\s*min(uten)?)?$").find(t)
            ?.takeIf { m -> m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty() || ':' in m.groupValues[4] }
            ?.let { m ->
                val v = m.groupValues[4].let { if (':' in it) it else "$it:00" }
                return AssistantReply("Ich springe zu $v.", AssistantAction.JUMP_TIME, v)
            }
        Regex("^(tempo|geschwindigkeit|abspielgeschwindigkeit)\\s*(auf)?\\s*(\\d([.,]\\d+)?)\\s*(x|fach|-fach)?$").find(t)?.let {
            return AssistantReply("Tempo ${it.groupValues[3]}.", AssistantAction.SPEED, it.groupValues[3])
        }
        Regex("^(hintergrund)?musik\\s+(aus|an|ein)(schalten)?$").find(t)?.let {
            val on = it.groupValues[2] != "aus"
            return AssistantReply(if (on) "Hintergrundmusik an." else "Hintergrundmusik aus.", if (on) AssistantAction.MUSIC_ON else AssistantAction.MUSIC_OFF)
        }
        val r = CommandMatcher.match(t, voice) ?: return null
        return when (r.action) {
            VoiceAction.PLAY -> AssistantReply("Weiter geht's.", AssistantAction.PLAY)
            VoiceAction.PAUSE -> AssistantReply("Pause.", AssistantAction.PAUSE)
            VoiceAction.REWIND -> AssistantReply("Zurück.", AssistantAction.REWIND, r.seconds?.toString() ?: "")
            VoiceAction.FORWARD -> AssistantReply("Vor.", AssistantAction.FORWARD, r.seconds?.toString() ?: "")
            VoiceAction.NEXT_CHAPTER -> AssistantReply("Nächstes Kapitel.", AssistantAction.NEXT_CHAPTER)
            VoiceAction.PREVIOUS_CHAPTER -> AssistantReply("Voriges Kapitel.", AssistantAction.PREVIOUS_CHAPTER)
            VoiceAction.SLEEP_TIMER -> {
                val minutes = r.seconds?.let { if (CommandMatcher.hasUnit(r.heard)) it / 60 else it }
                AssistantReply("Schlaf-Timer" + (minutes?.let { " $it Minuten" } ?: "") + ".", AssistantAction.SLEEP_TIMER, minutes?.toString() ?: "")
            }
            VoiceAction.SLEEP_OFF -> AssistantReply("Schlaf-Timer aus.", AssistantAction.SLEEP_OFF)
            VoiceAction.BOOKMARK -> AssistantReply("Lesezeichen gesetzt.", AssistantAction.BOOKMARK)
            else -> null // louder, quieter, faster, where … are better answered by the model or the player
        }
    }

    /** "1:02:03", "12:30" or "75" (minutes) as milliseconds; null if it is no time. */
    fun parseTime(value: String): Long? {
        val parts = value.trim().split(':').map { it.trim().toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> parts[0] * 60_000L
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> null
        }
    }

    // ------------------------------------------------------------------ finding the passages

    private val STOP = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder", "aber", "ist", "war",
        "sind", "waren", "wird", "wurde", "hat", "hatte", "haben", "wer", "was", "wie", "wo", "wann", "warum", "wieso", "welche",
        "welcher", "welches", "mit", "von", "vom", "zu", "zum", "zur", "im", "in", "an", "am", "auf", "aus", "bei", "fur", "uber",
        "nach", "vor", "sich", "sie", "er", "es", "ich", "du", "wir", "ihr", "mir", "mich", "dir", "dich", "nicht", "noch", "auch",
        "so", "da", "dann", "denn", "doch", "nur", "schon", "mal", "bitte", "eigentlich", "genau", "buch", "hörbuch", "horbuch",
        "the", "a", "an", "of", "to", "and", "or", "is", "was", "who", "what", "where", "when", "why", "how", "in", "on", "at", "it",
    )

    /** Word stems for matching: lower case, umlauts folded, at most six letters. */
    fun terms(text: String): List<String> = CommandMatcher.words(text).filter { it.length > 2 && it !in STOP }.map { it.take(6) }

    /**
     * The [k] windows that fit [question] best (by the rarity of the shared words), in book order;
     * with [untilMs] only windows that start before it. The window at the position is always included.
     */
    fun passages(question: String, windows: List<TranscriptWindow>, untilMs: Long, k: Int = MAX_PASSAGES): List<TranscriptWindow> {
        val pool = clip(windows, untilMs).filter { it.text.isNotBlank() }
        if (pool.isEmpty()) return emptyList()
        val query = terms(question).distinct()
        val docs = pool.map { w -> w to terms(w.text) }
        val df = HashMap<String, Int>()
        for ((_, ts) in docs) ts.distinct().forEach { df[it] = (df[it] ?: 0) + 1 }
        val n = docs.size.toDouble()
        val scored = docs.map { (w, ts) ->
            val counts = ts.groupingBy { it }.eachCount()
            val score = query.sumOf { q ->
                val tf = counts[q] ?: 0
                if (tf == 0) 0.0 else (1 + ln(tf.toDouble())) * ln(1 + n / (df[q] ?: 1))
            }
            w to score
        }
        val current = pool.lastOrNull { it.startMs <= untilMs }
        val best = scored.filter { it.second > 0 }.sortedByDescending { it.second }.take(k).map { it.first }
        return (best + listOfNotNull(current)).distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    /**
     * The windows before [untilMs]; the one running at that moment only up to it (its words are
     * spread evenly over its time, so the part after the position is cut off at a word).
     */
    fun clip(windows: List<TranscriptWindow>, untilMs: Long): List<TranscriptWindow> = windows.filter { it.startMs < untilMs }.map { w ->
        if (w.endMs <= untilMs || w.endMs <= w.startMs) return@map w
        val share = (untilMs - w.startMs).toDouble() / (w.endMs - w.startMs)
        var cut = (w.text.length * share).toInt().coerceIn(0, w.text.length)
        while (cut > 0 && !w.text[cut - 1].isWhitespace()) cut--
        w.copy(endMs = untilMs, text = w.text.substring(0, cut).trim())
    }

    /** The first window (from [fromMs] on, in book order) whose text contains most of [words]. */
    fun find(words: String, windows: List<TranscriptWindow>, fromMs: Long = 0L): TranscriptWindow? {
        val wanted = terms(words).distinct()
        if (wanted.isEmpty()) return null
        val ordered = windows.sortedBy { it.startMs }.let { list -> list.filter { it.startMs >= fromMs } + list.filter { it.startMs < fromMs } }
        val scored = ordered.map { w -> w to terms(w.text).toSet().let { ts -> wanted.count { it in ts } } }
        val top = scored.maxOfOrNull { it.second } ?: 0
        return if (top == 0) null else scored.first { it.second == top }.first
    }

    const val MAX_PASSAGES = 4
    const val PASSAGE_CHARS = 700
    const val MAX_HISTORY = 3
}
