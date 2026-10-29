package io.github.hexotronic.lauscher.music.synth

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Renders the background music: cross-fades between scenes, levels all moods to the same
 * loudness, ducks under speech and adds reverb. Moods come from the synthesizer or, when a
 * [recordings] source has a piece for them, from the downloaded music library.
 * Thread-safe hand-off: control methods may be called from any thread, [render] runs on the
 * audio thread.
 */
class MusicMixer(private val sampleRate: Int) {
    private class Layer(val generator: Generator, var gain: Float, var target: Float)

    private val layers = ArrayList<Layer>()
    private val reverb = Reverb(sampleRate)
    private val left = FloatArray(MAX_BLOCK)
    private val right = FloatArray(MAX_BLOCK)
    private val recLeft = FloatArray(MAX_BLOCK)
    private val recRight = FloatArray(MAX_BLOCK)
    private var current: SoundSpec? = null

    @Volatile
    private var pendingSpec: SoundSpec? = null

    @Volatile
    private var specChanged = false

    @Volatile
    private var chimeRequested = false

    @Volatile
    private var refreshRequested = false

    @Volatile
    private var closeRequested = false

    /** Recorded music for moods; null plays everything with the synthesizer. */
    @Volatile
    var recordings: RecordingSource? = null

    /** Library id of the recorded piece that is currently playing, if any. */
    @Volatile
    var currentTrackId: String? = null
        private set

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

    /** Picks the sound for the current target again, e.g. after the music source was changed. */
    fun refresh() {
        refreshRequested = true
    }

    /** Stops everything at once and frees the decoders. */
    fun closeAll() {
        closeRequested = true
        synchronized(layers) {
            layers.forEach { it.generator.close() }
            layers.clear()
        }
        currentTrackId = null
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
        if (closeRequested) {
            closeRequested = false
            current = null
        }
        if (specChanged || refreshRequested) {
            val force = refreshRequested
            specChanged = false
            refreshRequested = false
            applySpec(pendingSpec, force)
        }
        if (chimeRequested) {
            chimeRequested = false
            synchronized(layers) { layers.add(Layer(ChimeGenerator(sampleRate, System.nanoTime()), 1f, 1f)) }
        }
        left.fill(0f, 0, n)
        right.fill(0f, 0, n)
        recLeft.fill(0f, 0, n)
        recRight.fill(0f, 0, n)
        val fadeStep = n / (crossfadeSeconds.coerceAtLeast(0.2f) * sampleRate)
        synchronized(layers) {
            val tmpL = scratchL
            val tmpR = scratchR
            var wetSum = 0f
            var gainSum = 0f
            var playingTrack: String? = null
            var playingGain = 0f
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
                    layer.generator.close()
                    it.remove()
                    continue
                }
                tmpL.fill(0f, 0, n)
                tmpR.fill(0f, 0, n)
                layer.generator.render(tmpL, tmpR, n)
                // Equal-power style curve for smooth cross-fades.
                val g = layer.gain * layer.gain * (3 - 2 * layer.gain)
                // Recordings are already levelled and get no automatic gain, so they keep their dynamics.
                val recording = layer.generator as? RecordingGenerator
                val busL = if (recording != null) recLeft else left
                val busR = if (recording != null) recRight else right
                for (i in 0 until n) {
                    busL[i] += tmpL[i] * g
                    busR[i] += tmpR[i] * g
                }
                if (recording != null && layer.target > 0f && g >= playingGain) {
                    playingGain = g
                    playingTrack = recording.trackId
                }
                wetSum += layer.generator.reverb * g
                gainSum += g
            }
            if (gainSum > 0f) reverbWet = wetSum / gainSum
            currentTrackId = playingTrack
        }

        // Slow automatic gain on the synthesizer: every mood ends up equally loud.
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
        for (i in 0 until n) {
            left[i] = left[i] * agcGain + recLeft[i]
            right[i] = right[i] * agcGain + recRight[i]
        }
        reverb.process(left, right, n, reverbWet)

        // Duck under speech: fast down, slow up.
        val duckTarget = if (speechLevelDb > SPEECH_THRESHOLD_DB) 10f.pow(-duckDepthDb / 20f) else 1f
        val coef = if (duckTarget < duckGain) exp(-n / (0.15f * sampleRate)) else exp(-n / (1.5f * sampleRate))
        duckGain = coef * duckGain + (1 - coef) * duckTarget

        val v = volume.coerceIn(0f, 1f)
        val master = duckGain * v * v * MASTER
        for (i in 0 until n) {
            left[i] = softClip(left[i] * master)
            right[i] = softClip(right[i] * master)
        }
    }

    private val scratchL = FloatArray(MAX_BLOCK)
    private val scratchR = FloatArray(MAX_BLOCK)

    private fun applySpec(spec: SoundSpec?, force: Boolean) {
        if (spec == current && !force) return
        current = spec
        synchronized(layers) {
            val mood = spec?.mood
            // A recorded piece keeps playing across scenes of the same mood instead of restarting.
            val keep = if (force || mood == null || spec.trackId != null) {
                null
            } else {
                layers.firstOrNull { l -> l.target > 0f && (l.generator as? RecordingGenerator)?.mood == mood }
            }
            for (l in layers) if (l.generator !is ChimeGenerator && l !== keep) l.target = 0f
            if (spec != null) {
                if (mood != null && keep == null) {
                    val source = recordings
                    val stream = source?.open(mood, spec.intensity, spec.trackId)
                    val generator = if (source != null && stream != null) {
                        RecordingGenerator(sampleRate, source, mood, spec.intensity, stream)
                    } else {
                        MoodGenerator(sampleRate, mood, spec.intensity, spec.seed)
                    }
                    layers.add(Layer(generator, 0f, 1f))
                }
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
