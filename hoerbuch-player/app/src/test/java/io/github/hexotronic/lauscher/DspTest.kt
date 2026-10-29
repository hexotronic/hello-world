package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.audio.dsp.EnhancerEngine
import io.github.hexotronic.lauscher.audio.dsp.EnhancerPreset
import io.github.hexotronic.lauscher.audio.dsp.EnhancerSettings
import io.github.hexotronic.lauscher.audio.dsp.Fft
import io.github.hexotronic.lauscher.audio.dsp.SpectralDenoiser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.random.Random

class DspTest {
    private val rate = 44_100

    @Test
    fun fftRoundTrip() {
        val fft = Fft(512)
        val rnd = Random(1)
        val re = FloatArray(512) { rnd.nextFloat() - 0.5f }
        val im = FloatArray(512)
        val original = re.copyOf()
        fft.forward(re, im)
        fft.inverse(re, im)
        for (i in original.indices) assertEquals(original[i], re[i], 1e-5f)
    }

    @Test
    fun fftFindsSine() {
        val fft = Fft(1024)
        val re = FloatArray(1024) { sin(2 * PI * 32 * it / 1024).toFloat() }
        val im = FloatArray(1024)
        fft.forward(re, im)
        val mags = FloatArray(513) { re[it] * re[it] + im[it] * im[it] }
        assertEquals(32, mags.indices.maxByOrNull { mags[it] })
    }

    @Test
    fun denoiserIsTransparentAtZeroStrength() {
        val dn = SpectralDenoiser(rate, 2)
        val rnd = Random(7)
        val input = FloatArray(rate * 2) { rnd.nextFloat() - 0.5f }
        val buffer = input.copyOf()
        // Odd block sizes on purpose.
        var pos = 0
        while (pos < buffer.size / 2) {
            val frames = minOf(333, buffer.size / 2 - pos)
            val block = buffer.copyOfRange(pos * 2, (pos + frames) * 2)
            dn.process(block, frames, 0f, false)
            block.copyInto(buffer, pos * 2)
            pos += frames
        }
        val latency = dn.latency
        var maxError = 0f
        for (i in dn.fftSize until input.size / 2) {
            for (c in 0..1) {
                val e = abs(buffer[i * 2 + c] - input[(i - latency) * 2 + c])
                if (e > maxError) maxError = e
            }
        }
        assertTrue("reconstruction error $maxError", maxError < 1e-4f)
    }

    @Test
    fun denoiserReducesNoiseAndKeepsSpeech() {
        val seconds = 8
        val clean = syntheticSpeech(seconds)
        val rnd = Random(3)
        val noiseAmp = 0.05f
        val noise = FloatArray(clean.size) { (rnd.nextFloat() * 2 - 1) * noiseAmp }
        val noisy = FloatArray(clean.size) { clean[it] + noise[it] }

        val dn = SpectralDenoiser(rate, 1)
        val out = noisy.copyOf()
        processInBlocks(out) { block, frames -> dn.process(block, frames, 0.8f, false) }
        val lat = dn.latency

        // Compare the last 5 seconds (after adaptation).
        val from = rate * 3
        var cleanPow = 0.0
        var errIn = 0.0
        var errOut = 0.0
        var pauseIn = 0.0
        var pauseOut = 0.0
        for (i in from until clean.size - lat) {
            val c = clean[i].toDouble()
            cleanPow += c * c
            errIn += (noisy[i] - c) * (noisy[i] - c)
            val y = out[i + lat].toDouble()
            errOut += (y - c) * (y - c)
            if (abs(c) < 1e-6) {
                pauseIn += noisy[i] * noisy[i].toDouble()
                pauseOut += y * y
            }
        }
        val snrIn = 10 * log10(cleanPow / errIn)
        val snrOut = 10 * log10(cleanPow / errOut)
        val pauseReduction = 10 * log10(pauseIn / pauseOut)
        println("SNR in %.1f dB, out %.1f dB, noise in pauses -%.1f dB".format(snrIn, snrOut, pauseReduction))
        assertTrue("SNR should improve by 5 dB (in $snrIn, out $snrOut)", snrOut > snrIn + 5)
        assertTrue("pauses should be at least 15 dB quieter ($pauseReduction)", pauseReduction > 15)
    }

