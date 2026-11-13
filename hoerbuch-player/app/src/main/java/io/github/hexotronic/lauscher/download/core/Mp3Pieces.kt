package io.github.hexotronic.lauscher.download.core

import java.io.OutputStream

/**
 * Lets several LAME encoders work on one long recording at the same time: the sound is cut into
 * pieces ([PcmPieces]), each piece is encoded on its own with a little of the sound before and
 * after it, and [Mp3Joiner] puts the MP3 frames back together into one seamless file.
 *
 * Why this works: every encoder starts on the same frame grid (pieces start at a multiple of
 * the frame size), so frame n of one encoder covers the same sound as frame n of the next. After
 * a second of warm-up the next encoder makes frames like the first one would. The only thing
 * that links MP3 frames is the bit reservoir: a frame may begin its data in the bytes of earlier
 * frames ("main_data_begin"). At the joint the joiner picks a frame whose reservoir bytes fit
 * into the unused end of the previous encoder's frames and copies them there. (Should none fit,
 * the frames that cannot find their data are made silent: a gap of a few hundredths of a
 * second instead of a crackle. Real encoders settle to the same reservoir, so this is rare.)
 */
object Mp3Pieces {
    /** Sound per piece; pieces run in parallel, so shorter pieces mean less memory. */
    const val FRAMES_PER_PIECE = 834 // ≈ 20 s at 48 kHz

    /** Frames encoded before a piece so the encoder is settled when its part begins. */
    const val WARM_FRAMES = 64

    /** Frames encoded after a piece, room to look for a good joint. */
    const val EXTRA_FRAMES = 64

    /** How far after the nominal start a joint is looked for. */
    const val SEARCH_FRAMES = 40

    /** MPEG-1 Layer III: 1152 samples per frame at 32, 44.1 and 48 kHz. */
    const val SAMPLES_PER_FRAME = 1152

    /** Pieces only work at the MPEG-1 rates (other rates are encoded in one go). */
    fun supports(sampleRate: Int) = sampleRate == 32_000 || sampleRate == 44_100 || sampleRate == 48_000
}

/** One piece of sound to encode: [frames] sample frames of interleaved [samples]. */
class PcmPiece(
    val index: Int,
    /** MP3 frame (of the whole file) that the encoder's first frame will be. */
    val firstFrame: Int,
    /** MP3 frame where this piece's own part begins (before it: warm-up). */
    val nominalStart: Int,
    val samples: ShortArray,
    val frames: Int,
    val last: Boolean,
)

/** An encoded [PcmPiece]: the complete output of its encoder, flushed. */
class EncodedPiece(val index: Int, val firstFrame: Int, val nominalStart: Int, val bytes: ByteArray)

/**
 * Collects decoded sound ([add]) and hands out [PcmPiece]s as soon as they are complete. If the
 * recording ends before the first piece is full, [finish] gives the whole sound as one piece.
 */
class PcmPieces(
    private val channels: Int,
    private val framesPerPiece: Int = Mp3Pieces.FRAMES_PER_PIECE,
    private val warmFrames: Int = Mp3Pieces.WARM_FRAMES,
    private val extraFrames: Int = Mp3Pieces.EXTRA_FRAMES,
    private val samplesPerFrame: Int = Mp3Pieces.SAMPLES_PER_FRAME,
) {
    private var buf = ShortArray(0)
    /** Sample frame (of the whole recording) at buf[0], and how many are in buf. */
    private var bufStart = 0L
    private var bufFrames = 0
    private var next = 0

    private fun nominal(piece: Int) = piece * framesPerPiece
    private fun inputStart(piece: Int) = maxOf(0, nominal(piece) - warmFrames).toLong() * samplesPerFrame
    private fun inputEnd(piece: Int) = (nominal(piece + 1) + extraFrames).toLong() * samplesPerFrame

    fun add(pcm: ShortArray, frames: Int): List<PcmPiece> {
        val need = (bufFrames + frames) * channels
        if (buf.size < need) buf = buf.copyOf(maxOf(need, buf.size * 2))
        System.arraycopy(pcm, 0, buf, bufFrames * channels, frames * channels)
        bufFrames += frames
        val done = ArrayList<PcmPiece>(1)
        while (bufStart + bufFrames >= inputEnd(next)) {
            done += slice(next, inputEnd(next), last = false)
            next++
            drop(inputStart(next))
        }
        return done
    }

    /** The last piece (the rest of the sound), or null if nothing is left to encode. */
    fun finish(): PcmPiece? {
        val end = bufStart + bufFrames
        if (end <= inputStart(next) && next > 0) return null
        return slice(next, end, last = true)
    }

    private fun slice(piece: Int, end: Long, last: Boolean): PcmPiece {
        val from = (inputStart(piece) - bufStart).toInt()
        val count = (end - inputStart(piece)).toInt()
        val samples = buf.copyOfRange(from * channels, (from + count) * channels)
        return PcmPiece(piece, (inputStart(piece) / samplesPerFrame).toInt(), nominal(piece), samples, count, last)
    }

    private fun drop(until: Long) {
        val n = (until - bufStart).toInt().coerceIn(0, bufFrames)
        if (n == 0) return
        System.arraycopy(buf, n * channels, buf, 0, (bufFrames - n) * channels)
        bufFrames -= n
        bufStart += n
    }
}

