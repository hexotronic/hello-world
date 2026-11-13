package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes a time range of an audio file into 16 kHz mono float samples, the input format
 * of whisper.cpp. Works for every format the phone can play (MP3, AAC/M4B, FLAC, Opus, WAV …).
 */
object AudioSegmentDecoder {
    const val TARGET_RATE = 16_000

    fun decode(context: Context, uri: Uri, startMs: Long, durationMs: Long): FloatArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(0)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            val startUs = startMs * 1000
            val endUs = (startMs + durationMs) * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val mono = FloatArrayBuilder((durationMs * sampleRate / 1000).toInt() + sampleRate)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0
            while (!outputDone && idleLoops < 500) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        val time = extractor.sampleTime
                        if (size < 0 || time > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, time, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex >= 0 -> {
                        idleLoops = 0
                        if (info.size > 0 && info.presentationTimeUs + 50_000 >= startUs) {
                            val out = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val frameBytes = channels * if (floatPcm) 4 else 2
                            val frames = info.size / frameBytes
                            // Skip the part of the first buffer that lies before the start.
                            val skip = if (info.presentationTimeUs < startUs) {
                                ((startUs - info.presentationTimeUs) * sampleRate / 1_000_000).toInt().coerceIn(0, frames)
                            } else {
                                0
                            }
                            if (floatPcm) {
                                val fb = out.asFloatBuffer()
                                for (f in skip until frames) {
                                    var sum = 0f
                                    for (c in 0 until channels) sum += fb.get(f * channels + c)
                                    mono.add(sum / channels)
                                }
                            } else {
                                val sb = out.asShortBuffer()
                                for (f in skip until frames) {
                                    var sum = 0
                                    for (c in 0 until channels) sum += sb.get(f * channels + c)
                                    mono.add(sum / (32768f * channels))
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (mono.size >= durationMs * sampleRate / 1000) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    else -> if (inputDone) idleLoops++
                }
            }
            val wanted = (durationMs * sampleRate / 1000).toInt()
            return resample(mono.toArray(minOf(wanted, mono.size)), sampleRate, TARGET_RATE)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** Linear-interpolation resampler with a simple anti-alias average when downsampling. */
    fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val ratio = from.toDouble() / to
        val out = FloatArray((input.size / ratio).toInt())
        val span = ratio.toInt().coerceAtLeast(1)
        for (i in out.indices) {
            val pos = i * ratio
            val idx = pos.toInt()
            if (span > 1) {
                var sum = 0f
                var n = 0
                for (k in idx until minOf(idx + span, input.size)) {
                    sum += input[k]
                    n++
                }
                out[i] = if (n > 0) sum / n else 0f
            } else {
                val frac = (pos - idx).toFloat()
                val a = input[idx.coerceAtMost(input.size - 1)]
                val b = input[(idx + 1).coerceAtMost(input.size - 1)]
                out[i] = a + (b - a) * frac
            }
        }
        return out
    }

    private class FloatArrayBuilder(capacity: Int) {
        private var data = FloatArray(capacity.coerceAtLeast(1024))
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(n: Int): FloatArray = data.copyOf(n)
    }
}
