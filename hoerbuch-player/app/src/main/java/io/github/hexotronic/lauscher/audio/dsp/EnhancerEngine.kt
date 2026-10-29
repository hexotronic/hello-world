package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

/**
 * The complete "Klangstudio" chain for one audio format. Works on interleaved float samples
 * in the range -1..1 and keeps a constant latency of [latencyFrames].
 *
 * Order: mono/declick → rumble filter → noise reduction → gate → de-esser → hiss filter →
 * voice clarity → equalizer → compressor → auto level → boost → balance → limiter.
 */
class EnhancerEngine(val sampleRate: Int, val channels: Int) {
    private val denoiser = SpectralDenoiser(sampleRate, channels)
    private val declicker = Declicker(sampleRate, channels)
    private val rumble = Array(2) { Biquad(channels) }
    private val hiss = Array(2) { Biquad(channels) }
    private val clarityPresence = Biquad(channels)
    private val clarityMud = Biquad(channels)
    private val equalizer = Array(EnhancerSettings.EQ_FREQUENCIES.size) { Biquad(channels) }
    private val gate = NoiseGate(sampleRate, channels)
    private val deEsser = DeEsser(sampleRate, channels)
    private val compressor = Compressor(sampleRate, channels)
    private val autoLevel = AutoLevel(sampleRate, channels)
    private val limiter = Limiter(sampleRate, channels)

    private var configured: EnhancerSettings? = null
    private var currentBoost = 1f

    val latencyFrames: Int get() = denoiser.latency

    val compressorReductionDb: Float get() = compressor.currentReductionDb
    val autoLevelGainDb: Float get() = autoLevel.currentGainDb

    /** Called after a seek: drop buffered audio and filter state but keep the noise profile. */
    fun flush() {
        denoiser.flush()
        declicker.reset()
        for (f in rumble) f.reset()
        for (f in hiss) f.reset()
        clarityPresence.reset()
        clarityMud.reset()
        for (f in equalizer) f.reset()
        gate.reset()
        deEsser.reset()
        compressor.reset()
        limiter.reset()
    }

    fun reset() {
        flush()
        denoiser.reset()
        autoLevel.reset()
    }

    /**
     * @param bypass true while the A/B comparison button is held: the original signal is played
     *   with the same latency so switching is seamless.
     */
    fun process(buffer: FloatArray, frames: Int, settings: EnhancerSettings, bypass: Boolean) {
        if (frames == 0) return
        val active = settings.enabled && !bypass
        if (!active) {
            denoiser.process(buffer, frames, 0f, settings.freezeNoiseProfile)
            return
        }
        if (settings !== configured) configure(settings)

        if (settings.mono && channels == 2) {
            var idx = 0
            for (i in 0 until frames) {
                val m = 0.5f * (buffer[idx] + buffer[idx + 1])
                buffer[idx] = m
                buffer[idx + 1] = m
                idx += 2
            }
        }
        if (settings.declick > 0f) declicker.process(buffer, frames, settings.declick)
        applyFilters(buffer, frames, rumble)

        denoiser.process(buffer, frames, settings.noiseReduction, settings.freezeNoiseProfile)

        if (settings.noiseGate > 0f) gate.process(buffer, frames, settings.noiseGate)
        if (settings.deEsser > 0f) deEsser.process(buffer, frames, settings.deEsser)
        applyFilters(buffer, frames, hiss)
        if (settings.voiceClarity > 0f) {
            applyFilter(buffer, frames, clarityMud)
            applyFilter(buffer, frames, clarityPresence)
        }
        if (settings.equalizerEnabled) applyFilters(buffer, frames, equalizer)
        if (settings.compressor > 0f) compressor.process(buffer, frames, settings.compressor)
        if (settings.autoLevel) autoLevel.process(buffer, frames)
        applyBoostAndBalance(buffer, frames, settings)
        limiter.process(buffer, frames)
    }

