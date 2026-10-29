package io.github.hexotronic.lauscher.music.synth

import kotlin.math.PI
import kotlin.math.sin

/** Something that adds stereo audio into the given buffers. */
internal interface Generator {
    /** Reverb amount wanted by this layer. */
    val reverb: Float
    fun render(left: FloatArray, right: FloatArray, frames: Int)

    /** Frees resources (decoder threads) once the layer has faded out. */
    fun close() {}
}

/** Noise based soundscapes: rain, fire, forest, sea, summer night, storm. */
internal class AtmosphereGenerator(
    private val sampleRate: Int,
    private val kind: Atmosphere,
    private val level: Float,
    seed: Long,
) : Generator {
    override val reverb = when (kind) {
        Atmosphere.FOREST, Atmosphere.NIGHT -> 0.2f
        else -> 0.08f
    }
    private val rng = Rng(seed * 31 + kind.ordinal)
    private val sr = sampleRate.toFloat()
    private val lpL = OnePole(sampleRate, 800f)
    private val lpR = OnePole(sampleRate, 800f)
    private val hp = OnePole(sampleRate, 2500f)
    private val hpR = OnePole(sampleRate, 2500f)
    private val bandL = Svf(sampleRate)
    private val bandR = Svf(sampleRate)
    private val dropFilter = Svf(sampleRate)
    private var brownL = 0f
    private var brownR = 0f
    private var t = 0.0
    private var event = 0f
    private var eventDecay = 0.99f
    private var eventPan = 0f
    private var chirpPhase = 0f
    private var chirpFreq = 0f
    private var chirpTarget = 0f
    private var chirpAmp = 0f
    private var chirpsLeft = 0
    private var nextChirp = 1.0
    private var cricketPhase = 0f

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            t += 1.0 / sr
            val wl = rng.bipolar()
            val wr = rng.bipolar()
            brownL = (brownL + wl * 0.02f) * 0.998f
            brownR = (brownR + wr * 0.02f) * 0.998f
            var l = 0f
            var r = 0f
            when (kind) {
                Atmosphere.RAIN -> {
                    // Steady hiss plus random droplets.
                    l += hp.highPass(wl) * 0.08f + lpL.lowPass(wl) * 0.06f
                    r += hpR.highPass(wr) * 0.08f + lpR.lowPass(wr) * 0.06f
                    if (rng.chance(120f / sr)) {
                        event = 0.6f + rng.nextFloat() * 0.4f
                        eventDecay = 0.985f
                        eventPan = rng.bipolar()
                        dropFilter.set(1500f + rng.nextFloat() * 4000f, 6f)
                    }
                    val d = dropFilter.bandPass(rng.bipolar() * event)
                    event *= eventDecay
                    l += d * 0.25f * (1 - eventPan)
                    r += d * 0.25f * (1 + eventPan)
                }
                Atmosphere.FIRE -> {
                    l += lpL.lowPass(brownL) * 0.9f
                    r += lpR.lowPass(brownR) * 0.9f
                    if (rng.chance(9f / sr)) {
                        event = 0.5f + rng.nextFloat() * 0.5f
                        eventDecay = 0.93f + rng.nextFloat() * 0.04f
                        eventPan = rng.bipolar() * 0.6f
                    }
                    val c = hp.highPass(rng.bipolar()) * event
                    event *= eventDecay
                    l += c * 0.35f * (1 - eventPan)
                    r += c * 0.35f * (1 + eventPan)
                }
                Atmosphere.SEA -> {
                    val wave = (0.55 + 0.45 * sin(2 * PI * t / 8.5)).toFloat()
                    val wave2 = (0.55 + 0.45 * sin(2 * PI * t / 11.0 + 1.3)).toFloat()
                    lpL.setCutoff(300f + 1400f * wave * wave)
                    lpR.setCutoff(300f + 1400f * wave2 * wave2)
                    l += lpL.lowPass(wl) * 0.35f * wave
                    r += lpR.lowPass(wr) * 0.35f * wave2
                }
                Atmosphere.WIND -> {
                    val gust = (0.5 + 0.5 * sin(2 * PI * t / 6.0) * sin(2 * PI * t / 2.3 + 0.7)).toFloat()
                    bandL.set(350f + 700f * gust, 1.4f)
                    bandR.set(380f + 650f * gust, 1.4f)
                    l += bandL.bandPass(wl) * (0.25f + 0.35f * gust)
                    r += bandR.bandPass(wr) * (0.25f + 0.35f * gust)
                }
                Atmosphere.FOREST -> {
                    // Leaves in a light breeze and occasional bird calls.
                    val breeze = (0.6 + 0.4 * sin(2 * PI * t / 7.0)).toFloat()
                    l += hp.highPass(lpL.lowPass(wl)) * 0.07f * breeze
                    r += hpR.highPass(lpR.lowPass(wr)) * 0.06f * breeze
                    val bird = birdSample()
                    l += bird * (1 - eventPan)
                    r += bird * (1 + eventPan)
                }
                Atmosphere.NIGHT -> {
                    l += lpL.lowPass(wl) * 0.03f
                    r += lpR.lowPass(wr) * 0.03f
                    // Crickets: chirps of four pulses, twice per second.
                    val cycle = (t % 0.5) / 0.5
                    val pulse = if (cycle < 0.3) {
                        val p = (cycle / 0.3 * 4) % 1.0
                        if (p < 0.5) sin(PI * p / 0.5).toFloat() else 0f
                    } else {
                        0f
                    }
                    cricketPhase += 4300f / sr
                    if (cricketPhase >= 1f) cricketPhase -= 1f
                    val c = Tables.lookup(Tables.sine, cricketPhase) * pulse * 0.05f
                    l += c * 0.8f
                    r += c * 0.5f
                }
            }
            left[i] += l * level
            right[i] += r * level
        }
    }

    private fun birdSample(): Float {
        if (chirpsLeft == 0 && t >= nextChirp) {
            chirpsLeft = 2 + rng.nextInt(4)
            eventPan = rng.bipolar() * 0.8f
            nextChirp = t + 3 + rng.nextFloat() * 6
        }
        if (chirpsLeft > 0 && chirpAmp <= 0.001f) {
            chirpFreq = 2600f + rng.nextFloat() * 1200f
            chirpTarget = chirpFreq + 800f + rng.nextFloat() * 1500f
            chirpAmp = 1f
            chirpsLeft--
        }
        if (chirpAmp <= 0.001f) return 0f
        chirpFreq += (chirpTarget - chirpFreq) * 0.0015f
        chirpPhase += chirpFreq / sr
        if (chirpPhase >= 1f) chirpPhase -= 1f
        chirpAmp *= 0.9993f
        return Tables.lookup(Tables.sine, chirpPhase) * chirpAmp * 0.03f
    }
}

