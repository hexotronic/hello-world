package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Adaptive spectral noise reduction for a stationary noise floor (hiss, hum, fan noise, tape
 * noise, room tone).
 *
 * - STFT with sqrt-Hann analysis/synthesis windows and 75 % overlap.
 * - Noise power per frequency bin is estimated with minimum statistics over a sliding window
 *   of [NOISE_WINDOW_SECONDS], so no "noise sample" has to be selected by the user.
 * - Gains follow the decision-directed Wiener rule (Ephraim/Malah), which keeps "musical noise"
 *   artefacts low, and are floored according to the requested strength.
 *
 * The block has a constant latency of [latency] frames; output count always equals input count.
 * With strength 0 the signal is reconstructed exactly (just delayed), so the effect can be toggled
 * without clicks or time jumps.
 */
class SpectralDenoiser(sampleRate: Int, private val channels: Int) {
    val fftSize: Int = when {
        sampleRate <= 12_000 -> 256
        sampleRate <= 24_000 -> 512
        sampleRate <= 50_000 -> 1024
        else -> 2048
    }
    val hop = fftSize / 4

    /** A sample leaves the overlap-add pipeline exactly one FFT frame after it entered. */
    val latency = fftSize
    private val fifoStart = fftSize - hop
    val bins = fftSize / 2 + 1
    val binHz = sampleRate.toFloat() / fftSize

    private val fft = Fft(fftSize)
    private val window = FloatArray(fftSize) { n ->
        sqrt(0.5 - 0.5 * cos(2 * PI * n / fftSize)).toFloat()
    }
    private val olaScale: Float
    private val re = FloatArray(fftSize)
    private val im = FloatArray(fftSize)
    private val rawGain = FloatArray(bins)

    private val subWindowFrames: Int
    private val warmupFrames: Int

    private val state = Array(channels) { ChannelState() }

    /** Latest analysis of channel 0 for the live spectrum display (linear power per bin). */
    val displayInput = FloatArray(bins)
    val displayNoise = FloatArray(bins)
    val displayOutput = FloatArray(bins)

    @Volatile
    var displayVersion = 0L
        private set

    init {
        var sum = 0f
        var n = 0
        while (n < fftSize) {
            sum += window[n] * window[n]
            n += hop
        }
        olaScale = 1f / sum
        val framesPerSecond = sampleRate.toFloat() / hop
        subWindowFrames = max(4, (NOISE_WINDOW_SECONDS * framesPerSecond / SUB_WINDOWS).toInt())
        warmupFrames = (WARMUP_SECONDS * framesPerSecond).toInt()
    }

    private inner class ChannelState {
        val input = FloatArray(fftSize)
        val output = FloatArray(hop)
        val accumulator = FloatArray(fftSize)
        var rover = fifoStart
        val smoothedPower = FloatArray(bins)
        val previousClean = FloatArray(bins)
        val currentMin = FloatArray(bins) { Float.MAX_VALUE }
        val subMinima = Array(SUB_WINDOWS) { FloatArray(bins) { Float.MAX_VALUE } }
        val noise = FloatArray(bins)
        var subIndex = 0
        var subCount = 0
        var framesSinceReset = 0
        var hasNoiseEstimate = false

        fun resetStream() {
            input.fill(0f)
            output.fill(0f)
            accumulator.fill(0f)
            rover = fifoStart
        }

        fun resetNoise() {
            smoothedPower.fill(0f)
            previousClean.fill(0f)
            currentMin.fill(Float.MAX_VALUE)
            for (m in subMinima) m.fill(Float.MAX_VALUE)
            noise.fill(0f)
            subIndex = 0
            subCount = 0
            framesSinceReset = 0
            hasNoiseEstimate = false
        }
    }

    /** Clears the audio pipeline (e.g. after a seek) but keeps the learned noise profile. */
    fun flush() {
        for (s in state) s.resetStream()
    }

    fun reset() {
        for (s in state) {
            s.resetStream()
            s.resetNoise()
        }
    }

    /**
     * Processes [frames] interleaved frames in place.
     * @param strength 0 = transparent (delay only), 1 = maximum suppression.
     * @param freeze keep the current noise profile instead of adapting it.
     */
    fun process(buffer: FloatArray, frames: Int, strength: Float, freeze: Boolean) {
        for (ch in 0 until channels) {
            val s = state[ch]
            var idx = ch
            for (i in 0 until frames) {
                s.input[s.rover] = buffer[idx]
                buffer[idx] = s.output[s.rover - fifoStart]
                s.rover++
                if (s.rover >= fftSize) {
                    s.rover = fifoStart
                    processFrame(s, ch, strength, freeze)
                }
                idx += channels
            }
        }
    }

