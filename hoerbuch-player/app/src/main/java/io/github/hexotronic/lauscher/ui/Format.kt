package io.github.hexotronic.lauscher.ui

import java.util.Locale

private val German = Locale.GERMANY

/** 1:02:03 or 12:34 */
fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(German, "%d:%02d:%02d", h, m, s) else String.format(German, "%d:%02d", m, s)
}

/** "3 Std. 12 Min.", "12 Min.", "45 Sek." */
fun formatHuman(ms: Long): String {
    val totalMin = ms.coerceAtLeast(0) / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h Std. $m Min."
        h > 0 -> "$h Std."
        m > 0 -> "$m Min."
        else -> "${ms.coerceAtLeast(0) / 1000} Sek."
    }
}

/** Compact hours for statistics: "2,5 Std." */
fun formatHours(ms: Long): String {
    val hours = ms / 3_600_000.0
    return when {
        hours >= 10 -> String.format(German, "%.0f Std.", hours)
        hours >= 1 -> String.format(German, "%.1f Std.", hours)
        else -> "${ms / 60_000} Min."
    }
}

fun formatSpeed(speed: Float): String {
    val text = String.format(German, "%.2f", speed).trimEnd('0').trimEnd(',')
    return "$text×"
}

fun formatPercent(value: Float): String = "${(value * 100).toInt()} %"

fun formatDb(value: Float): String = String.format(German, "%+.1f dB", value)

fun formatHz(hz: Int): String = when {
    hz == 0 -> "Aus"
    hz >= 1000 -> String.format(German, "%.1f kHz", hz / 1000f).replace(",0 ", " ")
    else -> "$hz Hz"
}