/** Plays a [MoodStyle]: chord pad, arpeggio, melody, bells, pulse and drums. */
internal class MoodGenerator(
    private val sampleRate: Int,
    private val mood: Mood,
    intensity: Float,
    seed: Long,
) : Generator {
    private val style = MoodStyles.of(mood)
    private val rng = Rng(seed * 7919 + mood.ordinal)
    private val intensity = intensity.coerceIn(0f, 1f)
    override val reverb = style.reverb
    private val sr = sampleRate.toFloat()
    private var time = 0.0
    private var chordIndex = -1
    private var nextChord = 0.0
    private var nextArp = 0.5
    private var arpPos = 0
    private var arpDir = 1
    private var nextMelody = 1.0
    private var nextBellCheck = 0.0
    private var nextBeat = 0.0
    private var nextOstinato = 0.0
    private var ostinatoCount = 0
    private var nextShaker = 0.0
    private var nextKick = 0.0
    private var kickCount = 0
    private val pads = ArrayList<PadVoice>()
    private val plucks = ArrayList<PluckVoice>()
    private val bells = ArrayList<BellVoice>()
    private val thumps = ArrayList<ThumpVoice>()
    private val drones = ArrayList<PadVoice>()
    private val shimmers = ArrayList<PadVoice>()
    private val padFilterL = OnePole(sampleRate, style.padCutoff)
    private val padFilterR = OnePole(sampleRate, style.padCutoff)
    private val padL = FloatArray(BLOCK)
    private val padR = FloatArray(BLOCK)
    private val shaker = OnePole(sampleRate, 6000f)
    private var shakerEnv = 0f
    private val atmosphere = style.atmosphere?.let {
        AtmosphereGenerator(sampleRate, it, style.atmosphereLevel, seed)
    }
    private var shimmerLfo = 0.0

    init {
        style.drone?.forEach { drones.add(PadVoice(sampleRate, it.toFloat(), style.droneLevel, 3f, 4f)) }
        style.shimmer?.forEach { shimmers.add(PadVoice(sampleRate, it.toFloat(), style.shimmerLevel, 6f, 4f)) }
        // Brighter pads with higher intensity.
        val cutoff = style.padCutoff * (0.75f + 0.6f * this.intensity)
        padFilterL.setCutoff(cutoff)
        padFilterR.setCutoff(cutoff)
    }

    private val chord: IntArray get() = style.chords[chordIndex.coerceAtLeast(0) % style.chords.size]

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val n = minOf(BLOCK, frames - done)
            schedule()
            padL.fill(0f, 0, n)
            padR.fill(0f, 0, n)
            for (p in pads) p.render(padL, padR, n)
            for (d in drones) d.render(padL, padR, n)
            for (i in 0 until n) {
                left[done + i] += padFilterL.lowPass(padL[i])
                right[done + i] += padFilterR.lowPass(padR[i])
            }
            if (shimmers.isNotEmpty()) {
                padL.fill(0f, 0, n)
                padR.fill(0f, 0, n)
                for (s in shimmers) s.render(padL, padR, n)
                val trem = (0.6 + 0.4 * sin(shimmerLfo)).toFloat()
                for (i in 0 until n) {
                    left[done + i] += padL[i] * trem
                    right[done + i] += padR[i] * trem
                }
                shimmerLfo += 2 * PI * 0.15 * n / sr
            }
            renderInto(plucks, left, right, done, n) { v, l, r, c -> v.render(l, r, c) }
            renderInto(bells, left, right, done, n) { v, l, r, c -> v.render(l, r, c) }
            renderInto(thumps, left, right, done, n) { v, l, r, c -> v.render(l, r, c) }
            if (style.shakerLevel > 0f) {
                for (i in 0 until n) {
                    val s = shaker.highPass(rng.bipolar()) * shakerEnv * style.shakerLevel * (0.5f + intensity)
                    shakerEnv *= 0.9985f
                    left[done + i] += s * 0.8f
                    right[done + i] += s
                }
            }
            if (atmosphere != null) {
                padL.fill(0f, 0, n)
                padR.fill(0f, 0, n)
                atmosphere.render(padL, padR, n)
                for (i in 0 until n) {
                    left[done + i] += padL[i]
                    right[done + i] += padR[i]
                }
            }
            time += n / sr
            done += n
        }
        pads.removeAll { it.finished }
        plucks.removeAll { it.finished }
        bells.removeAll { it.finished }
        thumps.removeAll { it.finished }
    }

    private val scratchL = FloatArray(BLOCK)
    private val scratchR = FloatArray(BLOCK)

    private inline fun <T> renderInto(
        voices: List<T>,
        left: FloatArray,
        right: FloatArray,
        offset: Int,
        n: Int,
        render: (T, FloatArray, FloatArray, Int) -> Unit,
    ) {
        if (voices.isEmpty()) return
        scratchL.fill(0f, 0, n)
        scratchR.fill(0f, 0, n)
        for (v in voices) render(v, scratchL, scratchR, n)
        for (i in 0 until n) {
            left[offset + i] += scratchL[i]
            right[offset + i] += scratchR[i]
        }
    }

    /** Starts new notes; runs once per block (~5 ms), which is plenty for musical timing. */
    private fun schedule() {
        if (time >= nextChord) {
            for (p in pads) p.release()
            chordIndex++
            for (note in chord) {
                pads.add(PadVoice(sampleRate, note.toFloat(), style.padLevel / chord.size * 3f, style.padAttack, style.padAttack * 1.3f))
            }
            nextChord = time + style.chordSeconds
            arpPos = 0
        }
        if (style.arpStep > 0f && time >= nextArp) {
            nextArp = time + style.arpStep
            val chance = style.arpChance * (0.6f + 0.6f * intensity)
            if (rng.chance(chance)) {
                val notes = chord
                val idx = if (style.arpUpDown) {
                    if (arpPos >= notes.size - 1) arpDir = -1
                    if (arpPos <= 0) arpDir = 1
                    arpPos.coerceIn(0, notes.size - 1)
                } else {
                    rng.nextInt(notes.size)
                }
                arpPos += arpDir
                val note = notes[idx] + style.arpOctave
                plucks.add(PluckVoice(sampleRate, note.toFloat(), style.arpLevel, rng.bipolar() * 0.5f, style.arpBrightness, 0.996f, rng))
            }
        }
        if (style.melodyStep > 0f && time >= nextMelody) {
            nextMelody = time + style.melodyStep * (if (rng.chance(0.3f)) 0.5f else 1f)
            if (rng.chance(style.melodyChance)) {
                val note = chord[rng.nextInt(chord.size)] + 24
                bells.add(BellVoice(sampleRate, note.toFloat(), style.melodyLevel, rng.bipolar() * 0.3f, style.bellRatio, style.bellIndex, 2.8f))
            }
        }
        if (style.bellChancePerSec > 0f && time >= nextBellCheck) {
            nextBellCheck = time + 0.25
            if (rng.chance(style.bellChancePerSec * 0.25f * (0.5f + intensity))) {
                val scale = style.bellScale
                val note = scale?.get(rng.nextInt(scale.size)) ?: (chord[rng.nextInt(chord.size)] + style.bellOctave)
                bells.add(BellVoice(sampleRate, note.toFloat(), style.bellLevel, rng.bipolar() * 0.7f, style.bellRatio, style.bellIndex, 3.5f))
            }
        }
        if (style.heartbeatBpm > 0f && time >= nextBeat) {
            val bpm = style.heartbeatBpm + 30f * intensity
            nextBeat = time + 60.0 / bpm
            val level = style.heartbeatLevel * (0.6f + 0.6f * intensity)
            thumps.add(ThumpVoice(sampleRate, 90f, 48f, 0.18f, level))
            pendingSecondBeat = time + 0.22
        }
        if (pendingSecondBeat > 0 && time >= pendingSecondBeat) {
            pendingSecondBeat = -1.0
            thumps.add(ThumpVoice(sampleRate, 80f, 45f, 0.16f, style.heartbeatLevel * 0.6f * (0.6f + 0.6f * intensity)))
        }
        if (style.ostinatoStep > 0f && time >= nextOstinato) {
            nextOstinato = time + style.ostinatoStep
            val accent = ostinatoCount % 4 == 0
            ostinatoCount++
            val chance = if (accent) 1f else 0.45f + 0.55f * intensity
            if (rng.chance(chance)) {
                val root = chord.minOrNull() ?: 40
                val note = (if (root < 40) root + 12 else root) + if (ostinatoCount % 8 == 6) 7 else 0
                val level = style.ostinatoLevel * (if (accent) 1.2f else 0.8f)
                plucks.add(PluckVoice(sampleRate, note.toFloat(), level, 0f, 0.35f, 0.99f, rng))
            }
        }
        if (style.kickBpm > 0f && time >= nextKick) {
            nextKick = time + 60.0 / style.kickBpm
            kickCount++
            val level = style.kickLevel * (0.4f + 0.6f * intensity)
            thumps.add(ThumpVoice(sampleRate, 140f, 50f, 0.22f, level))
            if (kickCount % 4 == 0 && rng.chance(0.5f + 0.4f * intensity)) {
                thumps.add(ThumpVoice(sampleRate, 220f, 110f, 0.12f, level * 0.5f))
            }
        }
        if (style.shakerStep > 0f && time >= nextShaker) {
            nextShaker = time + style.shakerStep
            shakerEnv = 1f
        }
    }

    private var pendingSecondBeat = -1.0

    companion object {
        const val BLOCK = 256
    }
}

/** Short, friendly chime for chapter changes. */
internal class ChimeGenerator(sampleRate: Int, seed: Long) : Generator {
    override val reverb = 0.35f
    private val root = 74f + Rng(seed).nextInt(3) * 2
    private val bells = listOf(
        BellVoice(sampleRate, root, 0.12f, -0.3f, 1f, 1.5f, 2.5f),
        BellVoice(sampleRate, root + 7, 0.09f, 0.3f, 1f, 1.5f, 2.2f),
    )
    private var elapsed = 0
    private val secondAt = sampleRate / 5
    val finished get() = bells.all { it.finished }

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        bells[0].render(left, right, frames)
        if (elapsed >= secondAt) {
            bells[1].render(left, right, frames)
        }
        elapsed += frames
    }
}
