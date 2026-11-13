package io.github.hexotronic.lauscher.music.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

internal fun midiToHz(note: Float): Float = 440f * 2f.pow((note - 69f) / 12f)

/** Fast deterministic random numbers (xorshift), so every scene sounds the same on replay. */
internal class Rng(seed: Long) {
    private var state = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        state = x
        return x
    }

    /** 0 until 1 */
    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())

    /** -1 until 1 */
    fun bipolar(): Float = nextFloat() * 2f - 1f

    fun nextInt(bound: Int): Int = ((nextLong() ushr 33) % bound).toInt()

    fun chance(p: Float): Boolean = nextFloat() < p
}

/** Wavetables shared by all voices. */
internal object Tables {
    const val SIZE = 4096
    const val MASK = SIZE - 1
    val sine = FloatArray(SIZE) { sin(2 * PI * it / SIZE).toFloat() }

    /** Soft "string pad" waveform: a few decaying harmonics. */
    val pad = FloatArray(SIZE).also { t ->
        val amps = floatArrayOf(1f, 0.45f, 0.28f, 0.16f, 0.09f, 0.05f)
        var max = 0f
        for (i in 0 until SIZE) {
            var v = 0f
            for (h in amps.indices) v += amps[h] * sin(2 * PI * (h + 1) * i / SIZE).toFloat()
            t[i] = v
            if (kotlin.math.abs(v) > max) max = kotlin.math.abs(v)
        }
        for (i in 0 until SIZE) t[i] /= max
    }

    fun lookup(table: FloatArray, phase: Float): Float {
        val pos = phase * SIZE
        val i = pos.toInt()
        val frac = pos - i
        val a = table[i and MASK]
        val b = table[(i + 1) and MASK]
        return a + (b - a) * frac
    }
}

/** One-pole low-pass, cheap and click free when the cut-off moves. */
internal class OnePole(private val sampleRate: Int, cutoff: Float) {
    private var a = 0f
    private var z = 0f

    init {
        setCutoff(cutoff)
    }

    fun setCutoff(hz: Float) {
        a = exp(-2.0 * PI * hz.coerceIn(10f, sampleRate * 0.45f) / sampleRate).toFloat()
    }

    fun lowPass(x: Float): Float {
        z = x * (1 - a) + z * a
        return z
    }

    fun highPass(x: Float): Float = x - lowPass(x)
}

/** State-variable filter (Chamberlin/TPT) used for band-passed noise like wind or droplets. */
internal class Svf(private val sampleRate: Int) {
    private var g = 0f
    private var k = 1f
    private var ic1 = 0f
    private var ic2 = 0f

    fun set(cutoff: Float, q: Float) {
        g = tan(PI * cutoff.coerceIn(20f, sampleRate * 0.45f) / sampleRate).toFloat()
        k = 1f / q
    }

    fun bandPass(x: Float): Float {
        val a1 = 1f / (1f + g * (g + k))
        val v1 = a1 * ic1 + g * a1 * (x - ic2)
        val v2 = ic2 + g * v1
        ic1 = 2 * v1 - ic1
        ic2 = 2 * v2 - ic2
        return v1
    }
}

/** Attack / release envelope with linear segments. */
internal class Envelope(private val sampleRate: Int, attackSec: Float, releaseSec: Float) {
    private val attackStep = 1f / (attackSec * sampleRate).coerceAtLeast(1f)
    private val releaseStep = 1f / (releaseSec * sampleRate).coerceAtLeast(1f)
    var level = 0f
        private set
    private var releasing = false
    val finished get() = releasing && level <= 0f

    fun release() {
        releasing = true
    }

    fun next(): Float {
        if (releasing) {
            level -= releaseStep
            if (level < 0f) level = 0f
        } else if (level < 1f) {
            level += attackStep
            if (level > 1f) level = 1f
        }
        return level
    }
}

/** Sustained chord voice: two slightly detuned oscillators spread across the stereo field. */
internal class PadVoice(sampleRate: Int, note: Float, private val level: Float, attack: Float, release: Float) {
    private val sr = sampleRate.toFloat()
    private val inc1 = midiToHz(note - 0.05f) / sr
    private val inc2 = midiToHz(note + 0.05f) / sr
    private var p1 = 0f
    private var p2 = 0.37f
    private val env = Envelope(sampleRate, attack, release)
    val finished get() = env.finished

    fun release() = env.release()

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val e = env.next() * level
            val a = Tables.lookup(Tables.pad, p1)
            val b = Tables.lookup(Tables.pad, p2)
            p1 += inc1
            if (p1 >= 1f) p1 -= 1f
            p2 += inc2
            if (p2 >= 1f) p2 -= 1f
            left[i] += (0.7f * a + 0.3f * b) * e
            right[i] += (0.3f * a + 0.7f * b) * e
        }
    }
}

