package io.github.hexotronic.lauscher.analysis.core

/** Kinds of steps in the AI log, grouped for the filter. */
enum class AiStep(val label: String, val group: AiGroup) {
    HEARD("Gehört", AiGroup.LISTEN),
    READ("Gelesen", AiGroup.LISTEN),
    ASKED("Frage", AiGroup.MODEL),
    ANSWER("Antwort", AiGroup.MODEL),
    SCENE("Szene", AiGroup.SCENES),
    CUT("Szenenwechsel", AiGroup.SCENES),
    MUSIC("Musik", AiGroup.MUSIC),
    INFO("Info", AiGroup.OTHER),
    PROBLEM("Problem", AiGroup.PROBLEMS),
}

enum class AiGroup(val label: String) {
    LISTEN("Zuhören"),
    MODEL("Sprachmodell"),
    SCENES("Szenen"),
    MUSIC("Musik"),
    PROBLEMS("Probleme"),
    OTHER("Sonstiges"),
}

/**
 * One thing the AI did. [positionMs] is the place in the book it is about (null if none),
 * [detail] the full text behind it (what was heard, the question to the model, its raw answer).
 */
data class AiLogEntry(
    val id: Long,
    val timeMs: Long,
    val step: AiStep,
    val text: String,
    val detail: String? = null,
    val bookId: Long? = null,
    val positionMs: Long? = null,
)

/** The last [capacity] entries; old ones drop out. Thread-safe. */
class AiLogBuffer(private val capacity: Int = 400) {
    private val entries = ArrayDeque<AiLogEntry>()
    private var nextId = 1L

    @Synchronized
    fun add(step: AiStep, text: String, detail: String?, bookId: Long?, positionMs: Long?, timeMs: Long): List<AiLogEntry> {
        entries.addLast(AiLogEntry(nextId++, timeMs, step, text, detail?.take(MAX_DETAIL), bookId, positionMs))
        while (entries.size > capacity) entries.removeFirst()
        return entries.toList()
    }

    @Synchronized
    fun clear(): List<AiLogEntry> {
        entries.clear()
        return emptyList()
    }

    @Synchronized
    fun snapshot(): List<AiLogEntry> = entries.toList()

    companion object {
        /** Prompts can be long; the log keeps this much of each detail. */
        const val MAX_DETAIL = 8_000
    }
}

object AiLogText {
    /** "12:03:45" of the day for [timeMs] in [zoneOffsetMs]. */
    fun timeOfDay(timeMs: Long, zoneOffsetMs: Long): String {
        val s = Math.floorMod((timeMs + zoneOffsetMs) / 1000, 86_400L)
        return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    /** One line: time, step, place in the book and text. */
    fun line(e: AiLogEntry, zoneOffsetMs: Long): String =
        "${timeOfDay(e.timeMs, zoneOffsetMs)} ${e.step.label}" + (e.positionMs?.let { " @${ScenePrompt.clock(it)}" } ?: "") + ": ${e.text}"

    /** The whole log as plain text (for copying), with the details indented below each line. */
    fun export(entries: List<AiLogEntry>, zoneOffsetMs: Long, withDetails: Boolean = true): String = buildString {
        for (e in entries) {
            appendLine(line(e, zoneOffsetMs))
            if (withDetails && e.detail != null) e.detail.lines().forEach { appendLine("    $it") }
        }
    }

    /** The start of [text] in quotes, cut at a word, for a one-line entry. */
    fun excerpt(text: String, max: Int = 140): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        return if (t.length <= max) "„$t“" else "„" + t.take(max).substringBeforeLast(' ') + " …“"
    }
}
