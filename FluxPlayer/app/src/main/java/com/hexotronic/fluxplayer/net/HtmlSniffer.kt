package com.hexotronic.fluxplayer.net

/**
 * Sucht in einer Webseite nach direkt abspielbaren Videoquellen
 * (<video>/<source>, og:video, eingebettete .m3u8/.mp4/.mpd-Links in Skripten/JSON).
 */
object HtmlSniffer {

    data class Candidate(val url: String, val label: String)

    private val mediaUrl = Regex(
        """https?:(?:\\?/){2}[^\s"'<>`]+?\.(m3u8|mpd|mp4|webm|mkv|mov|m4v|ts|mp3|m4a|ogg|flv)(?:\?[^\s"'<>`]*)?(?=["'\s<>`]|\\["']|$)""",
        RegexOption.IGNORE_CASE
    )
    private val tagSrc = Regex("""<(?:video|source|audio)\b[^>]*?\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val metaA = Regex(
        """<meta\b[^>]*?(?:property|name)\s*=\s*["'](?:og:video(?::url|:secure_url)?|twitter:player:stream)["'][^>]*?content\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val metaB = Regex(
        """<meta\b[^>]*?content\s*=\s*["']([^"']+)["'][^>]*?(?:property|name)\s*=\s*["'](?:og:video(?::url|:secure_url)?|twitter:player:stream)["']""",
        RegexOption.IGNORE_CASE
    )
    private val titleTag = Regex("<title[^>]*>([^<]{1,300})</title>", RegexOption.IGNORE_CASE)

    fun pageTitle(html: String): String? =
        titleTag.find(html)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }?.takeIf { it.isNotEmpty() }

    fun find(html: String, pageUrl: String): List<Candidate> {
        val found = LinkedHashSet<String>()
        for (r in listOf(tagSrc, metaA, metaB)) {
            r.findAll(html).forEach { m ->
                val u = clean(m.groupValues[1])
                if (!u.startsWith("blob:") && !u.startsWith("data:")) found += PlaylistParser.resolve(pageUrl, u)
            }
        }
        mediaUrl.findAll(html).forEach { m -> found += clean(m.value) }
        return found
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .sortedBy { priority(it) }
            .map { Candidate(it, label(it)) }
    }

    private fun clean(s: String): String =
        decodeEntities(s)
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/", ignoreCase = true)
            .trim()

    private fun decodeEntities(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x2F;", "/")
            .replace("&lt;", "<").replace("&gt;", ">")

    private fun ext(url: String) = url.substringBefore('?').substringAfterLast('.', "").lowercase()

    private fun priority(url: String): Int = when (ext(url)) {
        "m3u8" -> 0
        "mpd" -> 1
        "mp4", "m4v", "webm", "mkv", "mov" -> 2
        "ts", "flv" -> 3
        else -> 4
    }

    private fun label(url: String): String {
        val kind = when (ext(url)) {
            "m3u8" -> "HLS-Stream"
            "mpd" -> "DASH-Stream"
            "mp3", "m4a", "ogg" -> "Audio"
            else -> ext(url).uppercase() + "-Video"
        }
        return "$kind · ${PlaylistParser.nameFromUrl(url)}"
    }
}
