package io.github.hexotronic.lauscher.analysis.core

import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs

/** The written text of an audiobook, with the character offsets where chapters begin. */
data class BookText(val text: String, val chapters: List<TextChapter>) {
    data class TextChapter(val title: String, val start: Int)
}

/** A known point in time of the audiobook and the text position read there. */
data class Anchor(val timeMs: Long, val char: Int)

/** Reads book texts from plain text, HTML and EPUB files. */
object BookTextParser {
    private val headingRegex = Regex(
        "^\\s*(kapitel|chapter|teil|part|prolog|prologue|epilog|epilogue|vorwort|nachwort)\\b.{0,60}$|^\\s*([0-9]{1,3}|[IVXLC]{1,7})\\.?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Plain text: chapters are lines like "Kapitel 3", "Chapter IV", "Prolog" or a lone number. */
    fun fromPlain(raw: String): BookText {
        val text = normalize(raw)
        val chapters = ArrayList<BookText.TextChapter>()
        var offset = 0
        for (line in text.split('\n')) {
            if (line.length <= 70 && line.isNotBlank() && headingRegex.containsMatchIn(line)) {
                chapters.add(BookText.TextChapter(line.trim(), offset))
            }
            offset += line.length + 1
        }
        return BookText(text, chapters)
    }

    /** HTML to text: block elements become line breaks, entities are decoded. */
    fun htmlToText(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</?(p|div|h[1-6]|li|section|article|blockquote|tr|title)[^>]*>"), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")
        s = Regex("&#(x?)([0-9a-fA-F]+);").replace(s) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.let { String(Character.toChars(it)) } ?: ""
        }
        return s
    }

    /** EPUB: the documents in reading order; each document of some length starts a chapter. */
    fun fromEpub(input: InputStream): BookText {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) files[entry.name] = zip.readBytes()
            }
        }
        val container = files["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val opfPath = container?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
            ?: files.keys.firstOrNull { it.endsWith(".opf") }
            ?: error("Keine gültige EPUB-Datei")
        val opf = files[opfPath]?.toString(Charsets.UTF_8) ?: error("Keine gültige EPUB-Datei")
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val manifest = Regex("<item\\b[^>]*>").findAll(opf).associate { m ->
            val tag = m.value
            attr(tag, "id") to attr(tag, "href")
        }
        val spine = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        val sb = StringBuilder()
        val chapters = ArrayList<BookText.TextChapter>()
        for (id in spine) {
            val href = manifest[id] ?: continue
            val path = resolve(base, java.net.URLDecoder.decode(href, "UTF-8"))
            val html = files[path]?.toString(Charsets.UTF_8) ?: continue
            val part = normalize(htmlToText(html)).trim()
            if (part.isEmpty()) continue
            if (part.length >= MIN_CHAPTER_CHARS) {
                chapters.add(BookText.TextChapter(part.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty(), sb.length))
            }
            sb.append(part).append("\n\n")
        }
        return BookText(sb.toString().trimEnd(), chapters)
    }

    private fun attr(tag: String, name: String) = Regex("\\b$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1).orEmpty()

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        for (p in (base + href).split('/')) {
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    /** Unifies line breaks and spaces, keeps paragraph breaks. */
    fun normalize(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n')
            .replace(Regex("[\\t\\u00A0 ]+"), " ")
            .replace(Regex(" ?\n ?"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private const val MIN_CHAPTER_CHARS = 1500
}

/**
 * Maps time in the audiobook to positions in its text. Anchors come from matching chapters,
 * from short speech samples found in the text, or – without either – from the assumption of
 * an even reading speed. Between anchors positions are interpolated.
 */
object TextAligner {
    data class AudioChapter(val title: String, val startMs: Long)

    /** Anchors from chapter starts when text and audio chapters correspond, else just start and end. */
    fun initialAnchors(text: BookText, audio: List<AudioChapter>, durationMs: Long): List<Anchor> {
        val anchors = ArrayList<Anchor>()
        anchors.add(Anchor(0, 0))
        val tc = text.chapters
        if (audio.size >= 2 && tc.size >= 2) {
            // First chapters whose numbers or titles match, in order.
            val matched = ArrayList<Anchor>()
            var next = 0
            for (ch in audio) {
                val key = chapterKey(ch.title) ?: continue
                val idx = (next until tc.size).firstOrNull { chapterKey(tc[it].title) == key } ?: continue
                matched.add(Anchor(ch.startMs, tc[idx].start))
                next = idx + 1
            }
            if (matched.size >= maxOf(2, audio.size * 6 / 10)) {
                anchors.addAll(matched)
            } else if (tc.size - audio.size in 0..1) {
                // Same number (or one extra text part such as a foreword): chapter by chapter.
                val offset = tc.size - audio.size
                audio.forEachIndexed { i, ch -> anchors.add(Anchor(ch.startMs, tc[i + offset].start)) }
            }
        }
        anchors.add(Anchor(durationMs, text.text.length))
        return clean(anchors)
    }

    /** "Kapitel 12", "12.", "Chapter XII" → "12"; titles without a number → their words. */
    fun chapterKey(title: String): String? {
        val t = title.lowercase()
        Regex("\\b(\\d{1,3})\\b").find(t)?.let { return it.groupValues[1].toInt().toString() }
        Regex("\\b([ivxlc]{1,7})\\b").find(t.replace(Regex("^(kapitel|chapter|teil|part)\\s+"), ""))?.let { m ->
            roman(m.groupValues[1])?.let { return it.toString() }
        }
        val words = t.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().replace(Regex("\\s+"), " ")
        return words.takeIf { it.length >= 4 }
    }

    private fun roman(s: String): Int? {
        val v = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100)
        var total = 0
        for (i in s.indices) {
            val cur = v[s[i]] ?: return null
            val next = if (i + 1 < s.length) v[s[i + 1]] ?: 0 else 0
            total += if (cur < next) -cur else cur
        }
        return total.takeIf { it > 0 }
    }

    /** Sorted by time and strictly increasing in both time and text position. */
    fun clean(anchors: List<Anchor>): List<Anchor> {
        val sorted = anchors.sortedWith(compareBy({ it.timeMs }, { it.char }))
        val out = ArrayList<Anchor>()
        for (a in sorted) {
            val last = out.lastOrNull()
            if (last == null || (a.timeMs > last.timeMs && a.char > last.char)) out.add(a)
        }
        return out
    }

    /** Adds a measured anchor; anchors it contradicts (out of order) are dropped, the new one wins. */
    fun withAnchor(anchors: List<Anchor>, anchor: Anchor): List<Anchor> {
        val kept = anchors.filter { (it.timeMs < anchor.timeMs && it.char < anchor.char) || (it.timeMs > anchor.timeMs && it.char > anchor.char) }
        return clean(kept + anchor)
    }

    /** Text position at [timeMs], interpolated between the anchors. */
    fun charAt(anchors: List<Anchor>, timeMs: Long): Int {
        if (anchors.isEmpty()) return 0
        if (timeMs <= anchors.first().timeMs) return anchors.first().char
        for (i in 1 until anchors.size) {
            val a = anchors[i - 1]
            val b = anchors[i]
            if (timeMs <= b.timeMs) {
                val f = (timeMs - a.timeMs).toDouble() / (b.timeMs - a.timeMs).coerceAtLeast(1)
                return (a.char + f * (b.char - a.char)).toInt()
            }
        }
        return anchors.last().char
    }

    /** The text read between [startMs] and [endMs], cut at word boundaries. */
    fun slice(text: String, anchors: List<Anchor>, startMs: Long, endMs: Long): String {
        var from = charAt(anchors, startMs).coerceIn(0, text.length)
        var to = charAt(anchors, endMs).coerceIn(from, text.length)
        while (from > 0 && !text[from - 1].isWhitespace() && from > to - 1000) from--
        while (to < text.length && !text[to].isWhitespace() && to < from + 100_000) to++
        return text.substring(from, to).trim()
    }

    /** Words of a text with their character offsets, for matching heard speech. */
    class Index(val text: String) {
        val words: List<String>
        val offsets: IntArray

        init {
            val w = ArrayList<String>()
            val o = ArrayList<Int>()
            for (m in WORD.findAll(text)) {
                w.add(normalizeWord(m.value))
                o.add(m.range.first)
            }
            words = w
            offsets = o.toIntArray()
        }

        /** Index of the first word at or after [char]. */
        fun wordAt(char: Int): Int {
            var lo = 0
            var hi = offsets.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (offsets[mid] < char) lo = mid + 1 else hi = mid
            }
            return lo
        }
    }

    /**
     * Finds where the [heard] words (a short speech sample) occur in the text, searching around
     * [expectedChar] within [radiusChars]. Returns the text position of the start of the match,
     * or null when nothing matches convincingly.
     */
    fun locate(index: Index, heard: String, expectedChar: Int, radiusChars: Int): Int? {
        val heardWords = WORD.findAll(heard).map { normalizeWord(it.value) }.filter { it.length >= 2 }.toList()
        if (heardWords.size < 4) return null
        val grams = HashMap<String, Int>()
        for (i in 0 until heardWords.size - 1) grams.putIfAbsent(heardWords[i] + " " + heardWords[i + 1], i)
        val from = index.wordAt((expectedChar - radiusChars).coerceAtLeast(0))
        val to = index.wordAt((expectedChar + radiusChars).coerceAtMost(index.text.length))
        // Every matching word pair votes for where the sample starts in the text; votes a few
        // words apart count together, since recognition skips or merges words.
        val votes = HashMap<Int, Int>()
        for (j in from until minOf(to, index.words.size - 1)) {
            val pos = grams[index.words[j] + " " + index.words[j + 1]] ?: continue
            votes.merge(j - pos, 1, Int::plus)
        }
        var bestStart = -1
        var bestScore = 0
        for (start in votes.keys) {
            val score = (start - 3..start + 3).sumOf { votes[it] ?: 0 }
            if (score > bestScore || (score == bestScore && start < bestStart)) {
                bestScore = score
                bestStart = start
            }
        }
        if (bestStart < 0 || bestScore < maxOf(3, heardWords.size / 5)) return null
        return index.offsets[bestStart.coerceIn(0, index.words.size - 1)]
    }

    /** Lowercase without accents and punctuation, so "Mädchen," and "madchen" match. */
    fun normalizeWord(w: String): String =
        java.text.Normalizer.normalize(w.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private val WORD = Regex("[\\p{L}\\p{N}]+")

    /** The words of [text], normalised for comparisons. */
    fun words(text: String): List<String> = WORD.findAll(text).map { normalizeWord(it.value) }.toList()

    /**
     * Share of the words of [reference] that [other] recognised in the same order (longest common
     * subsequence). Compares a sped-up transcript with the one at normal speed.
     */
    fun agreement(reference: String, other: String): Float {
        val a = words(reference)
        val b = words(other)
        if (a.isEmpty()) return 0f
        var prev = IntArray(b.size + 1)
        var cur = IntArray(b.size + 1)
        for (x in a) {
            for (j in b.indices) cur[j + 1] = if (x == b[j]) prev[j] + 1 else maxOf(prev[j + 1], cur[j])
            prev = cur.also { cur = prev }
        }
        return prev[b.size].toFloat() / a.size
    }

    /** Distance in ms from [timeMs] to the nearest anchor; decides where a speech sample is worth it. */
    fun nearestAnchorDistance(anchors: List<Anchor>, timeMs: Long): Long =
        anchors.minOfOrNull { abs(it.timeMs - timeMs) } ?: Long.MAX_VALUE
}
