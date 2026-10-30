package io.github.hexotronic.lauscher.download.core

import java.io.ByteArrayOutputStream

/** What the listener typed or shared: a link, or words to search for. */
object LinkInput {
    private val URL = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)

    /** Media files the phone can decode directly (audio, or video with an audio track). */
    val MEDIA_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mp4", "m4v", "mkv", "webm", "mov", "3gp")

    /** The first link in [text] ("Schau mal: https://… "), without trailing punctuation. */
    fun firstUrl(text: String): String? = URL.find(text)?.value?.trimEnd('.', ',', ';', ')', ']', '!', '?')

    /** All links in [text], one per video, for pasting several at once. */
    fun allUrls(text: String): List<String> = URL.findAll(text).map { it.value.trimEnd('.', ',', ';', ')', ']', '!', '?') }.distinct().toList()

    fun extensionOf(url: String): String =
        url.substringBefore('#').substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun isDirectMedia(url: String): Boolean = extensionOf(url) in MEDIA_EXTENSIONS

    fun fileNameOf(url: String): String =
        java.net.URLDecoder.decode(url.substringBefore('#').substringBefore('?').substringAfterLast('/'), "UTF-8")
            .substringBeforeLast('.').ifBlank { "Download" }
}

object FileNames {
    private val FORBIDDEN = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

    /** A name that is valid on every file system the phone may use. */
    fun safe(name: String, max: Int = 100): String =
        name.replace(FORBIDDEN, "_").replace(Regex("\\s+"), " ").trim().trim('.', ' ').take(max).trim().ifEmpty { "Download" }

    /** "007 Kapitelname.mp3": numbered so that every file browser keeps the order. */
    fun track(index: Int, count: Int, title: String, extension: String): String {
        val digits = maxOf(2, count.toString().length)
        return "%0${digits}d %s.%s".format(index + 1, safe(title, 80), extension)
    }
}

/** One thing to download: a video, a track, a chapter. */
data class MediaItem(
    val url: String,
    val title: String,
    val uploader: String? = null,
    val durationSeconds: Long = -1,
    val thumbnail: String? = null,
)

/** What is saved: MP3 (converted), or the sound as the platform delivers it (no quality loss, faster). */
enum class OutputFormat(val label: String) {
    MP3("MP3"),
    ORIGINAL("Original (ohne Umwandlung)"),
}

enum class Mp3Quality(val label: String, val kbps: Int, val mono: Boolean) {
    SPEECH("Sprache · 64 kbit/s mono", 64, true),
    STANDARD("Standard · 128 kbit/s", 128, false),
    HIGH("Hoch · 192 kbit/s", 192, false),
    MAX("Maximal · 320 kbit/s", 320, false),
}

/** How selected items become books in the library. */
enum class Grouping(val label: String) {
    ONE_BOOK("Ein Hörbuch (Kapitel)"),
    EACH_BOOK("Jedes einzeln"),
}

data class PlannedBook(val title: String, val author: String?, val items: List<MediaItem>)

object DownloadPlan {
    /**
     * [ONE_BOOK][Grouping.ONE_BOOK] keeps the items as chapters of one book named [collectionTitle]
     * (playlist or search); otherwise every item becomes its own book.
     */
    fun books(items: List<MediaItem>, grouping: Grouping, collectionTitle: String?): List<PlannedBook> {
        if (items.isEmpty()) return emptyList()
        if (grouping == Grouping.EACH_BOOK || items.size == 1) {
            return items.map { PlannedBook(it.title, it.uploader, listOf(it)) }
        }
        val uploaders = items.mapNotNull { it.uploader }.distinct()
        return listOf(
            PlannedBook(
                title = collectionTitle?.takeIf { it.isNotBlank() } ?: items.first().title,
                author = uploaders.singleOrNull(),
                items = items,
            ),
        )
    }
}

/** ID3v2.3 tags for the MP3 files Lauscher writes, so other players show the right names. */
object Id3 {
    fun tag(title: String?, artist: String?, album: String?, track: Int? = null, trackCount: Int? = null, cover: ByteArray? = null): ByteArray {
        val frames = ByteArrayOutputStream()
        fun text(id: String, value: String?) {
            if (value.isNullOrBlank()) return
            val body = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
            frame(frames, id, body)
        }
        text("TIT2", title)
        text("TPE1", artist)
        text("TALB", album)
        if (track != null) text("TRCK", if (trackCount != null) "$track/$trackCount" else "$track")
        if (cover != null && cover.isNotEmpty()) {
            val mime = if (cover.size > 3 && cover[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
            // Encoding ISO-8859-1, MIME type, picture type 3 (front cover), empty description.
            frame(frames, "APIC", byteArrayOf(0) + mime.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 3, 0) + cover)
        }
        val body = frames.toByteArray()
        val size = body.size
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0,
            (size shr 21 and 0x7F).toByte(), (size shr 14 and 0x7F).toByte(), (size shr 7 and 0x7F).toByte(), (size and 0x7F).toByte(),
        )
        return header + body
    }

    private fun frame(out: ByteArrayOutputStream, id: String, body: ByteArray) {
        out.write(id.toByteArray(Charsets.ISO_8859_1))
        val n = body.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(byteArrayOf(0, 0))
        out.write(body)
    }
}
