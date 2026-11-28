package com.hexotronic.fluxplayer.net

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.hexotronic.fluxplayer.data.MediaEntry
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Löst eine eingegebene Adresse in abspielbare Einträge auf:
 * direkte Mediendateien, HLS/DASH, RTSP/RTMP, M3U/PLS-Playlists (z. B. IPTV)
 * und Webseiten mit eingebettetem Video.
 */
class StreamResolver(private val context: Context, private val userAgent: String) {

    sealed interface Result {
        data class Play(val entries: List<MediaEntry>, val startIndex: Int = 0) : Result
        data class Choose(val title: String?, val entries: List<MediaEntry>) : Result
        data class Failed(val message: String) : Result
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun resolve(input: String, headers: Map<String, String>): Result {
        val url = normalize(input)
        if (url.isEmpty()) return Result.Failed("Keine Adresse angegeben.")
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        val ext = extensionOf(url)

        // Lokale Playlists
        if (scheme == "content" || scheme == "file") {
            if (ext in PLAYLIST_EXT) {
                val text = try {
                    context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_TEXT) }
                } catch (_: Exception) {
                    null
                }?.toString(Charsets.UTF_8) ?: return Result.Failed("Playlist konnte nicht gelesen werden.")
                return fromPlaylistText(text, null, url, headers)
            }
            return single(url, null, headers)
        }

        if (scheme in STREAM_SCHEMES) return single(url, null, headers)
        if (scheme != "http" && scheme != "https") {
            return Result.Failed("Nicht unterstütztes Protokoll: ${scheme ?: "?"}")
        }

        if (ext in MEDIA_EXT) return single(url, mimeForExtension(ext), headers)
        if (ext == "mpd") return single(url, MimeTypes.APPLICATION_MPD, headers)
        if (ext == "ism" || url.contains(".ism/manifest", ignoreCase = true)) return single(url, MimeTypes.APPLICATION_SS, headers)

        // Unklar (oder m3u/m3u8/pls): Inhalt anschauen
        return try {
            probe(url, headers)
        } catch (e: Exception) {
            // Netzwerkfehler beim Prüfen -> trotzdem direkt versuchen
            if (ext == "m3u8") single(url, MimeTypes.APPLICATION_M3U8, headers) else single(url, null, headers)
        }
    }

    private fun probe(url: String, headers: Map<String, String>): Result {
        val req = Request.Builder().url(url).apply {
            header("User-Agent", headers["User-Agent"] ?: userAgent)
            headers.forEach { (k, v) -> if (!k.equals("User-Agent", true)) header(k, v) }
        }.build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                return Result.Failed("Server antwortet mit HTTP ${resp.code}.")
            }
            val finalUrl = resp.request.url.toString()
            val type = (resp.header("Content-Type") ?: "").lowercase()
            val ext = extensionOf(finalUrl)
            val isText = type.startsWith("text/") || type.contains("mpegurl") || type.contains("json") ||
                type.contains("xml") || type.contains("x-scpls") || type.isEmpty() || ext in PLAYLIST_EXT
            if (type.startsWith("video/") || type.startsWith("audio/") && !type.contains("mpegurl") && !type.contains("scpls")) {
                return single(finalUrl, null, headers)
            }
            if (type.contains("dash+xml")) return single(finalUrl, MimeTypes.APPLICATION_MPD, headers)
            if (!isText) return single(finalUrl, null, headers)

            val bytes = resp.body.byteStream().use { readLimited(it, MAX_TEXT) }
            val text = bytes.toString(Charsets.UTF_8)
            if (PlaylistParser.isHls(text)) return single(finalUrl, MimeTypes.APPLICATION_M3U8, headers)
            if (PlaylistParser.looksLikeM3u(text) || PlaylistParser.looksLikePls(text)) {
                return fromPlaylistText(text, finalUrl, finalUrl, headers)
            }
            if (type.contains("html") || text.contains("<html", ignoreCase = true) || text.contains("<video", ignoreCase = true)) {
                val found = HtmlSniffer.find(text, finalUrl)
                val title = HtmlSniffer.pageTitle(text)
                if (found.isEmpty()) {
                    return Result.Failed(
                        "Auf der Seite wurde keine direkte Videoquelle gefunden. " +
                            "Tipp: Den direkten Link zur Videodatei bzw. zum .m3u8-Stream verwenden."
                    )
                }
                val withRef = headers + ("Referer" to finalUrl)
                val entries = found.map { c ->
                    MediaEntry(c.url, title ?: PlaylistParser.nameFromUrl(c.url), mimeFor(c.url), withRef)
                }
                return if (entries.size == 1) Result.Play(entries) else Result.Choose(title, entries.mapIndexed { i, e ->
                    e.copy(title = found[i].label)
                })
            }
            return single(finalUrl, null, headers)
        }
    }

    private fun fromPlaylistText(text: String, base: String?, url: String, headers: Map<String, String>): Result {
        if (PlaylistParser.isHls(text)) return single(url, MimeTypes.APPLICATION_M3U8, headers)
        val entries = if (PlaylistParser.looksLikePls(text)) PlaylistParser.parsePls(text, base)
        else PlaylistParser.parseM3u(text, base)
        if (entries.isEmpty()) return Result.Failed("Die Playlist enthält keine Einträge.")
        return Result.Play(entries.map { e ->
            e.copy(headers = headers + e.headers, mimeType = e.mimeType ?: mimeFor(e.uri))
        })
    }

    private fun single(url: String, mime: String?, headers: Map<String, String>) =
        Result.Play(listOf(MediaEntry(url, PlaylistParser.nameFromUrl(url), mime ?: mimeFor(url), headers)))

    companion object {
        private const val MAX_TEXT = 3 * 1024 * 1024
        val STREAM_SCHEMES = setOf("rtsp", "rtsps", "rtmp", "rtmps", "rtp", "udp", "mms", "srt")
        val PLAYLIST_EXT = setOf("m3u", "m3u8", "pls")
        val MEDIA_EXT = setOf(
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "ts", "m2ts", "mts", "3gp", "wmv", "asf",
            "mpg", "mpeg", "vob", "ogv", "rm", "rmvb", "divx", "f4v",
            "mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "amr", "ac3", "eac3", "dts", "mka"
        )
        /** Container, die ExoPlayer nicht selbst lesen kann -> direkt FFmpeg-Extraktor. */
        val FFMPEG_EXT = setOf("wmv", "asf", "wma", "rm", "rmvb", "divx", "dts")

        fun normalize(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return s
            if (!Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(s)) s = "https://$s"
            return s
        }

        fun extensionOf(url: String): String =
            url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase()

        fun mimeFor(url: String): String? = when (extensionOf(url)) {
            "m3u8" -> MimeTypes.APPLICATION_M3U8
            "mpd" -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        private fun mimeForExtension(ext: String): String? = null

        fun readLimited(input: InputStream, limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (total < limit) {
                val n = input.read(buf, 0, minOf(buf.size, limit - total))
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }

        /** Extrahiert die erste URL aus geteiltem Text. */
        fun extractUrl(text: String): String? =
            Regex("""(?:https?|rtsps?|rtmps?)://[^\s"'<>]+""", RegexOption.IGNORE_CASE).find(text)?.value
    }
}
