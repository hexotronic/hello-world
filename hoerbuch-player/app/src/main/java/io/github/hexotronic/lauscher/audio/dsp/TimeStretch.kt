package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Speeds up speech without changing its pitch (WSOLA: waveform-similarity overlap-add).
 * Used to hand the speech recognition less audio: at 2× it has half the work, and voices
 * still sound like voices instead of chipmunks.
 */
object TimeStretch {
    /** Returns [input] (mono) played [factor] times faster; factors ≤ 1 return the input unchanged. */
    fun speedUp(input: FloatArray, sampleRate: Int, factor: Float): FloatArray {
        if (factor <= 1.01f || input.size < sampleRate / 10) return input
        val frame = (sampleRate * FRAME_SECONDS).roundToInt() and 1.inv()
        val hopOut = frame / 2
        val hopIn = hopOut * factor
        val tolerance = frame / 4
        val window = FloatArray(frame) { (0.5 - 0.5 * cos(2 * PI * it / frame)).toFloat() }
        val outLength = (input.size / factor).toInt()
        val out = FloatArray(outLength + frame)
        val norm = FloatArray(outLength + frame)

        var outPos = 0
        // Input position whose continuation the next frame should match.
        var prevPos = 0
        var frameIndex = 0
        while (outPos + frame <= out.size) {
            val nominal = (frameIndex * hopIn).toInt()
            if (nominal + frame + tolerance >= input.size) break
            val chosen = if (frameIndex == 0) {
                0
            } else {
                // The natural continuation of the previous frame, matched within ± tolerance.
                val target = prevPos + hopOut
                var best = nominal
                var bestScore = Float.NEGATIVE_INFINITY
                val from = maxOf(0, nominal - tolerance)
                val to = minOf(input.size - frame, nominal + tolerance)
                var cand = from
                while (cand <= to) {
                    var score = 0f
                    var i = 0
                    while (i < hopOut) {
                        score += input[cand + i] * input[(target + i).coerceAtMost(input.size - 1)]
                        i += 2
                    }
                    if (score > bestScore) {
                        bestScore = score
                        best = cand
                    }
                    cand += 2
                }
                best
            }
            for (i in 0 until frame) {
                out[outPos + i] += input[chosen + i] * window[i]
                norm[outPos + i] += window[i]
            }
            prevPos = chosen
            outPos += hopOut
            frameIndex++
        }
        val result = FloatArray(minOf(outLength, outPos + hopOut))
        for (i in result.indices) result[i] = if (norm[i] > 1e-3f) out[i] / norm[i] else 0f
        return result
    }

    private const val FRAME_SECONDS = 0.03
}
