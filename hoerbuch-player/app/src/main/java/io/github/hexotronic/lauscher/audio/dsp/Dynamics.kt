package io.github.hexotronic.lauscher.audio.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

internal fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

internal fun powerToDb(power: Float): Float = 10f * log10(max(power, 1e-12f))

/** One-pole smoothing coefficient for a time constant in milliseconds. */
internal fun timeCoefficient(sampleRate: Int, ms: Float): Float =
    exp(-1.0 / (sampleRate * ms / 1000.0)).toFloat()

/**
 * Downward expander that cleans up breathing pauses. The threshold follows the recording:
 * it sits between the tracked noise floor and the tracked speech level.
 */
class NoiseGate(private val sampleRate: Int, private val channels: Int) {
    private val levelCoef = timeCoefficient(sampleRate, 10f)
    private val openCoef = timeCoefficient(sampleRate, 2f)
    private val closeCoef = timeCoefficient(sampleRate, 70f)
    private val holdSamples = sampleRate * 80 / 1000
    private val floorRisePerSample = 3f / sampleRate
    private val speechFallPerSample = 1.5f / sampleRate

    private var level = 0f
    private var floorDb = -60f
    private var speechDb = -20f
    private var gain = 1f
    private var hold = 0

    fun reset() {
        level = 0f
        gain = 1f
        hold = 0
    }

    fun process(buffer: FloatArray, frames: Int, strength: Float) {
        val maxAttenuationDb = 10f + 20f * strength
        val position = 0.2f + 0.25f * strength
        var idx = 0
        for (i in 0 until frames) {
            var p = 0f
            for (c in 0 until channels) {
                val x = buffer[idx + c]
                p = max(p, x * x)
            }
            level = levelCoef * level + (1 - levelCoef) * p
            val levelDb = powerToDb(level)
            floorDb = if (levelDb < floorDb) levelDb else floorDb + floorRisePerSample
            speechDb = if (levelDb > speechDb) levelDb else speechDb - speechFallPerSample
            if (speechDb < floorDb + 6f) speechDb = floorDb + 6f
            val threshold = floorDb + (speechDb - floorDb) * position

            val target: Float
            if (levelDb >= threshold) {
                hold = holdSamples
                target = 1f
            } else if (hold > 0) {
                hold--
                target = 1f
            } else {
                val attenuation = min(maxAttenuationDb, (threshold - levelDb) * 2f)
                target = dbToLinear(-attenuation)
            }
            gain = if (target > gain) {
                openCoef * gain + (1 - openCoef) * target
            } else {
                closeCoef * gain + (1 - closeCoef) * target
            }
            for (c in 0 until channels) buffer[idx + c] *= gain
            idx += channels
        }
    }
}

/** Splits off the sibilance band and turns it down while "s" and "sch" sounds dominate. */
class DeEsser(private val sampleRate: Int, private val channels: Int) {
    private val split = Biquad(channels)
    private val high = FloatArray(channels)
    private val envCoef = timeCoefficient(sampleRate, 4f)
    private val attackCoef = timeCoefficient(sampleRate, 1f)
    private val releaseCoef = timeCoefficient(sampleRate, 40f)
    private var envHigh = 0f
    private var envFull = 0f
    private var gain = 1f
    val isSupported = sampleRate >= 16_000

    init {
        if (isSupported) split.highPass(sampleRate, min(5500.0, sampleRate * 0.3), 0.707)
    }

    fun reset() {
        split.reset()
        envHigh = 0f
        envFull = 0f
        gain = 1f
    }

    fun process(buffer: FloatArray, frames: Int, strength: Float) {
        if (!isSupported) return
        val maxReductionDb = 4f + 10f * strength
        var idx = 0
        for (i in 0 until frames) {
            var ph = 0f
            var pf = 0f
            for (c in 0 until channels) {
                val x = buffer[idx + c]
                val h = split.process(x, c)
                high[c] = h
                ph += h * h
                pf += x * x
            }
            envHigh = envCoef * envHigh + (1 - envCoef) * ph
            envFull = envCoef * envFull + (1 - envCoef) * pf
            val ratioDb = powerToDb(envHigh) - powerToDb(envFull)
            val target = if (powerToDb(envFull) > -50f && ratioDb > THRESHOLD_DB) {
                dbToLinear(-min(maxReductionDb, (ratioDb - THRESHOLD_DB) * 2.5f))
            } else {
                1f
            }
            gain = if (target < gain) {
                attackCoef * gain + (1 - attackCoef) * target
            } else {
                releaseCoef * gain + (1 - releaseCoef) * target
            }
            val cut = 1f - gain
            for (c in 0 until channels) buffer[idx + c] -= cut * high[c]
            idx += channels
        }
    }

    private companion object {
        const val THRESHOLD_DB = -9f
    }
}

/** Feed-forward compressor with soft knee and linked channels ("Nachtmodus"). */
class Compressor(private val sampleRate: Int, private val channels: Int) {
    private val detectorCoef = timeCoefficient(sampleRate, 8f)
    private val attackCoef = timeCoefficient(sampleRate, 6f)
    private val releaseCoef = timeCoefficient(sampleRate, 220f)
    private var detector = 0f
    private var reductionDb = 0f

