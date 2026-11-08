package io.github.hexotronic.lauscher.voice.core

import kotlin.math.log10

/**
 * Cuts short utterances out of the microphone signal, so the speech recogniser only runs when
 * someone speaks. Works on 20 ms frames: a frame counts as speech when it is clearly louder than
 * the background, which is followed slowly (traffic, rustling in a pocket). An utterance starts
 * after a few speech frames, ends after a short pause and keeps a little sound before and after.
 * Anything longer than [maxMs] is a conversation or music rather than a command and is dropped.
 *
 * [sensitivity] 0..1: higher reacts to quieter speech (and to more noise).
 */
class SpeechDetector(
    private val sampleRate: Int = 16_000,
    sensitivity: Float = 0.5f,
    private val maxMs: Int = 5_000,
    private val onStart: () -> Unit = {},
    private val onEnd: (accepted: Boolean) -> Unit = {},
) {
    var sensitivity: Float = sensitivity.coerceIn(0f, 1f)
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    private val frame = sampleRate / 50
    private val pending = FloatArray(frame)
    private var pendingCount = 0

    /** Background level in dBFS, followed slowly while nobody speaks. */
    var noiseFloorDb = -60.0
        private set

    // Recent frames kept for the start of an utterance (its first syllable comes before the trigger).
    private val preRoll = ArrayDeque<FloatArray>()
    private val utterance = ArrayList<FloatArray>()
    private var inUtterance = false
    private var speechRun = 0
    private var silenceRun = 0
    private var speechFrames = 0
    private var dropping = false

    /** Feeds [count] samples (-1..1); returns the utterances that ended in them. */
    fun feed(samples: FloatArray, count: Int = samples.size): List<FloatArray> {
        val done = ArrayList<FloatArray>(1)
        var i = 0
        while (i < count) {
            val n = minOf(frame - pendingCount, count - i)
            System.arraycopy(samples, i, pending, pendingCount, n)
            pendingCount += n
            i += n
            if (pendingCount == frame) {
                pendingCount = 0
                frame(pending.copyOf())?.let { done += it }
            }
        }
        return done
    }

    fun reset() {
        pendingCount = 0
        preRoll.clear()
        utterance.clear()
        if (inUtterance) onEnd(false)
        inUtterance = false
        dropping = false
        speechRun = 0
        silenceRun = 0
        speechFrames = 0
    }

    private fun frame(f: FloatArray): FloatArray? {
        var sum = 0.0
        for (v in f) sum += v * v
        val db = 10 * log10(sum / f.size + 1e-12)
        // 18 dB above the background at the lowest sensitivity, 6 dB at the highest.
        val speech = db > noiseFloorDb + (18 - 12 * sensitivity) && db > ABSOLUTE_MIN_DB - 15 * sensitivity
        if (!inUtterance && !dropping) {
            // The background moves down quickly and up slowly (2.5 dB per second).
            noiseFloorDb = if (db < noiseFloorDb) noiseFloorDb * 0.7 + db * 0.3 else minOf(noiseFloorDb + 0.05, -15.0)
        }
        if (dropping) {
            // Too long for a command: wait for a pause before listening again.
            silenceRun = if (speech) 0 else silenceRun + 1
            if (silenceRun >= END_FRAMES) {
                dropping = false
                silenceRun = 0
            }
            return null
        }
        if (!inUtterance) {
            preRoll.addLast(f)
            if (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
            speechRun = if (speech) speechRun + 1 else 0
            if (speechRun >= START_FRAMES) {
                inUtterance = true
                utterance.clear()
                utterance.addAll(preRoll)
                preRoll.clear()
                speechFrames = speechRun
                silenceRun = 0
                onStart()
            }
            return null
        }
        utterance += f
        if (speech) {
            speechFrames++
            silenceRun = 0
        } else {
            silenceRun++
        }
        if (utterance.size * 20 > maxMs) {
            inUtterance = false
            dropping = true
            silenceRun = 0
            utterance.clear()
            onEnd(false)
            return null
        }
        if (silenceRun >= END_FRAMES) {
            inUtterance = false
            speechRun = 0
            val accepted = speechFrames * 20 >= MIN_SPEECH_MS
            onEnd(accepted)
            val out = if (accepted) join(utterance) else null
            utterance.clear()
            return out
        }
        return null
    }

    private fun join(frames: List<FloatArray>): FloatArray {
        val out = FloatArray(frames.sumOf { it.size })
        var at = 0
        for (f in frames) {
            System.arraycopy(f, 0, out, at, f.size)
            at += f.size
        }
        return out
    }

    private companion object {
        const val START_FRAMES = 3 // 60 ms of speech start an utterance
        const val END_FRAMES = 25 // 500 ms of quiet end it ("Lauscher … Pause" has short gaps)
        const val PRE_ROLL_FRAMES = 15 // 300 ms kept from before the start
        const val MIN_SPEECH_MS = 240
        const val ABSOLUTE_MIN_DB = -45.0
    }
}
