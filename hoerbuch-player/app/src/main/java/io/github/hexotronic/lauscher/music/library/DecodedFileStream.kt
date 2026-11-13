package io.github.hexotronic.lauscher.music.library

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import android.util.Log
import io.github.hexotronic.lauscher.music.library.core.PlayPlan
import io.github.hexotronic.lauscher.music.library.core.Segment
import io.github.hexotronic.lauscher.music.synth.PcmStream
import java.io.File
import java.nio.ByteOrder

/**
 * Decodes a piece (Ogg Vorbis, MP3, …) on its own thread into a ring buffer of stereo float
 * frames at the mixer's sample rate. The piece can consist of several files and loops
 * ([plan]); joins get tiny fades so they never click. The audio thread only copies from the buffer.
 */
class DecodedFileStream(
    private val files: List<File>,
    private val plan: List<Segment>,
    override val trackId: String,
    private val gain: Float,
    private val outRate: Int,
) : PcmStream {
    private val capacity = outRate * BUFFER_SECONDS
    private val bufL = FloatArray(capacity)
    private val bufR = FloatArray(capacity)

    @Volatile
    private var readPos = 0L

    @Volatile
    private var writePos = 0L

    @Volatile
    private var decoded = false

    @Volatile
    private var closed = false

    override val finished: Boolean get() = decoded && readPos >= writePos

    init {
        Thread({ decodeAll() }, "LauscherMusicDecoder").apply {
            isDaemon = true
            start()
        }
    }

    override fun read(left: FloatArray, right: FloatArray, frames: Int): Int {
        val start = readPos
        val n = minOf(frames.toLong(), writePos - start).toInt()
        for (i in 0 until n) {
            val idx = ((start + i) % capacity).toInt()
            left[i] = bufL[idx] * gain
            right[i] = bufR[idx] * gain
        }
        readPos = start + n
        return n
    }

    override fun close() {
        closed = true
    }

    // ------------------------------------------------------------------ decoder thread

    private var announced = false
    private var resamplePos = 0.0
    private var prevL = 0f
    private var prevR = 0f

    private fun decodeAll() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        try {
            val steps = PlayPlan.expand(plan)
            steps.forEachIndexed { i, seg ->
                if (closed) return
                val file = files.getOrNull(seg.file) ?: return
                if (i > 0) Log.i(TAG, "Fortsetzung: $trackId, Teil ${i + 1} von ${steps.size}")
                decodeSegment(file, seg, first = i == 0, last = i == steps.lastIndex)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wiedergabe von $trackId fehlgeschlagen: ${e.message}")
        } finally {
            decoded = true
        }
    }

    /** Decodes [seg] of [file]; fades in at joins, out before jumps and at the very end. */
    private fun decodeSegment(file: File, seg: Segment, first: Boolean, last: Boolean) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
            val fromUs = (seg.fromSec * 1_000_000).toLong()
            val toUs = if (seg.toSec > 0) (seg.toSec * 1_000_000).toLong() else durationUs
            if (fromUs > 0) extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var chunkL = FloatArray(4096)
            var chunkR = FloatArray(4096)
            // Fades: a few milliseconds at joins against clicks, longer at the very end.
            val joinUs = JOIN_FADE_MS * 1000L
            val fadeInUs = if (first) 0L else joinUs
            val fadeOutUs = if (last) END_FADE_MS * 1000L else joinUs
            while (!closed) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(5_000)
                    if (inIndex >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                        if (size < 0 || (toUs > 0 && extractor.sampleTime > toUs + 200_000)) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 5_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    continue
                }
                if (outIndex < 0) continue
                var reachedEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size > 0) {
                    if (!announced) {
                        announced = true
                        Log.i(TAG, "Dekodiert: $trackId ($sampleRate Hz, $channels Kanäle)")
                    }
                    val out = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                    out.position(info.offset)
                    out.limit(info.offset + info.size)
                    val frames = info.size / (channels * if (floatPcm) 4 else 2)
                    if (chunkL.size < frames) {
                        chunkL = FloatArray(frames)
                        chunkR = FloatArray(frames)
                    }
                    var n = 0
                    val fb = if (floatPcm) out.asFloatBuffer() else null
                    val sb = if (floatPcm) null else out.asShortBuffer()
                    for (f in 0 until frames) {
                        val t = info.presentationTimeUs + f * 1_000_000L / sampleRate
                        if (t < fromUs) continue
                        if (toUs > 0 && t >= toUs) {
                            reachedEnd = true
                            break
                        }
                        var l = fb?.get(f * channels) ?: (sb!!.get(f * channels) / 32768f)
                        var r = if (channels > 1) fb?.get(f * channels + 1) ?: (sb!!.get(f * channels + 1) / 32768f) else l
                        val g = rampGain(t - fromUs, if (toUs > 0) toUs - t else Long.MAX_VALUE, fadeInUs, fadeOutUs)
                        l *= g
                        r *= g
                        chunkL[n] = l
                        chunkR[n] = r
                        n++
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    push(chunkL, chunkR, n, sampleRate)
                } else {
                    codec.releaseOutputBuffer(outIndex, false)
                }
                if (reachedEnd) break
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun rampGain(sinceStartUs: Long, untilEndUs: Long, fadeInUs: Long, fadeOutUs: Long): Float {
        var g = 1f
        if (fadeInUs > 0 && sinceStartUs < fadeInUs) g = sinceStartUs.toFloat() / fadeInUs
        if (fadeOutUs > 0 && untilEndUs < fadeOutUs) g = minOf(g, untilEndUs.toFloat() / fadeOutUs)
        return g.coerceIn(0f, 1f)
    }

    /** Resamples (linear) to [outRate] and appends to the ring buffer, waiting while it is full. */
    private fun push(l: FloatArray, r: FloatArray, frames: Int, inRate: Int) {
        val step = inRate.toDouble() / outRate
        var pos = resamplePos
        while (pos < frames && !closed) {
            if (writePos - readPos >= capacity - 1) {
                Thread.sleep(WAIT_MS)
                continue
            }
            val i = pos.toInt()
            val frac = (pos - i).toFloat()
            // Index -1 is the last frame of the previous buffer.
            val l0 = if (i == 0) prevL else l[i - 1]
            val r0 = if (i == 0) prevR else r[i - 1]
            val idx = (writePos % capacity).toInt()
            bufL[idx] = l0 + (l[i] - l0) * frac
            bufR[idx] = r0 + (r[i] - r0) * frac
            writePos += 1
            pos += step
        }
        resamplePos = pos - frames
        if (frames > 0) {
            prevL = l[frames - 1]
            prevR = r[frames - 1]
        }
    }

    companion object {
        private const val TAG = "LauscherMusik"
        private const val BUFFER_SECONDS = 3
        private const val WAIT_MS = 40L
        private const val JOIN_FADE_MS = 6L
        private const val END_FADE_MS = 2500L
    }
}
