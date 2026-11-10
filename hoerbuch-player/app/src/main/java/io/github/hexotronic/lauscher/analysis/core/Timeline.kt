package io.github.hexotronic.lauscher.analysis.core

/** How far an analysis started by the listener reaches. */
enum class RangeEnd(val label: String) {
    MIN_15("15 Min."),
    HOUR("1 Std."),
    CHAPTER("Bis Kapitelende"),
    END("Bis zum Ende"),
}

/** Time arithmetic for the mood map and the analysis range picker. */
object Timeline {
    /** Share of [fromMs]..[untilMs] that [scenes] cover (0..1). */
    fun coverage(scenes: List<Scene>, fromMs: Long, untilMs: Long): Float {
        if (untilMs <= fromMs) return 1f
        var covered = 0L
        for (s in scenes) {
            val a = maxOf(s.startMs, fromMs)
            val b = minOf(s.endMs, untilMs)
            if (b > a) covered += b - a
        }
        return (covered.toFloat() / (untilMs - fromMs)).coerceIn(0f, 1f)
    }

    /**
     * Start of the first part at or after [fromMs] that has no scene yet (gaps up to a second are
     * ignored); null if everything up to [durationMs] is analysed.
     */
    fun firstGap(scenes: List<Scene>, fromMs: Long, durationMs: Long): Long? {
        var t = fromMs.coerceIn(0, durationMs)
        for (s in scenes.sortedBy { it.startMs }) {
            if (s.endMs <= t) continue
            if (s.startMs - t > GAP_TOLERANCE_MS) return t
            t = maxOf(t, s.endMs)
        }
        return if (durationMs - t > SceneMerger.END_TAIL_MS) t else null
    }

    /** Where an analysis from [fromMs] ends for [end]; [chapterStarts] sorted, the first at 0. */
    fun rangeEnd(end: RangeEnd, fromMs: Long, chapterStarts: List<Long>, durationMs: Long): Long = when (end) {
        RangeEnd.MIN_15 -> minOf(durationMs, fromMs + 15 * 60_000L)
        RangeEnd.HOUR -> minOf(durationMs, fromMs + 60 * 60_000L)
        RangeEnd.CHAPTER -> chapterStarts.firstOrNull { it > fromMs } ?: durationMs
        RangeEnd.END -> durationMs
    }

    /** The stretch of [spanMs] around [centerMs], kept inside the book. */
    fun window(centerMs: Long, spanMs: Long, durationMs: Long): LongRange {
        if (durationMs <= 0) return 0L..0L
        val span = spanMs.coerceIn(1L, durationMs)
        val start = (centerMs - span / 2).coerceIn(0L, durationMs - span)
        return start..(start + span)
    }

    /** Round times between [fromMs] and [untilMs] for axis labels, at most [maxTicks] of them. */
    fun ticks(fromMs: Long, untilMs: Long, maxTicks: Int): List<Long> {
        if (untilMs <= fromMs || maxTicks < 1) return emptyList()
        val span = untilMs - fromMs
        val step = STEPS.firstOrNull { span / it <= maxTicks } ?: (span / maxTicks + 1)
        val first = (fromMs + step - 1) / step * step
        return generateSequence(first) { it + step }.takeWhile { it <= untilMs }.toList()
    }

    /** "12:34" or "1:02:03" like the player shows it. */
    fun clock(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private const val GAP_TOLERANCE_MS = 1_000L
    private val STEPS = listOf(10_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L, 15 * 60_000L, 30 * 60_000L, 3_600_000L, 2 * 3_600_000L, 5 * 3_600_000L)
}
