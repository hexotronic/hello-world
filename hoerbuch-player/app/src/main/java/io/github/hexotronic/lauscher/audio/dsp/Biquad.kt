package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Second order IIR filter (RBJ audio EQ cookbook) with independent state per channel.
 * Uses double precision state so very low cut-off frequencies stay stable.
 */
class Biquad(private val channels: Int) {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val z1 = DoubleArray(channels)
    private val z2 = DoubleArray(channels)

    /** True when the filter is configured as a pass-through and can be skipped. */
    var isIdentity = true
        private set

    fun reset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    fun setIdentity() {
        b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
        isIdentity = true
    }

    fun lowPass(sampleRate: Int, freq: Double, q: Double) {
        val w0 = omega(sampleRate, freq)
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    fun highPass(sampleRate: Int, freq: Double, q: Double) {
        val w0 = omega(sampleRate, freq)
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
    }

    fun peaking(sampleRate: Int, freq: Double, q: Double, gainDb: Double) {
        if (gainDb == 0.0) {
            setIdentity(); return
        }
        val a = 10.0.pow(gainDb / 40)
        val w0 = omega(sampleRate, freq)
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q)
        set(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
    }

    fun lowShelf(sampleRate: Int, freq: Double, gainDb: Double) {
        if (gainDb == 0.0) {
            setIdentity(); return
        }
        val a = 10.0.pow(gainDb / 40)
        val w0 = omega(sampleRate, freq)
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) - (a - 1) * cw + sa),
            2 * a * ((a - 1) - (a + 1) * cw),
            a * ((a + 1) - (a - 1) * cw - sa),
            (a + 1) + (a - 1) * cw + sa,
            -2 * ((a - 1) + (a + 1) * cw),
            (a + 1) + (a - 1) * cw - sa,
        )
    }

    fun highShelf(sampleRate: Int, freq: Double, gainDb: Double) {
        if (gainDb == 0.0) {
            setIdentity(); return
        }
        val a = 10.0.pow(gainDb / 40)
        val w0 = omega(sampleRate, freq)
        val cw = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        set(
            a * ((a + 1) + (a - 1) * cw + sa),
            -2 * a * ((a - 1) + (a + 1) * cw),
            a * ((a + 1) + (a - 1) * cw - sa),
            (a + 1) - (a - 1) * cw + sa,
            2 * ((a - 1) - (a + 1) * cw),
            (a + 1) - (a - 1) * cw - sa,
        )
    }

    fun process(x: Float, channel: Int): Float {
        val y = b0 * x + z1[channel]
        z1[channel] = b1 * x - a1 * y + z2[channel]
        z2[channel] = b2 * x - a2 * y
        return y.toFloat()
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0
        a1 = na1 / a0; a2 = na2 / a0
        isIdentity = false
    }

    private fun omega(sampleRate: Int, freq: Double): Double {
        val nyquistSafe = freq.coerceIn(1.0, sampleRate * 0.49)
        return 2 * PI * nyquistSafe / sampleRate
    }

    companion object {
        /** Q values of the two sections of a 4th order Butterworth filter. */
        val BUTTERWORTH_4_Q = doubleArrayOf(0.5411961, 1.3065630)
    }
}
