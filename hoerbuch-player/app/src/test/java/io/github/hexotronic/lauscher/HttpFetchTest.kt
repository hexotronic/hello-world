package io.github.hexotronic.lauscher

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import kotlin.random.Random

class HttpFetchTest {
    private val data = Random(7).nextBytes(300_000)
    private lateinit var server: MiniServer
    private val ranges = ArrayList<String?>()
    private var dropsLeft = 0
    private lateinit var base: String
    private lateinit var dir: File

    /** Just enough HTTP/1.1 for these tests (java.net only, so it also runs in Android unit tests). */
    private class MiniServer(val handle: (method: String, target: String, headers: Map<String, String>) -> Reply) {
        class Reply(val code: Int, val headers: Map<String, String> = emptyMap(), val body: ByteArray = ByteArray(0), val sendOnly: Int = body.size)

        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { client.use { serve(it) } }
                }
            }
        }

        private fun serve(client: Socket) {
            val input = client.getInputStream()
            val head = readHead(input) ?: return
            val lines = head.split("\r\n")
            val (method, target) = lines[0].split(' ').let { it[0] to it[1] }
            val headers = lines.drop(1).filter { ':' in it }.associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            headers["content-length"]?.toIntOrNull()?.let { n -> repeat(n) { input.read() } }
            val reply = handle(method, target, headers)
            val out = client.getOutputStream()
            val sb = StringBuilder("HTTP/1.1 ${reply.code} X\r\nConnection: close\r\nContent-Length: ${reply.body.size}\r\n")
            reply.headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray())
            out.write(reply.body, 0, reply.sendOnly)
            out.flush()
        }

        private fun readHead(input: InputStream): String? {
            val buf = ByteArrayOutputStream()
            var last4 = 0
            while (true) {
                val b = input.read()
                if (b < 0) return null
                buf.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) return buf.toString(Charsets.ISO_8859_1.name()).trimEnd()
            }
        }

        fun stop() = socket.close()
    }

    @Before
    fun start() {
        server = MiniServer { method, target, headers ->
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")
            when (path) {
                // A normal file server with Range support (or without, for "/norange").
                "/file", "/norange" -> {
                    val range = headers["range"]
                    synchronized(ranges) { ranges += range }
                    val from = if (path == "/file" && range != null) range.removePrefix("bytes=").substringBefore('-').toInt() else 0
                    if (from >= data.size) {
                        MiniServer.Reply(416)
                    } else {
                        val body = data.copyOfRange(from, data.size)
                        val drop = dropsLeft > 0
                        if (drop) dropsLeft--
                        val extra = if (from > 0) mapOf("Content-Range" to "bytes $from-${data.size - 1}/${data.size}") else emptyMap()
                        MiniServer.Reply(if (from > 0) 206 else 200, extra, body, if (drop) body.size / 2 else body.size)
                    }
                }
                // YouTube style: POST with "&range=from-to" in the query.
                "/chunked" -> {
                    check(method == "POST")
                    val m = Regex("range=(\\d+)-(\\d+)").find(query)!!
                    val from = m.groupValues[1].toInt()
                    if (from >= data.size) {
                        MiniServer.Reply(416)
                    } else {
                        MiniServer.Reply(200, body = data.copyOfRange(from, minOf(m.groupValues[2].toInt(), data.size - 1) + 1))
                    }
                }
                else -> MiniServer.Reply(404)
            }
        }
        base = "http://127.0.0.1:${server.port}"
        dir = java.nio.file.Files.createTempDirectory("fetch").toFile()
    }

    @After
    fun stop() {
        server.stop()
        dir.deleteRecursively()
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
