package io.github.hexotronic.lauscher.download

import io.github.hexotronic.lauscher.download.core.HttpFetch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Plain HTTP for the downloads: pages, JSON, and media streams with resume. */
object Net {
    /** The desktop browser NewPipe identifies as; some sites refuse unknown clients. */
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    fun open(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    fun text(url: String): String {
        val conn = open(url, headers = mapOf("Accept" to "application/json, text/*"))
        try {
            if (conn.responseCode >= 400) throw IOException("HTTP ${conn.responseCode} für $url")
            return conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    fun bytes(url: String, max: Int = 8_000_000): ByteArray? = runCatching {
        val conn = open(url)
        try {
            if (conn.responseCode >= 400) return@runCatching null
            conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) return@runCatching null
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /**
     * Downloads a media stream into [file] over several connections at once, continuing what an
     * earlier attempt left. YouTube streams are fetched the way NewPipe's player does (POSTed in
     * ranges, see [isYoutubeStream]); other servers get Range requests. Reports (bytes in the file, total
     * or -1). A pause or cancel of the coroutine stops it and keeps the file.
     */
    suspend fun download(url: String, file: File, onProgress: (Long, Long) -> Unit): Long {
        val ctx = currentCoroutineContext()
        val cancelled = { !ctx.isActive }
        return if (isYoutubeStream(url)) {
            val headers = HashMap<String, String>()
            headers["Accept-Encoding"] = "identity"
            if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
                headers["User-Agent"] = YoutubeParsingHelper.getVisionOsUserAgent(null)
            }
            if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
                headers["Origin"] = "https://www.youtube.com"
                headers["Referer"] = "https://www.youtube.com"
            }
            HttpFetch.parallelToFile(url, file, USER_AGENT, cancelled, onProgress, CONNECTIONS, chunked = true, headers = headers, chunk = YOUTUBE_CHUNK)
        } else {
            HttpFetch.parallelToFile(url, file, USER_AGENT, cancelled, onProgress, CONNECTIONS)
        }
    }

    fun isYoutubeStream(url: String): Boolean = runCatching {
        val u = URL(url)
        u.host.endsWith("googlevideo.com") && u.path.startsWith("/videoplayback")
    }.getOrDefault(false)

    private const val YOUTUBE_CHUNK = 4L * 1024 * 1024

    /** Connections per file: servers (YouTube, archive.org) limit each one, together they are faster. */
    private const val CONNECTIONS = 4
}

/** The extractor's HTTP access. */
class ExtractorDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val headers = request.headers().mapValues { (k, v) -> v.joinToString(if (k.equals("Cookie", ignoreCase = true)) "; " else ", ") }
        val conn = Net.open(request.url(), request.httpMethod(), headers, request.dataToSend())
        try {
            val code = conn.responseCode
            if (code == 429) throw ReCaptchaException("Zu viele Anfragen – bitte später erneut versuchen", request.url())
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }
            val responseHeaders = conn.headerFields.entries.filter { it.key != null }.associate { it.key!! to it.value }
            return Response(code, conn.responseMessage, responseHeaders, body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }
}
