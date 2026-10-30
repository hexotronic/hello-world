package io.github.hexotronic.lauscher

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.hexotronic.lauscher.download.core.HttpFetch
import io.github.hexotronic.lauscher.download.core.TransferEstimate
import io.github.hexotronic.lauscher.download.core.TransferMeter
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CancellationException
import kotlin.random.Random

class HttpFetchTest {
    private val data = Random(7).nextBytes(300_000)
    private lateinit var server: HttpServer
    private val ranges = ArrayList<String?>()
    private var dropsLeft = 0
    private lateinit var base: String
    private lateinit var dir: File

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // A normal file server with Range support; "drop" ends the first answers half-way.
        server.createContext("/file") { ex -> serve(ex, rangeSupport = true) }
        server.createContext("/norange") { ex -> serve(ex, rangeSupport = false) }
        // YouTube style: POST with "&range=from-to" in the query.
        server.createContext("/chunked") { ex ->
            val q = ex.requestURI.rawQuery.orEmpty()
            val m = Regex("range=(\\d+)-(\\d+)").find(q)!!
            val from = m.groupValues[1].toInt()
            if (from >= data.size) {
                ex.sendResponseHeaders(416, -1)
            } else {
                val to = minOf(m.groupValues[2].toInt(), data.size - 1)
                ex.requestBody.readBytes()
                ex.sendResponseHeaders(200, (to - from + 1).toLong())
                ex.responseBody.use { it.write(data, from, to - from + 1) }
            }
            ex.close()
        }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
        dir = java.nio.file.Files.createTempDirectory("fetch").toFile()
    }

    @After
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun serve(ex: HttpExchange, rangeSupport: Boolean) {
        val range = ex.requestHeaders.getFirst("Range")
        synchronized(ranges) { ranges += range }
        val from = if (rangeSupport && range != null) range.removePrefix("bytes=").substringBefore('-').toInt() else 0
        when {
            from >= data.size -> ex.sendResponseHeaders(416, -1)
            else -> {
                val length = data.size - from
                if (from > 0) ex.responseHeaders.add("Content-Range", "bytes $from-${data.size - 1}/${data.size}")
                ex.sendResponseHeaders(if (from > 0) 206 else 200, length.toLong())
                val drop = dropsLeft > 0
                if (drop) dropsLeft--
                runCatching { ex.responseBody.write(data, from, if (drop) length / 2 else length) }
            }
        }
        ex.close()
    }

    private fun fetch(path: String, file: File, cancelAfter: Long = Long.MAX_VALUE): Long {
        var seen = 0L
        return HttpFetch.toFile("$base$path", file, "test", isCancelled = { seen > cancelAfter }) { done, _ -> seen = done }
    }

    @Test
    fun downloadsAndContinuesAfterAPause() {
        val file = File(dir, "a.part")
        try {
            fetch("/file", file, cancelAfter = 100_000)
            error("should have been paused")
        } catch (e: CancellationException) {
            // paused
        }
        val partial = file.length()
        assertTrue("partial $partial", partial in 100_000 until data.size.toLong())
        assertEquals(data.size.toLong(), fetch("/file", file))
        assertArrayEquals(data, file.readBytes())
        assertEquals("bytes=$partial-", ranges.last())
        // Asking again for a complete file: the server says 416, nothing changes.
        assertEquals(data.size.toLong(), fetch("/file", file))
        assertArrayEquals(data, file.readBytes())
    }

    @Test
    fun serversWithoutRangeStartOver() {
        val file = File(dir, "b.part")
        file.writeBytes(data.copyOf(50_000))
        assertEquals(data.size.toLong(), fetch("/norange", file))
        assertArrayEquals(data, file.readBytes())
    }

    @Test
    fun droppedConnectionsAreResumed() {
        dropsLeft = 2
        val file = File(dir, "c.part")
        assertEquals(data.size.toLong(), fetch("/file", file))
        assertArrayEquals(data, file.readBytes())
        assertTrue(ranges.size >= 3)
        assertTrue(ranges.drop(1).all { it != null && it.startsWith("bytes=") })
    }

    @Test
    fun chunkedDownloadsContinueToo() {
        val file = File(dir, "d.part")
        val url = "$base/chunked?clen=${data.size}"
        var seen = 0L
        try {
            HttpFetch.chunkedToFile(url, file, "test", emptyMap(), 64_000, isCancelled = { seen > 130_000 }) { done, _ -> seen = done }
            error("should have been paused")
        } catch (e: CancellationException) {
            // paused
        }
        assertTrue(file.length() in 130_000L until data.size.toLong())
        assertEquals(data.size.toLong(), HttpFetch.chunkedToFile(url, file, "test", emptyMap(), 64_000, { false }) { _, _ -> })
        assertArrayEquals(data, file.readBytes())
        // Without a known length the last short piece ends the download.
        val other = File(dir, "e.part")
        assertEquals(data.size.toLong(), HttpFetch.chunkedToFile("$base/chunked?x=1", other, "test", emptyMap(), 100_000, { false }) { _, _ -> })
        assertArrayEquals(data, other.readBytes())
    }

    @Test
    fun speedAndRemainingTime() {
        val meter = TransferMeter()
        meter.add(0, 0)
        meter.add(1_000, 2_000_000)
        assertEquals(2_000_000.0, meter.bytesPerSecond, 1.0)
        meter.add(2_000, 3_000_000)
        assertTrue(meter.bytesPerSecond in 1_000_000.0..2_000_000.0)
        meter.reset()
        assertEquals(0.0, meter.bytesPerSecond, 0.0)

        // Known size: 30 MB left at 2 MB/s = 15 s.
        assertEquals(15_000L, TransferEstimate.remainingMs(20_000_000, 50_000_000, 2_000_000.0, 0.4f, 10_000))
        // Unknown size: a quarter done after 60 s → 3 more minutes.
        assertEquals(180_000L, TransferEstimate.remainingMs(5_000_000, -1, 2_000_000.0, 0.25f, 60_000))
        assertNull(TransferEstimate.remainingMs(0, -1, 0.0, 0.01f, 2_000))
        assertEquals("12,4 MB", TransferEstimate.size(12_400_000))
        assertEquals("850 KB", TransferEstimate.size(850_000))
        assertEquals("1,2 GB", TransferEstimate.size(1_200_000_000))
    }
}
