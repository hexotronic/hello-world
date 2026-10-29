package io.github.hexotronic.lauscher.scan.core

import java.nio.charset.Charset

/** Minimal random access abstraction so the parsers work on files and SAF documents alike. */
interface RandomAccessSource {
    val length: Long

    /** Reads up to [count] bytes at [position]. Returns the number of bytes read, -1 at EOF. */
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int
}

fun RandomAccessSource.readFully(position: Long, buffer: ByteArray, offset: Int = 0, count: Int = buffer.size): Boolean {
    var done = 0
    while (done < count) {
        val n = read(position + done, buffer, offset + done, count - done)
        if (n <= 0) return false
        done += n
    }
    return true
}

class ByteArraySource(private val data: ByteArray) : RandomAccessSource {
    override val length: Long get() = data.size.toLong()
    override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(count.toLong(), data.size - position).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, n)
        return n
    }
}

data class RawChapter(val startMs: Long, val title: String)

private fun u16(d: ByteArray, p: Int) = ((d[p].toInt() and 0xff) shl 8) or (d[p + 1].toInt() and 0xff)

private fun u32(d: ByteArray, p: Int): Long =
    ((d[p].toLong() and 0xff) shl 24) or ((d[p + 1].toLong() and 0xff) shl 16) or
        ((d[p + 2].toLong() and 0xff) shl 8) or (d[p + 3].toLong() and 0xff)

private fun u64(d: ByteArray, p: Int): Long = (u32(d, p) shl 32) or u32(d, p + 4)

/**
 * Reads chapter marks from MP4/M4A/M4B files. Supports both the Nero `chpl` atom and
 * QuickTime chapter text tracks (`tref/chap`), which together cover files made by ffmpeg,
 * iTunes, Audible converters and most audiobook tools.
 */
object Mp4ChapterParser {
    private const val MAX_MOOV_BYTES = 64L * 1024 * 1024
    private const val MAX_CHAPTERS = 10_000

    private class Box(val type: String, val start: Int, val end: Int)

    fun parse(source: RandomAccessSource): List<RawChapter> {
        val moov = findTopLevelMoov(source) ?: return emptyList()
        val data = moov
        val root = children(data, 0, data.size)
        val nero = root.firstOrNull { it.type == "udta" }
            ?.let { udta -> children(data, udta.start, udta.end).firstOrNull { it.type == "chpl" } }
            ?.let { parseChpl(data, it) }
            .orEmpty()
        val quickTime = runCatching { parseQuickTime(source, data, root) }.getOrDefault(emptyList())
        val best = if (quickTime.size >= nero.size && quickTime.isNotEmpty()) quickTime else nero
        return best.sortedBy { it.startMs }.distinctBy { it.startMs }
    }