    private fun processFrame(s: ChannelState, channel: Int, strength: Float, freeze: Boolean) {
        val n = fftSize
        for (i in 0 until n) {
            re[i] = s.input[i] * window[i]
            im[i] = 0f
        }
        fft.forward(re, im)

        // The noise estimate is always tracked so that it is ready the moment the user turns
        // the effect on.
        updateNoiseEstimate(s, freeze)
        val publish = channel == 0
        if (publish) {
            for (k in 0 until bins) {
                displayInput[k] = re[k] * re[k] + im[k] * im[k]
                displayNoise[k] = s.noise[k]
            }
        }

        val effective = if (s.framesSinceReset < warmupFrames) {
            strength * s.framesSinceReset / warmupFrames
        } else {
            strength
        }
        if (effective > 0.001f && s.hasNoiseEstimate) {
            computeGains(s, effective)
            for (k in 0 until bins) {
                val g = rawGain[k]
                re[k] *= g
                im[k] *= g
                if (k in 1 until n / 2) {
                    re[n - k] *= g
                    im[n - k] *= g
                }
            }
        } else {
            for (k in 0 until bins) s.previousClean[k] = re[k] * re[k] + im[k] * im[k]
        }
        if (publish) {
            for (k in 0 until bins) displayOutput[k] = re[k] * re[k] + im[k] * im[k]
            displayVersion++
        }

        fft.inverse(re, im)
        val acc = s.accumulator
        for (i in 0 until n) acc[i] += re[i] * window[i] * olaScale
        System.arraycopy(acc, 0, s.output, 0, hop)
        System.arraycopy(acc, hop, acc, 0, n - hop)
        acc.fill(0f, n - hop, n)
        System.arraycopy(s.input, hop, s.input, 0, n - hop)
    }

    private fun updateNoiseEstimate(s: ChannelState, freeze: Boolean) {
        if (s.framesSinceReset < Int.MAX_VALUE) s.framesSinceReset++
        val sp = s.smoothedPower
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            sp[k] = POWER_SMOOTHING * sp[k] + (1 - POWER_SMOOTHING) * p
        }
        if (freeze && s.hasNoiseEstimate) return

        for (k in 0 until bins) {
            if (sp[k] < s.currentMin[k]) s.currentMin[k] = sp[k]
        }
        s.subCount++
        if (s.subCount >= subWindowFrames) {
            System.arraycopy(s.currentMin, 0, s.subMinima[s.subIndex], 0, bins)
            s.subIndex = (s.subIndex + 1) % SUB_WINDOWS
            s.currentMin.fill(Float.MAX_VALUE)
            s.subCount = 0
        }
        for (k in 0 until bins) {
            var m = s.currentMin[k]
            for (w in 0 until SUB_WINDOWS) m = min(m, s.subMinima[w][k])
            s.noise[k] = m * MINIMUM_BIAS
        }
        s.hasNoiseEstimate = true
    }

    private fun computeGains(s: ChannelState, strength: Float) {
        val overSubtraction = 1f + 1.5f * strength
        val floor = 10f.pow(-(8f + 22f * strength) / 20f)
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            val noisePower = max(s.noise[k] * overSubtraction, 1e-12f)
            val posterior = p / noisePower
            val prior = DD_ALPHA * s.previousClean[k] / noisePower +
                (1 - DD_ALPHA) * max(posterior - 1f, 0f)
            var g = prior / (1f + prior)
            if (g < floor) g = floor
            rawGain[k] = g
        }
        // Light smoothing across frequency reduces isolated "musical" tones.
        var prev = rawGain[0]
        for (k in 1 until bins - 1) {
            val cur = rawGain[k]
            rawGain[k] = 0.25f * prev + 0.5f * cur + 0.25f * rawGain[k + 1]
            prev = cur
        }
        for (k in 0 until bins) {
            val p = re[k] * re[k] + im[k] * im[k]
            s.previousClean[k] = rawGain[k] * rawGain[k] * p
        }
    }

    companion object {
        const val NOISE_WINDOW_SECONDS = 1.6f
        const val SUB_WINDOWS = 8
        const val WARMUP_SECONDS = 0.4f
        const val POWER_SMOOTHING = 0.8f
        const val DD_ALPHA = 0.98f

        /** Compensates the downward bias of the minimum of a smoothed periodogram. */
        const val MINIMUM_BIAS = 2.2f
    }
}
