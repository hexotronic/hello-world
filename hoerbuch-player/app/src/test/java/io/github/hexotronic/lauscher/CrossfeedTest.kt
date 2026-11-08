package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.audio.dsp.Crossfeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class CrossfeedTest {
    private val rate = 48_000

    /** RMS of each channel after crossfeed, for a tone at [hz] on the left only (or on both). */
    private fun run(hz: Double, both: Boolean, strength: Float): Pair<Double, Double> {
        val frames = rate
        val buf = FloatArray(frames * 2) { i ->
            val v = (0.5 * sin(2 * PI * hz * (i / 2) / rate)).toFloat()
            if (i % 2 == 0 || both) v else 0f
        }
        // Process in blocks the way the player does.
        val out = buf.copyOf()
        val f = Crossfeed(rate)
        var at = 0
        while (at < frames) {
            val n = minOf(480, frames - at)
            val block = out.copyOfRange(at * 2, (at + n) * 2)
            f.process(block, n, strength)
            System.arraycopy(block, 0, out, at * 2, n * 2)
            at += n
        }
        fun rms(ch: Int): Double {
            var s = 0.0
            for (i in rate / 2 until frames) s += out[i * 2 + ch] * out[i * 2 + ch]
            return sqrt(s / (frames - rate / 2))
        }
        return rms(0) to rms(1)
    }

    @Test
    fun lowSoundsReachTheOtherEarHighOnesHardly() {
        val (left, right) = run(200.0, both = false, strength = 1f)
        // A deep voice on the left is heard on the right about 9 dB lower …
        assertTrue("right $right left $left", right / left in 0.25..0.4)
        // … a hiss on the left stays almost only on the left.
        val (hl, hr) = run(8_000.0, both = false, strength = 1f)
        assertTrue("right $hr left $hl", hr / hl < 0.05)
    }

    @Test
    fun centredSoundKeepsItsLevelAndOffChangesNothing() {
        val (l, r) = run(200.0, both = true, strength = 1f)
        val reference = 0.5 / sqrt(2.0)
        assertEquals(reference, l, reference * 0.06)
        assertEquals(reference, r, reference * 0.06)
        val (ol, or) = run(200.0, both = false, strength = 0f)
        assertEquals(reference, ol, 1e-6)
        assertEquals(0.0, or, 1e-9)
    }
}