    private fun configure(s: EnhancerSettings) {
        if (s.rumbleFilterHz > 0) {
            for (i in rumble.indices) rumble[i].highPass(sampleRate, s.rumbleFilterHz.toDouble(), Biquad.BUTTERWORTH_4_Q[i])
        } else {
            for (f in rumble) f.setIdentity()
        }
        if (s.hissFilterHz > 0 && s.hissFilterHz < sampleRate * 0.45) {
            for (i in hiss.indices) hiss[i].lowPass(sampleRate, s.hissFilterHz.toDouble(), Biquad.BUTTERWORTH_4_Q[i])
        } else {
            for (f in hiss) f.setIdentity()
        }
        clarityPresence.peaking(sampleRate, 3000.0, 0.8, 7.0 * s.voiceClarity)
        clarityMud.lowShelf(sampleRate, 220.0, -5.0 * s.voiceClarity)
        for (i in equalizer.indices) {
            val f = EnhancerSettings.EQ_FREQUENCIES[i]
            if (f < sampleRate * 0.45) {
                equalizer[i].peaking(sampleRate, f.toDouble(), 1.41, s.eqGain(i).toDouble())
            } else {
                equalizer[i].setIdentity()
            }
        }
        configured = s
    }

    private fun applyFilters(buffer: FloatArray, frames: Int, filters: Array<Biquad>) {
        for (f in filters) applyFilter(buffer, frames, f)
    }

    private fun applyFilter(buffer: FloatArray, frames: Int, f: Biquad) {
        if (f.isIdentity) return
        var idx = 0
        for (i in 0 until frames) {
            for (c in 0 until channels) buffer[idx + c] = f.process(buffer[idx + c], c)
            idx += channels
        }
    }

    private fun applyBoostAndBalance(buffer: FloatArray, frames: Int, s: EnhancerSettings) {
        val targetBoost = dbToLinear(s.boostDb.coerceIn(0f, EnhancerSettings.MAX_BOOST_DB))
        val balance = s.balance.coerceIn(-1f, 1f)
        if (targetBoost == 1f && currentBoost == 1f && (balance == 0f || channels != 2)) return
        val leftGain = if (channels == 2) min(1f, 1f - balance) else 1f
        val rightGain = if (channels == 2) min(1f, 1f + balance) else 1f
        // Ramp the boost over the block to avoid zipper noise while dragging the slider.
        val step = (targetBoost - currentBoost) / frames
        var idx = 0
        for (i in 0 until frames) {
            currentBoost += step
            if (channels == 2) {
                buffer[idx] *= currentBoost * leftGain
                buffer[idx + 1] *= currentBoost * rightGain
            } else {
                for (c in 0 until channels) buffer[idx + c] *= currentBoost
            }
            idx += channels
        }
        currentBoost = targetBoost
    }

    /**
     * Copies the latest spectrum (channel 0) into logarithmic bands for the UI.
     * Values are in dBFS-ish units, roughly -100..0.
     */
    fun snapshotSpectrum(bands: Int, input: FloatArray, noise: FloatArray, output: FloatArray): Long {
        val minHz = 50f
        val maxHz = min(16_000f, sampleRate / 2f)
        val norm = (denoiser.fftSize * denoiser.fftSize / 4f)
        for (b in 0 until bands) {
            val lo = minHz * exp(ln(maxHz / minHz) * b / bands)
            val hi = minHz * exp(ln(maxHz / minHz) * (b + 1) / bands)
            val k0 = max(1, (lo / denoiser.binHz).toInt())
            val k1 = max(k0 + 1, min(denoiser.bins, (hi / denoiser.binHz).toInt() + 1))
            var si = 0f
            var sn = 0f
            var so = 0f
            for (k in k0 until k1) {
                si += denoiser.displayInput[k]
                sn += denoiser.displayNoise[k]
                so += denoiser.displayOutput[k]
            }
            val count = (k1 - k0) * norm
            input[b] = powerToDb(si / count)
            noise[b] = powerToDb(sn / count)
            output[b] = powerToDb(so / count)
        }
        return denoiser.displayVersion
    }
}
