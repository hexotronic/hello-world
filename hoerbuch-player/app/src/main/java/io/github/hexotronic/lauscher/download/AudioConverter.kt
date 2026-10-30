package io.github.hexotronic.lauscher.download

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.mp3.Mp3Encoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Turns the sound of any audio or video file the phone can decode into MP3, or copies it unchanged. */
object AudioConverter {
    /** MIME type of the first audio track, or null if there is none. */
    fun audioMime(context: Context, source: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, source, null)
            audioTrack(extractor)?.let { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        } catch (e: IOException) {
            null
        } finally {
            extractor.release()
        }
    }

    /** Decodes the first audio track of [source] and writes it as MP3 frames to [out]. */
    suspend fun toMp3(context: Context, source: Uri, out: OutputStream, quality: Mp3Quality, onProgress: (Float) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var encoder: Mp3Encoder? = null
        try {
            extractor.setDataSource(context, source, null)
            val track = audioTrack(extractor) ?: throw IOException("Die Datei hat keine Tonspur")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var pcm = ShortArray(0)
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val n = extractor.readSampleData(decoder.getInputBuffer(inIndex)!!, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = decoder.outputFormat
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    continue
                }
                if (outIndex < 0) continue
                val buf = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val samples = if (floatPcm) info.size / 4 else info.size / 2
                val frames = samples / channels
                if (frames > 0) {
                    val outChannels = minOf(channels, 2)
                    if (pcm.size < frames * outChannels) pcm = ShortArray(frames * outChannels)
                    readFrames(buf, floatPcm, channels, frames, outChannels, pcm)
                    val enc = encoder ?: (
                        Mp3Encoder.create(rate, outChannels, quality.kbps, quality.mono)
                            ?: throw IOException("MP3-Umwandlung bei $rate Hz nicht möglich")
                        ).also { encoder = it }
                    out.write(enc.encode(pcm, frames))
                }
                if (durationUs > 0) onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                decoder.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
            val enc = encoder ?: throw IOException("Die Tonspur ist leer")
            out.write(enc.flush())
        } finally {
            codec?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
            encoder?.close()
            extractor.release()
        }
    }

    /**
     * Copies the audio track of [source] into [target] without re-encoding (AAC → MP4/M4A,
     * Opus and Vorbis → WebM). Returns the file extension, or null for other codecs.
     */
    suspend fun extractAudio(context: Context, source: Uri, target: File): String? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            val track = audioTrack(extractor) ?: return null
            val format = extractor.getTrackFormat(track)
            val (container, extension) = when (format.getString(MediaFormat.KEY_MIME)) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 to "m4a"
                MediaFormat.MIMETYPE_AUDIO_OPUS, MediaFormat.MIMETYPE_AUDIO_VORBIS -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM to "webm"
                else -> return null
            }
            extractor.selectTrack(track)
            val muxer = MediaMuxer(target.path, container)
            try {
                val outTrack = muxer.addTrack(format)
                muxer.start()
                val size = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
                val buf = ByteBuffer.allocate(maxOf(size, 1 shl 20))
                val info = MediaCodec.BufferInfo()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) break
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                    info.set(0, n, extractor.sampleTime, flags)
                    muxer.writeSampleData(outTrack, buf, info)
                    extractor.advance()
                }
                muxer.stop()
            } finally {
                muxer.release()
            }
            return extension
        } finally {
            extractor.release()
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
    }

    /** Interleaved 16-bit samples; more than two channels are reduced to front left and right. */
    private fun readFrames(buf: ByteBuffer, floatPcm: Boolean, channels: Int, frames: Int, outChannels: Int, into: ShortArray) {
        if (floatPcm) {
            val f = buf.asFloatBuffer()
            for (i in 0 until frames) for (c in 0 until outChannels) {
                into[i * outChannels + c] = (f.get(i * channels + c).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
        } else {
            val s = buf.asShortBuffer()
            if (channels == outChannels) {
                s.get(into, 0, frames * channels)
            } else {
                for (i in 0 until frames) for (c in 0 until outChannels) into[i * outChannels + c] = s.get(i * channels + c)
            }
        }
    }
}
