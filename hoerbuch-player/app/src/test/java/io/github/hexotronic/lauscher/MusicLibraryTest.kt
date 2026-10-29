package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.music.library.core.LibraryTrack
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.library.core.TrackPicker
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicMixer
import io.github.hexotronic.lauscher.music.synth.PcmStream
import io.github.hexotronic.lauscher.music.synth.RecordingSource
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MusicLibraryTest {
    private val rate = 44_100

    @Test
    fun catalogIsCompleteAndPinned() {
        val tracks = MusicCatalog.tracks
        assertEquals("ids are unique", tracks.size, tracks.map { it.id }.toSet().size)
        for (t in tracks) {
            assertTrue(t.id, t.url.startsWith("https://raw.githubusercontent.com/"))
            assertTrue("${t.id} must use a fixed version", "/master/" !in t.url && "/main/" !in t.url)
            assertTrue(t.id, t.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(t.id, t.bytes > 50_000 && t.seconds >= 40)
            assertTrue(t.id, t.energy in 0f..1f && t.gainDb in -8f..15f)
            assertTrue(t.id, t.moods.isNotEmpty() && t.moods.distinct().size == t.moods.size)
            assertTrue(t.id, t.composer.isNotBlank() && t.title.isNotBlank())
        }
        // Several pieces for every mood.
        for (m in Mood.entries) assertTrue("$m has too few pieces", MusicCatalog.forMood(m).size >= 5)
    }

    @Test
    fun pickerPrefersFittingPiecesAndAvoidsRepeats() {
        val picker = TrackPicker(seed = 7)
        val calm = MusicCatalog.forMood(Mood.CALM)
        val picks = (0 until 40).map { picker.pick(calm, Mood.CALM, 0.3f)!!.id }
        // No piece twice in a row and nothing within the remembered window.
        for (i in 1 until picks.size) assertNotEquals(picks[i - 1], picks[i])
        for (i in 4 until picks.size) assertTrue(picks[i] !in picks.subList(i - 4, i))
        // Pieces made for the mood come up more often than ones that merely fit.
        val primary = picks.count { MusicCatalog.byId(it)!!.primaryMood == Mood.CALM }
        assertTrue("primary $primary of ${picks.size}", primary > picks.size / 3)
        assertNull(picker.pick(emptyList(), Mood.CALM, 0.5f))
        assertNull(picker.pick(MusicCatalog.forMood(Mood.ACTION).filter { Mood.ROMANTIC !in it.moods }, Mood.ROMANTIC, 0.5f))
    }

    @Test
    fun energyFollowsTheScene() {
        val picker = TrackPicker(seed = 11)
        val tense = MusicCatalog.forMood(Mood.TENSE)
        fun meanEnergy(intensity: Float) = (0 until 60).map { picker.pick(tense, Mood.TENSE, intensity)!!.energy }.average()
        assertTrue(meanEnergy(1f) > meanEnergy(0f))
    }

    /** A sine "recording" of a fixed length that is delivered in small pieces like a decoder would. */
    private class FakeStream(override val trackId: String, frames: Int) : PcmStream {
        var left = frames
        var phase = 0.0
        var closed = false
        override val finished get() = left == 0

        override fun read(left: FloatArray, right: FloatArray, frames: Int): Int {
            val n = minOf(frames, this.left, 300)
            for (i in 0 until n) {
                val v = (0.2 * sin(phase)).toFloat()
                phase += 2 * PI * 330 / 44_100
                left[i] = v
                right[i] = v
            }
            this.left -= n
            return n
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeSource(val seconds: Float, val available: Set<Mood>) : RecordingSource {
        val opened = mutableListOf<FakeStream>()
        override fun open(mood: Mood, intensity: Float, trackId: String?): PcmStream? {
            if (trackId == null && mood !in available) return null
            return FakeStream(trackId ?: "${mood.name}-${opened.size}", (seconds * 44_100).toInt()).also { opened += it }
        }
    }

    private fun render(mixer: MusicMixer, seconds: Float): Float {
        val block = FloatArray(1024 * 2)
        var peak = 0f
        repeat((seconds * rate / 1024).toInt()) {
            mixer.render(block, 1024)
            for (v in block) peak = maxOf(peak, kotlin.math.abs(v))
        }
        return peak
    }

    @Test
    fun recordingsPlayKeepRunningAcrossScenesAndFollowEachOther() {
        val source = FakeSource(seconds = 3f, available = setOf(Mood.CALM, Mood.TENSE))
        val mixer = MusicMixer(rate).apply {
            recordings = source
            crossfadeSeconds = 1f
        }
        mixer.setTarget(SoundSpec(Mood.CALM, intensity = 0.4f, seed = 1))
        assertTrue(render(mixer, 1f) > 0.01f)
        assertEquals("CALM-0", mixer.currentTrackId)

        // Next scene, same mood: the piece continues instead of starting over.
        mixer.setTarget(SoundSpec(Mood.CALM, intensity = 0.6f, seed = 2))
        render(mixer, 0.5f)
        assertEquals(1, source.opened.size)

        // The piece ends, a pause follows and then the next piece of the mood starts.
        render(mixer, 1.5f + 2.5f + 0.5f)
        assertTrue(source.opened[0].closed)
        assertEquals(2, source.opened.size)
        assertEquals("CALM-1", mixer.currentTrackId)

        // Another mood cross-fades to a new piece; the old one is closed after the fade.
        mixer.setTarget(SoundSpec(Mood.TENSE, intensity = 0.8f, seed = 3))
        render(mixer, 1.5f)
        assertEquals(3, source.opened.size)
        assertTrue(source.opened[1].closed)
        assertEquals("TENSE-2", mixer.currentTrackId)
    }

    @Test
    fun synthesizerStepsInWhenNoPieceIsAvailable() {
        val source = FakeSource(seconds = 3f, available = emptySet())
        val mixer = MusicMixer(rate).apply { recordings = source }
        mixer.setTarget(SoundSpec(Mood.SAD, intensity = 0.5f, seed = 4))
        assertTrue(render(mixer, 3f) > 0.01f)
        assertTrue(source.opened.isEmpty())
        assertNull(mixer.currentTrackId)

        // A preview of a specific piece always uses the recording.
        mixer.setTarget(SoundSpec(Mood.SAD, trackId = "wesnoth/sad"))
        render(mixer, 0.5f)
        assertEquals("wesnoth/sad", mixer.currentTrackId)

        mixer.closeAll()
        assertTrue(source.opened.all { it.closed })
    }

    @Test
    fun creditNamesComposerAndLicence() {
        val t: LibraryTrack = MusicCatalog.byId("wesnoth/silvan_sanctuary")!!
        assertTrue(t.credit, "Mattias Westlund" in t.credit && "GPL" in t.credit && "Wesnoth" in t.credit)
    }
}
