package io.github.hexotronic.lauscher.download.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/**
 * Downloads into a file that can be resumed: a partial file from an earlier attempt (pause,
 * dropped connection) is continued instead of fetched again.
 */
object HttpFetch {
    /** An answer like 403 or 404: trying again does not help. */
    class HttpStatus(val code: Int) : IOException("HTTP $code beim Laden")

    fun open(url: String, userAgent: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: ByteArray? = null): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = method
        conn.setRequestProperty("User-Agent", userAgent)
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (body != null) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    /**
     * One request (with a Range header to continue [file]); a dropped connection is resumed up
     * to three times. Servers without Range support start over. Reports (bytes in the file,
     * total or -1); [isCancelled] stops with a [CancellationException], the file stays.
     */
    fun toFile(url: String, file: File, userAgent: String, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit): Long {
        var done = file.length()
        var total = -1L
        var attempts = 0
        while (true) {
            val conn = open(url, userAgent, headers = if (done > 0) mapOf("Range" to "bytes=$done-") else emptyMap())
            try {
                val code = conn.responseCode
                // Asked for the rest of a file that is already complete.
                if (code == 416 && done > 0) return done
                if (code >= 400) throw HttpStatus(code)
                val append = done > 0 && code == HttpURLConnection.HTTP_PARTIAL
                if (!append) done = 0
                if (total < 0) total = conn.contentLengthLong.takeIf { it > 0 }?.plus(done) ?: -1
                FileOutputStream(file, append).use { out ->
                    conn.inputStream.use { input -> done = pump(input, out, done, total, isCancelled, onProgress) }
                }
                if (total < 0 || done >= total) return done
                // The answer ended early: ask for the rest.
                if (++attempts > 3) throw IOException("Die Verbindung bricht immer wieder ab")
            } catch (e: HttpStatus) {
                throw e
            } catch (e: IOException) {
                if (++attempts > 3) throw e
                // Everything that arrived is in the file; continue after it.
                done = file.length()
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * In pieces of [chunk] bytes, each a POST with "&range=from-to" in the URL – the way
     * YouTube's servers are asked (see NewPipe's player). Continues [file] like [toFile].
     */
    fun chunkedToFile(
        url: String,
        file: File,
        userAgent: String,
        headers: Map<String, String>,
        chunk: Long,
        isCancelled: () -> Boolean,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        val total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1
        var done = file.length()
        var request = 0
        var attempts = 0
        while (total < 0 || done < total) {
            val conn = open("$url&range=$done-${done + chunk - 1}&rn=${request++}", userAgent, "POST", headers, POST_BODY)
            try {
                val code = conn.responseCode
                if (code == 416) break
                if (code >= 400) throw HttpStatus(code)
                val before = done
                FileOutputStream(file, true).use { out ->
                    conn.inputStream.use { input -> done = pump(input, out, done, total, isCancelled, onProgress) }
                }
                if (done - before < chunk) break
            } catch (e: HttpStatus) {
                throw e
            } catch (e: IOException) {
                if (++attempts > 3) throw e
                // Whatever arrived of this piece stays; the next request continues after it.
                done = file.length()
            } finally {
                conn.disconnect()
            }
        }
        return done
    }

    private fun pump(input: InputStream, out: FileOutputStream, start: Long, total: Long, isCancelled: () -> Boolean, onProgress: (Long, Long) -> Unit): Long {
        val buf = ByteArray(64 * 1024)
        var done = start
        while (true) {
            if (isCancelled()) throw CancellationException("Download angehalten")
            val n = input.read(buf)
            if (n < 0) return done
            out.write(buf, 0, n)
            done += n
            onProgress(done, total)
        }
    }

    private val POST_BODY = byteArrayOf(0x78, 0)
}

/** Download speed, smoothed over a few seconds. */
class TransferMeter(private val smoothingMs: Long = 3_000) {
    var bytesPerSecond = 0.0
        private set
    private var lastTime = -1L
    private var lastBytes = 0L

    /** [totalBytes] transferred so far at [timeMs]. */
    fun add(timeMs: Long, totalBytes: Long) {
        if (lastTime < 0) {
            lastTime = timeMs
            lastBytes = totalBytes
            return
        }
        val dt = timeMs - lastTime
        if (dt < 200) return
        val rate = (totalBytes - lastBytes).coerceAtLeast(0) * 1000.0 / dt
        val keep = kotlin.math.exp(-dt.toDouble() / smoothingMs)
        bytesPerSecond = if (bytesPerSecond == 0.0) rate else keep * bytesPerSecond + (1 - keep) * rate
        lastTime = timeMs
        lastBytes = totalBytes
    }

    /** After a pause nothing flows; the next measurement starts fresh. */
    fun reset() {
        bytesPerSecond = 0.0
        lastTime = -1L
    }
}

object TransferEstimate {
    /**
     * Remaining time in ms: from the bytes when the total is known and data flows, otherwise
     * from the share done ([fraction]) after [activeMs] of work (this also covers conversion).
     * Null while there is too little to go on.
     */
    fun remainingMs(bytes: Long, bytesTotal: Long, bytesPerSecond: Double, fraction: Float, activeMs: Long): Long? {
        if (bytesTotal > 0 && bytes <= bytesTotal && bytesPerSecond >= 1_000) return ((bytesTotal - bytes) / bytesPerSecond * 1000).toLong()
        if (fraction >= 0.03f && activeMs >= 5_000) return (activeMs * (1 - fraction) / fraction).toLong()
        return null
    }

    /** "850 KB", "12,4 MB", "1,2 GB". */
    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f MB", bytes / 1e6)
        else -> "${(bytes / 1000).coerceAtLeast(if (bytes > 0) 1 else 0)} KB"
    }
}
