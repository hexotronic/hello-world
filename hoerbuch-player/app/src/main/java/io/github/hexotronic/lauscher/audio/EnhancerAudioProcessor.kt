package io.github.hexotronic.lauscher.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.hexotronic.lauscher.audio.dsp.EnhancerEngine
import java.nio.ByteBuffer

/**
 * Plugs the [EnhancerEngine] into ExoPlayer's audio sink. It runs before the speed changer,
 * so noise reduction and "skip silence" both work on the original recording.
 *
 * The engine has a fixed latency; at the end of a stream the remaining samples are flushed
 * so no audio is lost at the end of a book.
 */
@OptIn(UnstableApi::class)
class EnhancerAudioProcessor : BaseAudioProcessor() {
    private var engine: EnhancerEngine? = null
    private var samples = FloatArray(0)
    private var shorts = ShortArray(0)
    private var tailPending = false

    override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = (format.encoding == C.ENCODING_PCM_16BIT || format.encoding == C.ENCODING_PCM_FLOAT) &&
            format.channelCount in 1..8 && format.sampleRate in 8_000..192_000
        // An inactive processor is skipped by the audio sink, so playback keeps working.
        return if (supported) format else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (isFloat) 4 else 2
        val channels = format.channelCount
        val frames = inputBuffer.remaining() / (bytesPerSample * channels)
        if (frames == 0) {
            if (tailPending) emitTail(isFloat)
            return
        }
        val eng = engineFor(format)
        val count = frames * channels
        ensureCapacity(count)
        if (isFloat) {
            inputBuffer.asFloatBuffer().get(samples, 0, count)
        } else {
            inputBuffer.asShortBuffer().get(shorts, 0, count)
            for (i in 0 until count) samples[i] = shorts[i] / 32768f
        }
        inputBuffer.position(inputBuffer.position() + count * bytesPerSample)
        eng.process(samples, frames, AudioEnhancerHub.settings, AudioEnhancerHub.bypass)
        write(count, isFloat)
    }

    private fun emitTail(isFloat: Boolean) {
        tailPending = false
        val eng = engine ?: return
        val frames = eng.latencyFrames
        val count = frames * eng.channels
        ensureCapacity(count)
        samples.fill(0f, 0, count)
        eng.process(samples, frames, AudioEnhancerHub.settings, AudioEnhancerHub.bypass)
        write(count, isFloat)
    }

    private fun write(count: Int, isFloat: Boolean) {
        val bytes = count * if (isFloat) 4 else 2
        val out = replaceOutputBuffer(bytes)
        if (isFloat) {
            out.asFloatBuffer().put(samples, 0, count)
        } else {
            for (i in 0 until count) {
                val v = samples[i] * 32768f
                shorts[i] = when {
                    v >= 32767f -> Short.MAX_VALUE
                    v <= -32768f -> Short.MIN_VALUE
                    else -> v.toInt().toShort()
                }
            }
            out.asShortBuffer().put(shorts, 0, count)
        }
        out.position(bytes)
        out.flip()
    }

    private fun ensureCapacity(count: Int) {
        if (samples.size < count) {
            samples = FloatArray(count)
            shorts = ShortArray(count)
        }
    }

    private fun engineFor(format: AudioProcessor.AudioFormat): EnhancerEngine {
        val current = engine
        if (current != null && current.sampleRate == format.sampleRate && current.channels == format.channelCount) {
            return current
        }
        return EnhancerEngine(format.sampleRate, format.channelCount).also {
            engine = it
            AudioEnhancerHub.engine = it
        }
    }

    override fun onQueueEndOfStream() {
        tailPending = engine != null
    }

    override fun isEnded(): Boolean = super.isEnded() && !tailPending

    override fun onFlush() {
        tailPending = false
        val format = inputAudioFormat
        if (format == AudioProcessor.AudioFormat.NOT_SET) return
        val current = engine
        if (current != null && current.sampleRate == format.sampleRate && current.channels == format.channelCount) {
            current.flush()
        } else {
            engineFor(format)
        }
    }

    override fun onReset() {
        tailPending = false
        engine = null
    }
}