/** The frames of an MPEG-1/2 Layer III stream, with what is needed to join streams. */
class Mp3FrameList private constructor(
    val bytes: ByteArray,
    /** Byte offset of each frame. */
    val offset: IntArray,
    /** Byte offset of each frame's side info. */
    val sideOffset: IntArray,
    /** Byte offset and length of each frame's payload (after header, CRC and side info). */
    val payloadOffset: IntArray,
    val payloadLength: IntArray,
    val mainDataBegin: IntArray,
    /** Payload position (over all payloads one after another) where each frame's payload starts. */
    val payloadStart: IntArray,
    /** Highest payload position used by the data of this or an earlier frame. */
    val dataEnd: IntArray,
) {
    val size: Int get() = offset.size

    /** Turns frame [index] into silence: no data, nothing borrowed from earlier frames. */
    fun silence(index: Int) {
        bytes.fill(0, sideOffset[index], payloadOffset[index])
        mainDataBegin[index] = 0
    }

    /** Byte offset where frame [index] begins ([bytes] size for the end). */
    fun byteOffset(index: Int): Int = if (index < size) offset[index] else bytes.size

    /** Bytes before frame [frame]'s payload that no earlier frame uses. */
    fun freeBefore(frame: Int): Int = payloadStart[frame] - (if (frame > 0) dataEnd[frame - 1] else 0)

    /** Byte offset in [bytes] of payload position [pos] (which must lie in frame < [before]). */
    fun byteAt(pos: Int, before: Int): Int {
        var f = before - 1
        while (f > 0 && payloadStart[f] > pos) f--
        require(pos >= payloadStart[f] && pos < payloadStart[f] + payloadLength[f]) { "Position $pos liegt nicht in den Rahmen" }
        return payloadOffset[f] + (pos - payloadStart[f])
    }

    companion object {
        private val BITRATES_V1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
        private val BITRATES_V2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
        private val RATES_V1 = intArrayOf(44_100, 48_000, 32_000)

        /** Parses [bytes], which must hold nothing but Layer III frames (as LAME writes them). */
        fun parse(bytes: ByteArray): Mp3FrameList {
            val offsets = ArrayList<Int>()
            val sideOffsets = ArrayList<Int>()
            val payloadOffsets = ArrayList<Int>()
            val payloadLengths = ArrayList<Int>()
            val mdbs = ArrayList<Int>()
            val bits = ArrayList<Int>()
            var pos = 0
            while (pos + 4 <= bytes.size) {
                val b1 = bytes[pos + 1].toInt() and 0xFF
                val b2 = bytes[pos + 2].toInt() and 0xFF
                val b3 = bytes[pos + 3].toInt() and 0xFF
                require((bytes[pos].toInt() and 0xFF) == 0xFF && (b1 and 0xE0) == 0xE0) { "Kein MP3-Rahmen bei Byte $pos" }
                val version = (b1 shr 3) and 3 // 3: MPEG-1, 2: MPEG-2, 0: MPEG-2.5
                require(version != 1 && ((b1 shr 1) and 3) == 1) { "Kein Layer-III-Rahmen bei Byte $pos" }
                val mpeg1 = version == 3
                val crc = (b1 and 1) == 0
                val bitrateIndex = b2 shr 4
                val rateIndex = (b2 shr 2) and 3
                require(bitrateIndex in 1..14 && rateIndex < 3) { "Ungültiger Rahmenkopf bei Byte $pos" }
                val rate = RATES_V1[rateIndex] shr (when (version) { 3 -> 0; 2 -> 1; else -> 2 })
                val kbps = (if (mpeg1) BITRATES_V1 else BITRATES_V2)[bitrateIndex]
                val padding = (b2 shr 1) and 1
                val length = (if (mpeg1) 144 else 72) * kbps * 1000 / rate + padding
                val mono = (b3 shr 6) == 3
                val channels = if (mono) 1 else 2
                val sideLength = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
                val side = pos + 4 + (if (crc) 2 else 0)
                require(pos + length <= bytes.size && side + sideLength <= pos + length) { "Rahmen bei Byte $pos ist unvollständig" }
                val reader = BitReader(bytes, side)
                val mdb = reader.read(if (mpeg1) 9 else 8)
                var dataBits = 0
                if (mpeg1) {
                    reader.skip((if (mono) 5 else 3) + 4 * channels)
                    repeat(2 * channels) {
                        dataBits += reader.read(12)
                        reader.skip(59 - 12)
                    }
                } else {
                    reader.skip(if (mono) 1 else 2)
                    repeat(channels) {
                        dataBits += reader.read(12)
                        reader.skip(63 - 12)
                    }
                }
                offsets += pos
                sideOffsets += side
                payloadOffsets += side + sideLength
                payloadLengths += pos + length - (side + sideLength)
                mdbs += mdb
                bits += dataBits
                pos += length
            }
            require(pos == bytes.size) { "Unvollständiger Rahmen am Ende" }
            val n = offsets.size
            val payloadStart = IntArray(n)
            val dataEnd = IntArray(n)
            var start = 0
            var end = 0
            for (i in 0 until n) {
                payloadStart[i] = start
                end = maxOf(end, start - mdbs[i] + (bits[i] + 7) / 8)
                dataEnd[i] = end
                start += payloadLengths[i]
            }
            return Mp3FrameList(bytes, offsets.toIntArray(), sideOffsets.toIntArray(), payloadOffsets.toIntArray(), payloadLengths.toIntArray(), mdbs.toIntArray(), payloadStart, dataEnd)
        }
    }

    private class BitReader(private val bytes: ByteArray, private val start: Int) {
        private var bit = 0

        fun read(n: Int): Int {
            var v = 0
            repeat(n) {
                val b = bytes[start + (bit shr 3)].toInt() shr (7 - (bit and 7)) and 1
                v = (v shl 1) or b
                bit++
            }
            return v
        }

        fun skip(n: Int) {
            bit += n
        }
    }
}

