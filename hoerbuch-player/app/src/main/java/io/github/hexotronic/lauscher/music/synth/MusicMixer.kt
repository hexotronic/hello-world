package io.github.hexotronic.lauscher.music.synth

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Renders the background music: cross-fades between scenes, levels all moods to the same
 * loudness, ducks under speech and adds reverb. Thread-safe hand-off: control methods may be
 * called from any thread, [render] runs on the audio thread.
 */
class MusicMixer(private val sampleRate: Int) {
    private class Layer(val generator: Generator, var gain: Float, var target: Float)

    private val layers = ArrayList<Layer>()
    private val reverb = Reverb(sampleRate)
    private val left = FloatArray(MAX_BLOCK)
    private val right = FloatArray(MAX_BLOCK)
    private var current: SoundSpec? = null

    @Volatile
    private var pendingSpec: SoundSpec? = null

    @Volatile
    private var specChanged = false

    @Volatile
    private var chimeRequested = false

    /** 0..1 user volume. */
    @Volatile
    var volume = 0.5f

    /** How much quieter the music gets while someone speaks (dB). */
    @Volatile
    var duckDepthDb = 8f

    @Volatile
    var crossfadeSeconds = 4f

    /** Level of the narration in dBFS, updated from the audio processor. */
    @Volatile
    var speechLevelDb = -90f

    private var duckGain = 1f
    private var agcGain = 1f
    private var meanPower = TARGET_RMS * TARGET_RMS
    private var reverbWet = 0.25f

    /** Current loudness reduction caused by speech, for the UI (0..1). */
    val duckingAmount: Float get() = 1f - duckGain

    fun setTarget(spec: SoundSpec?) {
        pendingSpec = spec
        specChanged = true
    }

    fun playChime() {
        chimeRequested = true
    }

    /** True when nothing is audible anymore (all layers faded out). */
    val isSilent: Boolean get() = synchronized(layers) { layers.isEmpty() }

    /** Renders [frames] interleaved stereo frames into [out] (overwrites). */
    fun render(out: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(MAX_BLOCK, frames - done)
            renderBlock(n)
            for (i in 0 until n) {
                out[(done + i) * 2] = left[i]
                out[(done + i) * 2 + 1] = right[i]
            }
            done += n
        }
    }

    private fun renderBlock(n: Int) {
        if (specChanged) {
            specChanged = false
            applySpec(pendingSpec)
        }
        if (chimeRequested) {
            chimeRequested = false
            synchronized(layers) { layers.add(Layer(ChimeGenerator(sampleRate, System.nanoTime()), 1f, 1f)) }
        }
        left.fill(0f, 0, n)
        right.fill(0f, 0, n)
        val fadeStep = n / (crossfadeSeconds.coerceAtLeast(0.2f) * sampleRate)
        synchronized(layers) {
            val tmpL = scratchL
            val tmpR = scratchR
            var wetSum = 0f
            var gainSum = 0f
            val it = layers.iterator()
            while (it.hasNext()) {
                val layer = it.next()
                layer.gain = when {
                    layer.gain < layer.target -> minOf(layer.target, layer.gain + fadeStep)
                    layer.gain > layer.target -> maxOf(layer.target, layer.gain - fadeStep)
                    else -> layer.gain
                }
                val chime = layer.generator as? ChimeGenerator
                if ((layer.target == 0f && layer.gain <= 0f) || chime?.finished == true) {
                    it.remove()
                    continue
                }
                tmpL.fill(0f, 0, n)
                tmpR.fill(0f, 0, n)
                layer.generator.render(tmpL, tmpR, n)
                // Equal-power style curve for smooth cross-fades.
                val g = layer.gain * layer.gain * (3 - 2 * layer.gain)
                for (i in 0 until n) {
                    left[i] += tmpL[i] * g
                    right[i] += tmpR[i] * g
                }
                wetSum += layer.generator.reverb * g
                gainSum += g
            }
            if (gainSum > 0f) reverbWet = wetSum / gainSum
        }
        reverb.process(left, right, n, reverbWet)

        // Slow automatic gain: every mood ends up equally loud.
        var p = 0f
        for (i in 0 until n) p += left[i] * left[i] + right[i] * right[i]
        p /= (2 * n)
        if (p > SILENCE_POWER) {
            val a = exp(-n / (AGC_SECONDS * sampleRate))
            meanPower = a * meanPower + (1 - a) * p
        }
        val agcTarget = (TARGET_RMS / sqrt(max(meanPower, 1e-9f))).coerceIn(0.25f, 6f)
        val agcStep = exp(-n / (0.8f * sampleRate))
        agcGain = agcStep * agcGain + (1 - agcStep) * agcTarget

        // Duck under speech: fast down, slow up.
        val duckTarget = if (speechLevelDb > SPEECH_THRESHOLD_DB) 10f.pow(-duckDepthDb / 20f) else 1f
        val coef = if (duckTarget < duckGain) exp(-n / (0.15f * sampleRate)) else exp(-n / (1.5f * sampleRate))
        duckGain = coef * duckGain + (1 - coef) * duckTarget

        val v = volume.coerceIn(0f, 1f)
        val master = agcGain * duckGain * v * v * MASTER
        for (i in 0 until n) {
            left[i] = softClip(left[i] * master)
            right[i] = softClip(right[i] * master)
        }
    }

    private val scratchL = FloatArray(MAX_BLOCK)
    private val scratchR = FloatArray(MAX_BLOCK)

    private fun applySpec(spec: SoundSpec?) {
        if (spec == current) return
        current = spec
        synchronized(layers) {
            for (l in layers) if (l.generator !is ChimeGenerator) l.target = 0f
            if (spec != null) {
                spec.mood?.let { layers.add(Layer(MoodGenerator(sampleRate, it, spec.intensity, spec.seed), 0f, 1f)) }
                spec.atmosphere?.let { layers.add(Layer(AtmosphereGenerator(sampleRate, it, 1f, spec.seed), 0f, 1f)) }
            }
        }
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a < 0.8f) return x
        val y = 0.8f + 0.2f * ((a - 0.8f) / (a - 0.8f + 0.2f))
        return if (x < 0) -y else y
    }

    companion object {
        const val MAX_BLOCK = 512
        private const val TARGET_RMS = 0.12f
        private const val MASTER = 0.9f
        private const val AGC_SECONDS = 4f
        private const val SPEECH_THRESHOLD_DB = -42f
        private val SILENCE_POWER = 1e-7f

        fun dbOf(power: Float): Float = 10f * log10(max(power, 1e-12f))
    }
}
