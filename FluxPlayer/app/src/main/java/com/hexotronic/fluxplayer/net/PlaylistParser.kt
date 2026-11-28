package com.hexotronic.fluxplayer.net

import com.hexotronic.fluxplayer.data.MediaEntry
import java.net.URI
import java.net.URLDecoder

/** Parser für M3U/M3U8-Playlists (inkl. IPTV-Attribute und VLC-Optionen) sowie PLS. */
object PlaylistParser {

    /** HLS-Medien-/Master-Playlists enthalten #EXT-X-Tags – die spielt ExoPlayer direkt. */
    fun isHls(text: String): Boolean = text.contains("#EXT-X-", ignoreCase = true)

    fun looksLikeM3u(text: String): Boolean {
        val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return t.startsWith("#EXTM3U", ignoreCase = true) || t.lineSequence().take(20).any {
            val l = it.trim()
            l.startsWith("#EXTINF", ignoreCase = true)
        }
    }

    fun looksLikePls(text: String): Boolean =
        text.trimStart('﻿', ' ', '\n', '\r').startsWith("[playlist]", ignoreCase = true)

    fun parseM3u(text: String, baseUrl: String?): List<MediaEntry> {
        val out = ArrayList<MediaEntry>()
        var title: String? = null
        val headers = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val name = line.substringAfterLast(',', "").trim()
                    title = name.ifEmpty { attribute(line, "tvg-name") }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':').trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> headers["User-Agent"] = value
                        "http-referrer", "http-referer" -> headers["Referer"] = value
                        "http-origin" -> headers["Origin"] = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val url = resolve(baseUrl, line)
                    out += MediaEntry(
                        uri = url,
                        title = title ?: nameFromUrl(url),
                        headers = headers.toMap()
                    )
                    title = null
                    headers.clear()
                }
            }
        }
        return out
    }

    fun parsePls(text: String, baseUrl: String?): List<MediaEntry> {
        val files = sortedMapOf<Int, String>()
        val titles = HashMap<Int, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=', "").trim()
            when {
                key.startsWith("File", ignoreCase = true) -> key.drop(4).toIntOrNull()?.let { files[it] = value }
                key.startsWith("Title", ignoreCase = true) -> key.drop(5).toIntOrNull()?.let { titles[it] = value }
            }
        }
        return files.map { (i, f) ->
            val url = resolve(baseUrl, f)
            MediaEntry(url, titles[i]?.takeIf { it.isNotBlank() } ?: nameFromUrl(url))
        }
    }

    fun resolve(base: String?, ref: String): String {
        if (base == null) return ref
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(ref)) return ref
        return try {
            URI(base).resolve(ref.replace(" ", "%20")).toString()
        } catch (_: Exception) {
            ref
        }
    }

    fun nameFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val last = path.substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(last, "UTF-8")
        } catch (_: Exception) {
            last
        }
        return decoded.ifBlank { url }
    }

    private fun attribute(line: String, name: String): String? =
        Regex("$name=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}