/**
 * Writes [EncodedPiece]s (in order) to [out] as one MP3 stream. Each piece is held until the
 * next one arrives, because the joint lies a little after the piece's nominal end.
 */
class Mp3Joiner(private val out: OutputStream) {
    private var held: Mp3FrameList? = null
    /** Frame of the whole file that [held]'s frame 0 is. */
    private var heldFirst = 0
    /** First frame of [held] not written yet. */
    private var heldFrom = 0

    /** Frames (of the whole file) where the pieces were joined. */
    val joints = ArrayList<Int>()

    /** Joints where no frame fitted cleanly (one frame may sound wrong there); for tests and logs. */
    var roughJoints = 0
        private set

    fun add(piece: EncodedPiece) {
        val cur = Mp3FrameList.parse(piece.bytes)
        val prev = held
        if (prev == null) {
            held = cur
            heldFirst = piece.firstFrame
            heldFrom = 0
            return
        }
        // Candidates: frames both encoders made, after the next one's warm-up and before the
        // previous one's last frames (which already feel the end of its sound).
        val from = piece.nominalStart
        val to = minOf(
            piece.nominalStart + Mp3Pieces.SEARCH_FRAMES,
            heldFirst + prev.size - SAFE_TAIL,
            piece.firstFrame + cur.size - 1,
        )
        require(from <= to && from - heldFirst - 1 >= heldFrom && from - piece.firstFrame >= MIN_WARM) { "Teile passen nicht zusammen" }
        var joint = -1
        var best = -1
        var bestMissing = Int.MAX_VALUE
        for (g in from..to) {
            val needed = cur.mainDataBegin[g - piece.firstFrame]
            val missing = needed - prev.freeBefore(g - heldFirst)
            if (missing <= 0) {
                joint = g
                break
            }
            if (missing < bestMissing) {
                bestMissing = missing
                best = g
            }
        }
        if (joint < 0) joint = best
        joints += joint
        val p = joint - heldFirst
        val c = joint - piece.firstFrame
        // The new frame's reservoir bytes go into the unused end of the previous frames.
        val count = minOf(cur.mainDataBegin[c], prev.freeBefore(p))
        for (i in 1..count) {
            prev.bytes[prev.byteAt(prev.payloadStart[p] - i, p)] = cur.bytes[cur.byteAt(cur.payloadStart[c] - i, c)]
        }
        // Frames whose data would begin before the copied bytes cannot be decoded: silence them.
        var f = c
        while (f < cur.size && cur.payloadStart[f] - cur.mainDataBegin[f] < cur.payloadStart[c] - count) cur.silence(f++)
        if (f > c) roughJoints++
        out.write(prev.bytes, prev.byteOffset(heldFrom), prev.byteOffset(p) - prev.byteOffset(heldFrom))
        held = cur
        heldFirst = piece.firstFrame
        heldFrom = c
    }

    /** Writes the rest; call after the last piece. */
    fun finish() {
        val prev = held ?: return
        out.write(prev.bytes, prev.byteOffset(heldFrom), prev.bytes.size - prev.byteOffset(heldFrom))
        held = null
    }

    private companion object {
        /** The last frames of a piece are made with the encoder's end padding. */
        const val SAFE_TAIL = 8

        /** Frames a piece needs before its first frame that may be used. */
        const val MIN_WARM = 16
    }
}
