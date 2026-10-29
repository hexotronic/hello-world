package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.music.synth.Atmosphere
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicMixer
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class MusicSynthTest {
    private val rate = 44_100

    private fun renderSeconds(mixer: MusicMixer, seconds: Float): FloatArray {
        val frames = (seconds * rate).toInt()
        val out = FloatArray(frames * 2)
        val block = FloatArray(1024 * 2)
        var pos = 0
        while (pos < frames) {
            val n = minOf(1024, frames - pos)
            mixer.render(block, n)
            System.arraycopy(block, 0, out, pos * 2, n * 2)
            pos += n
        }
        return out
    }

    private fun rmsDb(x: FloatArray, from: Int = 0): Double {
        var s = 0.0
        for (i in from until x.size) s += x[i] * x[i].toDouble()
        return 10 * log10(s / (x.size - from) + 1e-12)
    }

    @Test
    fun everyMoodAndAtmosphereIsAudibleBoundedAndLevelled() {
        val specs = Mood.entries.map { SoundSpec(mood = it, intensity = 0.6f, seed = 3) } +
            Atmosphere.entries.map { SoundSpec(atmosphere = it, seed = 3) }
        val levels = mutableListOf<Double>()
        for (spec in specs) {
            val mixer = MusicMixer(rate)
            mixer.volume = 1f
            mixer.crossfadeSeconds = 0.5f
            mixer.setTarget(spec)
            val t0 = System.nanoTime()
            val out = renderSeconds(mixer, 24f)
            val ms = (System.nanoTime() - t0) / 1e6
            for (v in out) {
                assertTrue("NaN in $spec", v.isFinite())
                assertTrue("clipping in $spec: $v", abs(v) <= 1f)
            }
            // Measure after AGC settled.
            val level = rmsDb(out, out.size / 2)
            // Atmospheres are meant to stay subtle; only the music moods must match in loudness.
            if (spec.mood != null) levels += level
            println("%-38s %6.1f dBFS  %5.0f ms for 24 s".format(spec.mood ?: spec.atmosphere, level, ms))
            assertTrue("$spec too quiet ($level dB)", level > -40)
            assertTrue("$spec too loud ($level dB)", level < -6)
        }
        val spread = levels.max() - levels.min()
        println("loudness spread %.1f dB".format(spread))
        assertTrue("moods should be similarly loud, spread $spread dB", spread < 12)
    }

    @Test
    fun crossfadeAndDuckingWork() {
        val mixer = MusicMixer(rate)
        mixer.volume = 1f
        mixer.crossfadeSeconds = 2f
        mixer.setTarget(SoundSpec(mood = Mood.CALM))
        val calm = renderSeconds(mixer, 10f)
        mixer.speechLevelDb = -20f
        val ducked = renderSeconds(mixer, 4f)
        mixer.speechLevelDb = -90f
        mixer.setTarget(SoundSpec(mood = Mood.ACTION))
        val action = renderSeconds(mixer, 8f)
        mixer.setTarget(null)
        val silence = renderSeconds(mixer, 6f)
        val calmDb = rmsDb(calm, calm.size / 2)
        val duckedDb = rmsDb(ducked, ducked.size / 2)
        println("calm %.1f, ducked %.1f, action %.1f, after stop %.1f".format(calmDb, duckedDb, rmsDb(action, action.size / 2), rmsDb(silence, silence.size / 2)))
        assertTrue("ducking should lower by > 5 dB", calmDb - duckedDb > 5)
        assertTrue(mixer.isSilent)
        assertTrue(rmsDb(silence, silence.size * 3 / 4) < -80)
    }
}
