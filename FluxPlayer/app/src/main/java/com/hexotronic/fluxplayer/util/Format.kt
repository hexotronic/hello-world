package com.hexotronic.fluxplayer.util

import java.util.Locale
import kotlin.math.abs

object Format {
    fun time(ms: Long): String {
        if (ms < 0) return "--:--"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    fun speed(v: Float): String {
        val r = Math.round(v * 100) / 100.0
        val txt = if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.GERMANY, "%.2f", r).trimEnd('0')
        return txt + "x"
    }

    fun signedTime(ms: Long): String = (if (ms < 0) "−" else "+") + time(abs(ms))

    fun size(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.GERMANY, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.GERMANY, "%.1f MB", mb)
            else -> String.format(Locale.GERMANY, "%.0f KB", kb)
        }
    }

    fun resolution(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return ""
        val p = minOf(w, h)
        return when {
            p >= 2100 -> "4K"
            p >= 1400 -> "1440p"
            p >= 1000 -> "1080p"
            p >= 700 -> "720p"
            else -> "${p}p"
        }
    }

    fun ago(timestamp: Long): String {
        val diff = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            diff < 60 -> "gerade eben"
            diff < 3600 -> "vor ${diff / 60} Min."
            diff < 86400 -> "vor ${diff / 3600} Std."
            diff < 86400 * 30 -> "vor ${diff / 86400} Tagen"
            else -> "vor langer Zeit"
        }
    }
}