    @Test
    fun noiseEstimateIsCalibrated() {
        val dn = SpectralDenoiser(rate, 1)
        val engine = EnhancerEngine(rate, 1)
        val rnd = Random(11)
        val amp = 0.1f
        val buffer = FloatArray(rate * 4) { (rnd.nextFloat() * 2 - 1) * amp }
        processInBlocks(buffer) { block, frames -> engine.process(block, frames, EnhancerSettings(), false) }
        val bands = 24
        val input = FloatArray(bands)
        val noise = FloatArray(bands)
        val output = FloatArray(bands)
        engine.snapshotSpectrum(bands, input, noise, output)
        // White noise: the estimate should be within a few dB of the measured spectrum.
        val diff = (0 until bands).map { noise[it] - input[it] }.average()
        println("noise estimate vs input: %.2f dB".format(diff))
        assertTrue("noise estimate off by $diff dB", diff > -4 && diff < 5)
        assertTrue(dn.latency > 0)
    }

    @Test
    fun fullChainStaysBoundedAndFinite() {
        val clean = syntheticSpeech(4)
        val rnd = Random(5)
        val loud = FloatArray(clean.size * 2) { clean[it / 2] * 3f + (rnd.nextFloat() - 0.5f) * 0.2f }
        val everything = EnhancerSettings(
            noiseReduction = 1f, rumbleFilterHz = 120, hissFilterHz = 8000, declick = 1f, noiseGate = 1f,
            deEsser = 1f, voiceClarity = 1f, equalizerEnabled = true,
            equalizerGains = List(10) { if (it % 2 == 0) 12f else -12f },
            compressor = 1f, autoLevel = true, boostDb = 15f, mono = true, balance = 0.5f,
        )
        for (settings in EnhancerPreset.entries.map { it.settings } + everything) {
            val engine = EnhancerEngine(rate, 2)
            val buffer = loud.copyOf()
            var pos = 0
            while (pos < buffer.size / 2) {
                val frames = minOf(1024, buffer.size / 2 - pos)
                val block = buffer.copyOfRange(pos * 2, (pos + frames) * 2)
                engine.process(block, frames, settings, false)
                block.copyInto(buffer, pos * 2)
                pos += frames
            }
            for (v in buffer) {
                assertTrue("not finite", v.isFinite())
                assertTrue("out of range: $v", abs(v) <= 1f)
            }
        }
    }

    private fun processInBlocks(buffer: FloatArray, block: (FloatArray, Int) -> Unit) {
        var pos = 0
        while (pos < buffer.size) {
            val frames = minOf(777, buffer.size - pos)
            val b = buffer.copyOfRange(pos, pos + frames)
            block(b, frames)
            b.copyInto(buffer, pos)
            pos += frames
        }
    }

    /** Harmonic "voice" with syllables and pauses; exact zeros in the pauses. */
    private fun syntheticSpeech(seconds: Int): FloatArray {
        val out = FloatArray(rate * seconds)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / rate
            val inPhrase = (t % 2.5) < 1.8
            val syllable = (t % 0.3) < 0.2
            if (!inPhrase || !syllable) continue
            val env = sin(PI * (t % 0.3) / 0.2)
            val f0 = 130 + 25 * sin(2 * PI * 0.7 * t)
            phase += 2 * PI * f0 / rate
            var s = 0.0
            for (h in 1..25) {
                val f = f0 * h
                if (f > rate / 2.2) break
                val formant = 1.0 / (1 + ((f - 700) / 300).let { it * it }) +
                    0.6 / (1 + ((f - 1800) / 400).let { it * it }) +
                    0.3 / (1 + ((f - 2800) / 500).let { it * it })
                s += formant * sin(h * phase) / h
            }
            out[i] = (0.3 * env * s).toFloat()
        }
        return out
    }
}