    private fun findTopLevelMoov(source: RandomAccessSource): ByteArray? {
        val header = ByteArray(16)
        var pos = 0L
        while (pos + 8 <= source.length) {
            if (!source.readFully(pos, header, 0, 8)) return null
            var size = u32(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (size == 1L) {
                if (!source.readFully(pos + 8, header, 8, 8)) return null
                size = u64(header, 8)
                headerSize = 16
            } else if (size == 0L) {
                size = source.length - pos
            }
            if (size < headerSize) return null
            if (type == "moov") {
                val payload = size - headerSize
                if (payload > MAX_MOOV_BYTES) return null
                val data = ByteArray(payload.toInt())
                return if (source.readFully(pos + headerSize, data)) data else null
            }
            pos += size
        }
        return null
    }

    private fun children(d: ByteArray, start: Int, end: Int): List<Box> {
        val out = ArrayList<Box>()
        var p = start
        while (p + 8 <= end) {
            var size = u32(d, p)
            val type = String(d, p + 4, 4, Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                if (p + 16 > end) break
                size = u64(d, p + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - p).toLong()
            }
            if (size < header || p + size > end) break
            out.add(Box(type, p + header, (p + size).toInt()))
            p += size.toInt()
        }
        return out
    }

    private fun child(d: ByteArray, box: Box?, type: String): Box? =
        box?.let { b -> children(d, b.start, b.end).firstOrNull { it.type == type } }

    private fun parseChpl(d: ByteArray, box: Box): List<RawChapter> {
        var p = box.start
        if (p + 5 > box.end) return emptyList()
        val version = d[p].toInt() and 0xff
        p += 4
        if (version != 0) p += 4
        if (p >= box.end) return emptyList()
        val count = d[p].toInt() and 0xff
        p += 1
        val out = ArrayList<RawChapter>()
        for (i in 0 until count) {
            if (p + 9 > box.end) break
            val start = u64(d, p)
            p += 8
            val len = d[p].toInt() and 0xff
            p += 1
            if (p + len > box.end) break
            val title = String(d, p, len, Charsets.UTF_8).trim()
            p += len
            out.add(RawChapter(start / 10_000, title))
        }
        return out
    }

    private fun trackId(d: ByteArray, trak: Box): Long? {
        val tkhd = child(d, trak, "tkhd") ?: return null
        val version = d[tkhd.start].toInt() and 0xff
        val p = tkhd.start + if (version == 1) 20 else 12
        return if (p + 4 <= tkhd.end) u32(d, p) else null
    }

    private fun parseQuickTime(source: RandomAccessSource, d: ByteArray, root: List<Box>): List<RawChapter> {
        val traks = root.filter { it.type == "trak" }
        val chapterIds = traks.asSequence()
            .mapNotNull { child(d, child(d, it, "tref"), "chap") }
            .flatMap { chap -> (chap.start until chap.end - 3 step 4).asSequence().map { u32(d, it) } }
            .toSet()
        if (chapterIds.isEmpty()) return emptyList()
        val trak = traks.firstOrNull { trackId(d, it) in chapterIds } ?: return emptyList()
        val mdia = child(d, trak, "mdia") ?: return emptyList()
        val mdhd = child(d, mdia, "mdhd") ?: return emptyList()
        val mdhdVersion = d[mdhd.start].toInt() and 0xff
        val timescale = u32(d, mdhd.start + if (mdhdVersion == 1) 20 else 12)
        if (timescale <= 0) return emptyList()
        val stbl = child(d, child(d, mdia, "minf"), "stbl") ?: return emptyList()

        // Sample start times.
        val stts = child(d, stbl, "stts") ?: return emptyList()
        val starts = ArrayList<Long>()
        var t = 0L
        val sttsCount = u32(d, stts.start + 4).toInt()
        for (i in 0 until sttsCount) {
            val p = stts.start + 8 + i * 8
            if (p + 8 > stts.end) break
            val n = u32(d, p)
            val delta = u32(d, p + 4)
            for (j in 0 until n) {
                if (starts.size >= MAX_CHAPTERS) break
                starts.add(t)
                t += delta
            }
        }

        // Sample sizes.
        val stsz = child(d, stbl, "stsz") ?: return emptyList()
        val fixedSize = u32(d, stsz.start + 4)
        val sampleCount = minOf(u32(d, stsz.start + 8).toInt(), MAX_CHAPTERS)
        val sizes = LongArray(sampleCount) { i ->
            if (fixedSize != 0L) fixedSize else u32(d, stsz.start + 12 + i * 4)
        }

        // Chunk offsets and sample-to-chunk mapping.
        val stco = child(d, stbl, "stco")
        val co64 = child(d, stbl, "co64")
        val chunkOffsets: LongArray = when {
            stco != null -> LongArray(u32(d, stco.start + 4).toInt()) { u32(d, stco.start + 8 + it * 4) }
            co64 != null -> LongArray(u32(d, co64.start + 4).toInt()) { u64(d, co64.start + 8 + it * 8) }
            else -> return emptyList()
        }
        val stsc = child(d, stbl, "stsc") ?: return emptyList()
        val stscCount = u32(d, stsc.start + 4).toInt()
        val firstChunks = IntArray(stscCount) { u32(d, stsc.start + 8 + it * 12).toInt() }
        val perChunk = IntArray(stscCount) { u32(d, stsc.start + 12 + it * 12).toInt() }

        val offsets = LongArray(sampleCount)
        var sample = 0
        for (chunk in chunkOffsets.indices) {
            val chunkNumber = chunk + 1
            var entry = 0
            while (entry + 1 < stscCount && firstChunks[entry + 1] <= chunkNumber) entry++
            var offset = chunkOffsets[chunk]
            val n = if (stscCount > 0) perChunk[entry] else 1
            for (j in 0 until n) {
                if (sample >= sampleCount) break
                offsets[sample] = offset
                offset += sizes[sample]
                sample++
            }
            if (sample >= sampleCount) break
        }

        val out = ArrayList<RawChapter>()
        val header = ByteArray(2)
        for (i in 0 until minOf(sample, starts.size)) {
            if (!source.readFully(offsets[i], header)) break
            val len = u16(header, 0)
            val title = if (len in 1..(sizes[i] - 2).toInt().coerceAtLeast(0)) {
                val text = ByteArray(len)
                if (!source.readFully(offsets[i] + 2, text)) break
                decodeQuickTimeText(text)
            } else {
                ""
            }
            out.add(RawChapter(starts[i] * 1000 / timescale, title))
        }
        return out
    }

    private fun decodeQuickTimeText(b: ByteArray): String {
        if (b.size >= 2 && (b[0].toInt() and 0xff) == 0xFE && (b[1].toInt() and 0xff) == 0xFF) {
            return String(b, 2, b.size - 2, Charsets.UTF_16BE).trim()
        }
        if (b.size >= 2 && (b[0].toInt() and 0xff) == 0xFF && (b[1].toInt() and 0xff) == 0xFE) {
            return String(b, 2, b.size - 2, Charsets.UTF_16LE).trim()
        }
        return String(b, Charsets.UTF_8).trim()
    }
}

/** Reads ID3v2.3/2.4 `CHAP` frames (MP3 audiobooks and podcasts). */
object Id3ChapterParser {
    private const val MAX_TAG_BYTES = 64 * 1024 * 1024

