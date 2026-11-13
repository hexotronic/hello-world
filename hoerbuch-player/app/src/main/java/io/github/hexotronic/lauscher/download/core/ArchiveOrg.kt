package io.github.hexotronic.lauscher.download.core

import io.github.hexotronic.lauscher.analysis.core.MiniJson
import java.net.URLEncoder

/**
 * Free audiobooks: the public-domain recordings of LibriVox, hosted by the Internet Archive.
 * Builds the search and metadata requests and reads their JSON answers.
 */
object ArchiveOrg {
    enum class Language(val label: String, val query: String?) {
        GERMAN("Deutsch", "(German OR ger OR deu OR Deutsch)"),
        ENGLISH("Englisch", "(English OR eng)"),
        ALL("Alle Sprachen", null),
    }

    data class Item(val id: String, val title: String, val author: String?, val language: String?, val downloads: Long)

    data class SearchPage(val total: Int, val items: List<Item>)

    data class File(val name: String, val url: String, val title: String?, val track: Int?, val seconds: Double?, val bytes: Long?)

    data class Book(
        val id: String,
        val title: String,
        val author: String?,
        val description: String?,
        val files: List<File>,
        val coverUrl: String,
    ) {
        val seconds: Double get() = files.sumOf { it.seconds ?: 0.0 }
        val bytes: Long get() = files.sumOf { it.bytes ?: 0L }
    }

    /** LibriVox MP3 sets, in order of preference (64 kbit/s is LibriVox' standard for speech). */
    val MP3_FORMATS = listOf("64Kbps MP3", "128Kbps MP3", "VBR MP3", "MP3")

    fun searchUrl(text: String, language: Language, page: Int = 1, rows: Int = 30): String {
        val parts = mutableListOf("collection:(librivoxaudio)")
        language.query?.let { parts += "language:$it" }
        val words = text.replace(SPECIAL, " ").split(' ').filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val terms = words.joinToString(" AND ")
            parts += "(title:($terms) OR creator:($terms))"
        }
        val fields = listOf("identifier", "title", "creator", "language", "downloads")
        return "https://archive.org/advancedsearch.php?q=" + enc(parts.joinToString(" AND ")) +
            fields.joinToString("") { "&" + enc("fl[]") + "=" + it } +
            "&" + enc("sort[]") + "=" + enc("downloads desc") + "&rows=$rows&page=$page&output=json"
    }

    fun parseSearch(json: String): SearchPage {
        val response = (MiniJson.parse(json) as? Map<*, *>)?.get("response") as? Map<*, *> ?: return SearchPage(0, emptyList())
        val docs = response["docs"] as? List<*> ?: emptyList<Any>()
        val items = docs.mapNotNull { d ->
            val m = d as? Map<*, *> ?: return@mapNotNull null
            val id = m["identifier"] as? String ?: return@mapNotNull null
            Item(
                id = id,
                title = text(m["title"]) ?: id,
                author = text(m["creator"]),
                language = text(m["language"]),
                downloads = (m["downloads"] as? Number)?.toLong() ?: 0,
            )
        }
        return SearchPage((response["numFound"] as? Number)?.toInt() ?: items.size, items)
    }

    fun metadataUrl(id: String) = "https://archive.org/metadata/" + enc(id)

    fun downloadUrl(id: String, name: String) =
        "https://archive.org/download/" + enc(id) + "/" + name.split('/').joinToString("/") { enc(it).replace("+", "%20") }

    /** The book's chapters (one MP3 set, in reading order) and cover; null if it has no MP3s. */
    fun parseMetadata(id: String, json: String): Book? {
        val root = MiniJson.parse(json) as? Map<*, *> ?: return null
        val meta = root["metadata"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val files = (root["files"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
        val format = MP3_FORMATS.firstOrNull { f -> files.any { it["format"] == f } } ?: return null
        val chapters = files.filter { it["format"] == format }
            .mapNotNull { f ->
                val name = f["name"] as? String ?: return@mapNotNull null
                if (name.endsWith("_sample.mp3")) return@mapNotNull null
                File(
                    name = name,
                    url = downloadUrl(id, name),
                    title = text(f["title"])?.takeIf { it.isNotBlank() },
                    track = (f["track"] as? String)?.let { LEADING_NUMBER.find(it)?.value?.toIntOrNull() },
                    seconds = (f["length"] as? String)?.let(::parseLength),
                    bytes = (f["size"] as? String)?.toLongOrNull(),
                )
            }
            .sortedWith(compareBy<File> { it.track ?: Int.MAX_VALUE }.thenBy { it.name })
        if (chapters.isEmpty()) return null
        val cover = files.filter { (it["format"] == "JPEG" || it["format"] == "PNG") }
            .maxByOrNull { (it["size"] as? String)?.toLongOrNull() ?: 0L }
            ?.let { downloadUrl(id, it["name"] as String) }
            ?: "https://archive.org/services/img/" + enc(id)
        return Book(
            id = id,
            title = text(meta["title"]) ?: id,
            author = text(meta["creator"]),
            description = text(meta["description"])?.let(::stripHtml),
            files = chapters,
            coverUrl = cover,
        )
    }

    /** "1234.5" seconds or "20:34" / "1:02:03". */
    fun parseLength(s: String): Double? {
        if (':' !in s) return s.toDoubleOrNull()
        return s.split(':').fold(0.0) { acc, part -> acc * 60 + (part.toDoubleOrNull() ?: return null) }
    }

    private fun text(v: Any?): String? = when (v) {
        is String -> v.trim()
        is List<*> -> v.filterIsInstance<String>().joinToString(", ").trim()
        else -> null
    }?.takeIf { it.isNotEmpty() }

    private fun stripHtml(s: String): String =
        s.replace(Regex("<br\\s*/?>|</p>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
            .replace(Regex("\n{3,}"), "\n\n").trim()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val SPECIAL = Regex("[+\\-&|!(){}\\[\\]^\"~*?:\\\\/]")
    private val LEADING_NUMBER = Regex("\\d+")
}
