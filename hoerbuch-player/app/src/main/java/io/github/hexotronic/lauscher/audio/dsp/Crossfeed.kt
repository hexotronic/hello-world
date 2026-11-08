package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.PI
import kotlin.math.exp

/**
 * Headphone crossfeed: each ear also gets a little of the other channel, low-passed and delayed
 * by about 0.3 ms, as with loudspeakers in a room. Recordings with voices or effects hard on one
 * side then sound less tiring over headphones. Centred sound (the same on both channels) keeps
 * its level; only interleaved stereo with two channels is changed.
 */
class Crossfeed(sampleRate: Int) {
    private val delay = maxOf(1, (sampleRate * 0.0003).toInt())
    private val lowPass = exp(-2 * PI * 700.0 / sampleRate).toFloat()
    private val historyL = FloatArray(delay)
    private val historyR = FloatArray(delay)
    private var at = 0
    private var lpL = 0f
    private var lpR = 0f

    /** [strength] 0..1: at 1 the other channel is mixed in at about −9 dB (below 700 Hz). */
    fun process(buffer: FloatArray, frames: Int, strength: Float) {
        if (strength <= 0f) return
        val g = 0.35f * strength.coerceAtMost(1f)
        val norm = 1f / (1f + g)
        var idx = 0
        for (i in 0 until frames) {
            val l = buffer[idx]
            val r = buffer[idx + 1]
            // Low-pass of each channel, then delayed before it reaches the other ear.
            lpL = l + lowPass * (lpL - l)
            lpR = r + lowPass * (lpR - r)
            val fromL = historyL[at]
            val fromR = historyR[at]
            historyL[at] = lpL
            historyR[at] = lpR
            at = (at + 1) % delay
            buffer[idx] = (l + g * fromR) * norm
            buffer[idx + 1] = (r + g * fromL) * norm
            idx += 2
        }
    }
}