    /** Current gain reduction for the UI meter. */
    val currentReductionDb get() = reductionDb

    fun reset() {
        detector = 0f
        reductionDb = 0f
    }

    fun process(buffer: FloatArray, frames: Int, strength: Float) {
        val threshold = -12f - 18f * strength
        val ratio = 1.5f + 4.5f * strength
        val knee = 8f
        val makeup = -threshold * (1f - 1f / ratio) * 0.55f
        var idx = 0
        for (i in 0 until frames) {
            var p = 0f
            for (c in 0 until channels) {
                val x = buffer[idx + c]
                p = max(p, x * x)
            }
            detector = detectorCoef * detector + (1 - detectorCoef) * p
            val over = powerToDb(detector) - threshold
            val target = when {
                over <= -knee / 2 -> 0f
                over < knee / 2 -> (1f / ratio - 1f) * (over + knee / 2).let { it * it } / (2 * knee)
                else -> (1f / ratio - 1f) * over
            }
            reductionDb = if (target < reductionDb) {
                attackCoef * reductionDb + (1 - attackCoef) * target
            } else {
                releaseCoef * reductionDb + (1 - releaseCoef) * target
            }
            val g = dbToLinear(reductionDb + makeup)
            for (c in 0 until channels) buffer[idx + c] *= g
            idx += channels
        }
    }
}

/** Slowly levels out volume differences between chapters, files and speakers. */
class AutoLevel(private val sampleRate: Int, private val channels: Int) {
    private val measureCoef = timeCoefficient(sampleRate, 2500f)
    private val maxStepDb = 1.5f / sampleRate
    private var meanPower = dbToLinear(TARGET_DB).let { it * it }
    private var gainDb = 0f

    val currentGainDb get() = gainDb

    fun reset() {
        meanPower = dbToLinear(TARGET_DB).let { it * it }
        gainDb = 0f
    }

    fun process(buffer: FloatArray, frames: Int) {
        var idx = 0
        for (i in 0 until frames) {
            var p = 0f
            for (c in 0 until channels) {
                val x = buffer[idx + c]
                p += x * x
            }
            p /= channels
            // Only loud enough material updates the measurement, pauses are ignored.
            if (p > GATE_POWER) meanPower = measureCoef * meanPower + (1 - measureCoef) * p
            val target = (TARGET_DB - powerToDb(meanPower)).coerceIn(-12f, 15f)
            gainDb += (target - gainDb).coerceIn(-maxStepDb, maxStepDb)
            val g = dbToLinear(gainDb)
            for (c in 0 until channels) buffer[idx + c] *= g
            idx += channels
        }
    }

    private companion object {
        const val TARGET_DB = -20f
        val GATE_POWER = dbToLinear(-45f).let { it * it }
    }
}

/** Peak limiter with instant attack plus a soft clipper as last line of defence. */
class Limiter(private val sampleRate: Int, private val channels: Int) {
    private val releaseCoef = timeCoefficient(sampleRate, 80f)
    private var gain = 1f

    fun reset() {
        gain = 1f
    }

    fun process(buffer: FloatArray, frames: Int) {
        var idx = 0
        for (i in 0 until frames) {
            var peak = 0f
            for (c in 0 until channels) peak = max(peak, abs(buffer[idx + c]))
            val target = if (peak > CEILING) CEILING / peak else 1f
            gain = if (target < gain) target else releaseCoef * gain + (1 - releaseCoef) * target
            for (c in 0 until channels) buffer[idx + c] = softClip(buffer[idx + c] * gain)
            idx += channels
        }
    }

    private fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val over = a - KNEE
        val room = 1f - KNEE
        val y = KNEE + room * (over / (over + room))
        return if (x < 0) -y else y
    }

    private companion object {
        const val CEILING = 0.89f
        const val KNEE = 0.9f
    }
}

/** Adaptive slew-rate limiter that flattens clicks and crackle from digitised tapes and vinyl. */
class Declicker(sampleRate: Int, private val channels: Int) {
    private val meanCoef = timeCoefficient(sampleRate, 25f)
    private val previousIn = FloatArray(channels)
    private val previousOut = FloatArray(channels)
    private val meanSlope = FloatArray(channels)

    fun reset() {
        previousIn.fill(0f)
        previousOut.fill(0f)
        meanSlope.fill(0f)
    }

    fun process(buffer: FloatArray, frames: Int, strength: Float) {
        val k = 14f - 10f * strength
        var idx = 0
        for (i in 0 until frames) {
            for (c in 0 until channels) {
                val x = buffer[idx + c]
                val slope = abs(x - previousIn[c])
                previousIn[c] = x
                val maxSlope = k * meanSlope[c] + 0.004f
                // Only "normal" slopes teach the detector, otherwise a click would raise the bar.
                meanSlope[c] = meanCoef * meanSlope[c] + (1 - meanCoef) * min(slope, maxSlope)
                val d = x - previousOut[c]
                val y = when {
                    d > maxSlope -> previousOut[c] + maxSlope
                    d < -maxSlope -> previousOut[c] - maxSlope
                    else -> x
                }
                previousOut[c] = y
                buffer[idx + c] = y
            }
            idx += channels
        }
    }
}