/** Plucked string (Karplus-Strong) for harp, guitar and ostinato figures. */
internal class PluckVoice(
    sampleRate: Int,
    note: Float,
    private val level: Float,
    private val pan: Float,
    brightness: Float,
    private val decay: Float,
    rng: Rng,
) {
    private val buffer = FloatArray((sampleRate / midiToHz(note)).toInt().coerceIn(2, 4000))
    private var index = 0
    private var last = 0f
    private val damping = 0.5f - 0.35f * (1f - brightness)
    private var age = 0
    private val maxAge = sampleRate * 6
    val finished get() = age > maxAge

    init {
        var lp = 0f
        for (i in buffer.indices) {
            lp = lp + (rng.bipolar() - lp) * (0.25f + 0.7f * brightness)
            buffer[i] = lp
        }
    }

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        val gl = level * (1f - pan) * 1.4f
        val gr = level * (1f + pan) * 1.4f
        for (i in 0 until frames) {
            val x = buffer[index]
            val next = buffer[(index + 1) % buffer.size]
            val y = decay * ((0.5f + damping) * x + (0.5f - damping) * next)
            buffer[index] = y
            index = (index + 1) % buffer.size
            last = x
            left[i] += x * gl
            right[i] += x * gr
        }
        age += frames
    }
}

/** FM bell / electric piano tone. */
internal class BellVoice(
    sampleRate: Int,
    note: Float,
    private val level: Float,
    private val pan: Float,
    ratio: Float,
    private val index: Float,
    decaySec: Float,
) {
    private val sr = sampleRate.toFloat()
    private val carrierInc = midiToHz(note) / sr
    private val modInc = carrierInc * ratio
    private var pc = 0f
    private var pm = 0f
    private var amp = 1f
    private val ampDecay = exp(-1.0 / (decaySec * sampleRate)).toFloat()
    private val indexDecay = exp(-1.0 / (decaySec * 0.35f * sampleRate)).toFloat()
    private var idx = 1f
    private var attack = 0f
    private val attackStep = 1f / (0.004f * sampleRate)
    val finished get() = amp < 0.0005f

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val mod = Tables.lookup(Tables.sine, pm) * index * idx
            var phase = pc + mod / (2 * PI.toFloat())
            phase -= kotlin.math.floor(phase)
            if (attack < 1f) attack += attackStep
            val v = Tables.lookup(Tables.sine, phase) * amp * level * attack.coerceAtMost(1f)
            pc += carrierInc
            if (pc >= 1f) pc -= 1f
            pm += modInc
            if (pm >= 1f) pm -= 1f
            amp *= ampDecay
            idx *= indexDecay
            left[i] += v * (1f - pan)
            right[i] += v * (1f + pan)
        }
    }
}

/** Soft drum: a sine with falling pitch (heartbeat, kick, low tom). */
internal class ThumpVoice(sampleRate: Int, private val startHz: Float, private val endHz: Float, decaySec: Float, private val level: Float) {
    private val sr = sampleRate.toFloat()
    private var phase = 0f
    private var freq = startHz
    private val pitchCoef = exp(-1.0 / (0.04 * sampleRate)).toFloat()
    private var amp = 1f
    private val ampDecay = exp(-1.0 / (decaySec * sampleRate)).toFloat()
    val finished get() = amp < 0.0005f

    fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            freq = endHz + (freq - endHz) * pitchCoef
            phase += freq / sr
            if (phase >= 1f) phase -= 1f
            val v = Tables.lookup(Tables.sine, phase) * amp * level
            amp *= ampDecay
            left[i] += v
            right[i] += v
        }
    }
}

/** Freeverb-style stereo reverb (4 combs + 2 all-passes per side). */
internal class Reverb(sampleRate: Int) {
    private val scale = sampleRate / 44_100f
    private val combL = intArrayOf(1116, 1188, 1277, 1356).map { Comb((it * scale).toInt()) }
    private val combR = intArrayOf(1139, 1211, 1300, 1379).map { Comb((it * scale).toInt()) }
    private val allL = intArrayOf(556, 441).map { AllPass((it * scale).toInt()) }
    private val allR = intArrayOf(579, 464).map { AllPass((it * scale).toInt()) }
    var feedback = 0.84f
    var damp = 0.3f

    private inner class Comb(size: Int) {
        val buf = FloatArray(size.coerceAtLeast(1))
        var i = 0
        var store = 0f
        fun process(x: Float): Float {
            val out = buf[i]
            store = out * (1 - damp) + store * damp
            buf[i] = x + store * feedback
            if (++i >= buf.size) i = 0
            return out
        }
    }

    private class AllPass(size: Int) {
        val buf = FloatArray(size.coerceAtLeast(1))
        var i = 0
        fun process(x: Float): Float {
            val b = buf[i]
            val out = -x + b
            buf[i] = x + b * 0.5f
            if (++i >= buf.size) i = 0
            return out
        }
    }

    /** Adds [wet] reverb of the input to the buffers in place. */
    fun process(left: FloatArray, right: FloatArray, frames: Int, wet: Float) {
        if (wet <= 0f) return
        for (n in 0 until frames) {
            val input = (left[n] + right[n]) * 0.08f
            var l = 0f
            var r = 0f
            for (c in combL) l += c.process(input)
            for (c in combR) r += c.process(input)
            for (a in allL) l = a.process(l)
            for (a in allR) r = a.process(r)
            left[n] += l * wet
            right[n] += r * wet
        }
    }
}
