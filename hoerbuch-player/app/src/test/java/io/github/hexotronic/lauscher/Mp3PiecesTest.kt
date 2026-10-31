package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.download.core.EncodedPiece
import io.github.hexotronic.lauscher.download.core.Mp3FrameList
import io.github.hexotronic.lauscher.download.core.Mp3Joiner
import io.github.hexotronic.lauscher.download.core.PcmPiece
import io.github.hexotronic.lauscher.download.core.PcmPieces
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class Mp3PiecesTest {
    @Test
    fun soundIsCutIntoOverlappingPiecesOnTheFrameGrid() {
        val spf = 10
        val channels = 2
        val total = 1234
        val sound = ShortArray(total * channels) { it.toShort() }
        val pieces = PcmPieces(channels, framesPerPiece = 30, warmFrames = 4, extraFrames = 3, samplesPerFrame = spf)
        val out = ArrayList<PcmPiece>()
        val rnd = Random(5)
        var pos = 0
        while (pos < total) {
            val n = minOf(total - pos, 1 + rnd.nextInt(90))
            out += pieces.add(sound.copyOfRange(pos * channels, (pos + n) * channels), n)
            pos += n
        }
        pieces.finish()?.let { out += it }
        assertEquals((0 until out.size).toList(), out.map { it.index })
        assertTrue(out.last().last && out.dropLast(1).none { it.last })
        out.forEachIndexed { i, p ->
            assertEquals(i * 30, p.nominalStart)
            assertEquals(maxOf(0, i * 30 - 4), p.firstFrame)
            val start = p.firstFrame * spf
            val end = if (p.last) total else (i * 30 + 30 + 3) * spf
            assertEquals(end - start, p.frames)
            assertArrayEquals(sound.copyOfRange(start * channels, end * channels), p.samples.copyOf(p.frames * channels))
        }
        // Together the pieces' own parts cover the whole sound.
        assertTrue(out.last().nominalStart * spf < total)
    }

    @Test
    fun shortSoundStaysOnePiece() {
        val pieces = PcmPieces(1, framesPerPiece = 30, warmFrames = 4, extraFrames = 3, samplesPerFrame = 10)
        assertTrue(pieces.add(ShortArray(200) { 7 }, 200).isEmpty())
        val p = pieces.finish()!!
        assertEquals(0, p.index)
        assertEquals(200, p.frames)
        assertTrue(p.last)
        // Nothing at all: nothing to encode.
        val empty = PcmPieces(1, framesPerPiece = 30, warmFrames = 4, extraFrames = 3, samplesPerFrame = 10)
        assertEquals(0, empty.finish()!!.frames)
        val exact = PcmPieces(1, framesPerPiece = 3, warmFrames = 1, extraFrames = 1, samplesPerFrame = 1)
        assertEquals(1, exact.add(ShortArray(4), 4).size)
        assertEquals(2, exact.finish()!!.frames) // frames 2 and 3: warm-up and part of the second piece
        assertNull(PcmPieces(1, framesPerPiece = 3, warmFrames = 0, extraFrames = 0, samplesPerFrame = 1).run { add(ShortArray(3), 3); finish() })
    }

    @Test
    fun framesAndTheirReservoirAreRead() {
        val stream = FakeEncoder(Random(1), source = 1).encode(0, 20)
        val frames = Mp3FrameList.parse(stream.bytes)
        assertEquals(20, frames.size)
        for (f in 0 until 20) {
            assertEquals(f * FRAME, frames.byteOffset(f))
            assertEquals(stream.mdb[f], frames.mainDataBegin[f])
            assertEquals(f * PAYLOAD, frames.payloadStart[f])
        }
        assertEquals(stream.free(7), frames.freeBefore(7))
    }

    @Test
    fun piecesAreJoinedSoEveryFrameFindsItsData() {
        var movedJoints = 0
        var clean = 0
        var silentMax = 0
        repeat(200) { seed ->
            val rnd = Random(seed)
            val a = FakeEncoder(rnd, source = 1, greed = rnd.nextDouble()).encode(0, 120)
            val b = FakeEncoder(rnd, source = 2, greed = rnd.nextDouble()).encode(40, 200)
            val out = ByteArrayOutputStream()
            val joiner = Mp3Joiner(out)
            joiner.add(EncodedPiece(0, 0, 0, a.bytes.copyOf()))
            joiner.add(EncodedPiece(1, 40, 100, b.bytes.copyOf()))
            joiner.finish()
            val joined = Mp3FrameList.parse(out.toByteArray())
            assertEquals(200, joined.size)
            val joint = joiner.joints.single()
            assertTrue("seed $seed: joint $joint", joint in 100..112)
            if (joiner.roughJoints == 0) {
                clean++
                if (joint > 100) movedJoints++
            }
            // Every frame decodes its own data, or is silent (only right at a rough joint).
            var silent = 0
            for (f in 0 until 200) {
                val expected = marker(if (f < joint) 1 else 2, f)
                val data = decode(joined, f)
                if (data.isEmpty() && f >= joint && joined.mainDataBegin[f] == 0 && b.length[f - 40] > 0) silent++
                assertTrue("seed $seed frame $f", data.all { it == expected })
            }
            silentMax = maxOf(silentMax, silent)
            assertTrue("seed $seed: $silent silent, rough ${joiner.roughJoints}", (silent > 0) == (joiner.roughJoints > 0))
        }
        assertTrue("no joint was moved to a later frame", movedJoints > 0)
        // These fake encoders are independent and the search window is short, so many joints are
        // rough here; real LAME encoders settle to the same reservoir and join cleanly.
        println("Saubere Nähte: $clean von 200, davon $movedJoints verschoben; höchstens $silentMax stille Rahmen")
        assertTrue(clean > 100 && silentMax <= 3)
    }

    @Test
    fun aJointWithoutRoomIsCountedAsRough() {
        // The first encoder never leaves room, the second always borrows from earlier frames.
        val a = FakeEncoder(Random(2), source = 1, greed = 1.0, fill = true).encode(0, 120)
        val b = FakeEncoder(Random(3), source = 2, greed = 0.0).encode(40, 200)
        val out = ByteArrayOutputStream()
        val joiner = Mp3Joiner(out)
        joiner.add(EncodedPiece(0, 0, 0, a.bytes))
        joiner.add(EncodedPiece(1, 40, 100, b.bytes))
        joiner.finish()
        assertEquals(1, joiner.roughJoints)
        val joined = Mp3FrameList.parse(out.toByteArray())
        assertEquals(200, joined.size)
        val joint = joiner.joints.single()
        assertTrue(decode(joined, joint).isEmpty())
        for (f in 0 until 200) if (f != joint) assertTrue("frame $f", decode(joined, f).all { it == marker(if (f < joint) 1 else 2, f) })
    }

    /** The bytes of frame [f]'s main data, read the way a decoder does. */
    private fun decode(frames: Mp3FrameList, f: Int): ByteArray {
        val start = frames.payloadStart[f] - frames.mainDataBegin[f]
        val length = dataLength(frames, f)
        return ByteArray(length) { frames.bytes[frames.byteAt(start + it, f + 1)] }
    }

    private fun dataLength(frames: Mp3FrameList, f: Int): Int {
        // part2_3_length of both granules (MPEG-1 mono: after 9 + 5 + 4 bits, then 59 bits per granule).
        val side = frames.byteOffset(f) + 4
        fun bits(from: Int, n: Int): Int {
            var v = 0
            for (i in from until from + n) v = (v shl 1) or (frames.bytes[side + i / 8].toInt() shr (7 - i % 8) and 1)
            return v
        }
        return (bits(18, 12) + bits(18 + 59, 12)) / 8
    }

    private class Stream(val bytes: ByteArray, val mdb: IntArray, val start: IntArray, val length: IntArray) {
        fun free(f: Int): Int = f * PAYLOAD - (start[f - 1] + length[f - 1])
    }

    /**
     * Makes MPEG-1 Layer III frames (48 kHz, 64 kbit/s, mono) like an encoder with a bit
     * reservoir: each frame's data starts right after the previous frame's data, at most 511
     * bytes before its own payload. Every data byte holds [marker] (source, frame).
     */
    private class FakeEncoder(private val rnd: Random, private val source: Int, private val greed: Double = 0.5, private val fill: Boolean = false) {
        fun encode(firstFrame: Int, endFrame: Int): Stream {
            val n = endFrame - firstFrame
            val bytes = ByteArray(n * FRAME)
            val mdb = IntArray(n)
            val start = IntArray(n)
            val length = IntArray(n)
            var dataEnd = 0
            for (i in 0 until n) {
                val payloadStart = i * PAYLOAD
                val begin = if (fill) payloadStart else maxOf(dataEnd, payloadStart - 511)
                val room = payloadStart + PAYLOAD - begin
                val len = if (fill) PAYLOAD else (room * (greed * 0.6 + 0.4 * rnd.nextDouble())).toInt().coerceIn(0, room) and 0x7FE
                mdb[i] = payloadStart - begin
                start[i] = begin
                length[i] = len
                val o = i * FRAME
                bytes[o] = 0xFF.toByte()
                bytes[o + 1] = 0xFB.toByte() // MPEG-1, Layer III, no CRC
                bytes[o + 2] = 0x54.toByte() // 64 kbit/s, 48 kHz, no padding
                bytes[o + 3] = 0xC0.toByte() // mono
                val half = len * 8 / 2
                writeBits(bytes, o + 4, 0, 9, mdb[i])
                writeBits(bytes, o + 4, 18, 12, half)
                writeBits(bytes, o + 4, 18 + 59, 12, len * 8 - half)
                for (p in begin until begin + len) bytes[(p / PAYLOAD) * FRAME + 4 + 17 + p % PAYLOAD] = marker(source, firstFrame + i)
                dataEnd = begin + len
            }
            return Stream(bytes, mdb, start, length)
        }

        private fun writeBits(bytes: ByteArray, at: Int, from: Int, n: Int, value: Int) {
            for (k in 0 until n) {
                val bit = (value shr (n - 1 - k)) and 1
                val pos = from + k
                val idx = at + pos / 8
                bytes[idx] = (bytes[idx].toInt() or (bit shl (7 - pos % 8))).toByte()
            }
        }
    }

    private companion object {
        const val FRAME = 192
        const val PAYLOAD = FRAME - 4 - 17

        fun marker(source: Int, frame: Int): Byte = ((source shl 6) or (frame % 64)).toByte()
    }
}
