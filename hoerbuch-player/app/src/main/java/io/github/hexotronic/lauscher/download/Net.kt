package io.github.hexotronic.lauscher.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException
import java.io.OutputStream
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
     * Copies a media stream to [out], reporting (bytes so far, total or -1). YouTube streams are
     * fetched the way NewPipe's player does (POSTed in ranges, see [isYoutubeStream]); other
     * servers get one request that is resumed with a Range header if the connection drops.
     */
    suspend fun copyStream(url: String, out: OutputStream, onProgress: (Long, Long) -> Unit): Long =
        if (isYoutubeStream(url)) copyYoutube(url, out, onProgress) else copyPlain(url, out, onProgress)

    fun isYoutubeStream(url: String): Boolean = runCatching {
        val u = URL(url)
        u.host.endsWith("googlevideo.com") && u.path.startsWith("/videoplayback")
    }.getOrDefault(false)

    private suspend fun copyPlain(url: String, out: OutputStream, onProgress: (Long, Long) -> Unit): Long {
        var done = 0L
        var total = -1L
        var attempts = 0
        while (true) {
            val conn = open(url, headers = if (done > 0) mapOf("Range" to "bytes=$done-") else emptyMap())
            try {
                val code = conn.responseCode
                if (code >= 400) throw IOException("HTTP $code beim Laden")
                if (done > 0 && code != HttpURLConnection.HTTP_PARTIAL) throw IOException("Der Server kann nicht fortsetzen")
                if (total < 0) total = conn.contentLengthLong.takeIf { it > 0 }?.plus(done) ?: -1
                conn.inputStream.use { input -> done = pump(input, out, done, total, onProgress) }
                if (total < 0 || done >= total) return done
            } catch (e: IOException) {
                if (++attempts > 3 || done == 0L) throw e
            } finally {
                conn.disconnect()
            }
        }
    }

    private suspend fun copyYoutube(url: String, out: OutputStream, onProgress: (Long, Long) -> Unit): Long {
        val total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1
        val headers = HashMap<String, String>()
        headers["Accept-Encoding"] = "identity"
        if (YoutubeParsingHelper.isVisionOsStreamingUrl(url)) {
            headers["User-Agent"] = YoutubeParsingHelper.getVisionOsUserAgent(null)
        }
        if (YoutubeParsingHelper.isWebStreamingUrl(url)) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com"
        }
        var done = 0L
        var request = 0
        var attempts = 0
        while (total < 0 || done < total) {
            val end = done + YOUTUBE_CHUNK - 1
            val conn = open("$url&range=$done-$end&rn=${request++}", "POST", headers, YOUTUBE_POST_BODY)
            try {
                val code = conn.responseCode
                if (code >= 400) throw IOException("HTTP $code beim Laden")
                val before = done
                conn.inputStream.use { input -> done = pump(input, out, done, total, onProgress) }
                if (done - before < YOUTUBE_CHUNK) break
            } catch (e: IOException) {
                if (++attempts > 3) throw e
            } finally {
                conn.disconnect()
            }
        }
        return done
    }

    private suspend fun pump(input: java.io.InputStream, out: OutputStream, start: Long, total: Long, onProgress: (Long, Long) -> Unit): Long {
        val buf = ByteArray(64 * 1024)
        var done = start
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) return done
            out.write(buf, 0, n)
            done += n
            onProgress(done, total)
        }
    }

    private const val YOUTUBE_CHUNK = 4L * 1024 * 1024
    private val YOUTUBE_POST_BODY = byteArrayOf(0x78, 0)
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