    fun parse(source: RandomAccessSource): List<RawChapter> {
        val header = ByteArray(10)
        if (!source.readFully(0, header)) return emptyList()
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return emptyList()
        }
        val major = header[3].toInt()
        if (major != 3 && major != 4) return emptyList()
        val flags = header[5].toInt() and 0xff
        val size = synchsafe(header, 6)
        if (size <= 0 || size > MAX_TAG_BYTES) return emptyList()
        var tag = ByteArray(size)
        if (!source.readFully(10, tag)) return emptyList()
        if (major == 3 && flags and 0x80 != 0) tag = removeUnsync(tag, 0, tag.size)

        var p = 0
        if (flags and 0x40 != 0) {
            p = if (major == 3) 4 + u32(tag, 0).toInt() else synchsafe(tag, 0)
        }
        val chapters = ArrayList<RawChapter>()
        parseFrames(tag, p, tag.size, major) { id, body ->
            if (id == "CHAP") parseChap(body, major)?.let { chapters.add(it) }
        }
        return chapters.sortedBy { it.startMs }.distinctBy { it.startMs }
    }

    private fun parseFrames(d: ByteArray, start: Int, end: Int, major: Int, onFrame: (String, ByteArray) -> Unit) {
        var p = start
        while (p + 10 <= end) {
            if (d[p].toInt() == 0) break
            val id = String(d, p, 4, Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break
            val frameSize = if (major == 4) synchsafe(d, p + 4) else u32(d, p + 4).toInt()
            val formatFlags = d[p + 9].toInt() and 0xff
            if (frameSize < 0 || p + 10 + frameSize > end) break
            var bodyStart = p + 10
            var bodyEnd = bodyStart + frameSize
            var body: ByteArray
            if (major == 4) {
                if (formatFlags and 0x01 != 0) bodyStart += 4
                body = if (formatFlags and 0x02 != 0) {
                    removeUnsync(d, bodyStart, bodyEnd)
                } else {
                    d.copyOfRange(bodyStart, bodyEnd)
                }
            } else {
                body = d.copyOfRange(bodyStart, bodyEnd)
            }
            onFrame(id, body)
            p += 10 + frameSize
        }
    }

    private fun parseChap(b: ByteArray, major: Int): RawChapter? {
        var p = 0
        while (p < b.size && b[p].toInt() != 0) p++
        p++
        if (p + 16 > b.size) return null
        val start = u32(b, p)
        p += 16
        var title = ""
        parseFrames(b, p, b.size, major) { id, body ->
            if (id == "TIT2" && title.isEmpty()) title = decodeText(body)
        }
        return RawChapter(start, title)
    }

    fun decodeText(body: ByteArray): String {
        if (body.isEmpty()) return ""
        val charset: Charset = when (body[0].toInt()) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(body, 1, body.size - 1, charset).split('\u0000').firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun synchsafe(d: ByteArray, p: Int): Int =
        ((d[p].toInt() and 0x7f) shl 21) or ((d[p + 1].toInt() and 0x7f) shl 14) or
            ((d[p + 2].toInt() and 0x7f) shl 7) or (d[p + 3].toInt() and 0x7f)

    private fun removeUnsync(d: ByteArray, start: Int, end: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(end - start)
        var i = start
        while (i < end) {
            val b = d[i]
            out.write(b.toInt())
            if ((b.toInt() and 0xff) == 0xff && i + 1 < end && d[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }
}
