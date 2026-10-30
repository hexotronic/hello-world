package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.music.library.core.LibraryTrack
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.library.core.PlayPlan
import io.github.hexotronic.lauscher.music.library.core.Segment
import io.github.hexotronic.lauscher.music.library.core.TrackPicker
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicMixer
import io.github.hexotronic.lauscher.music.synth.PcmStream
import io.github.hexotronic.lauscher.music.synth.RecordingSource
import io.github.hexotronic.lauscher.music.synth.Setting
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
    private val PINNED = Regex("^https://raw\\.githubusercontent\\.com/[^/]+/[^/]+/([0-9a-f]{40}|[0-9]+(\\.[0-9]+)+)/")

    @Test
    fun catalogIsCompleteAndPinned() {
        val tracks = MusicCatalog.tracks
        assertEquals("ids are unique", tracks.size, tracks.map { it.id }.toSet().size)
        for (t in tracks) {
            assertTrue(t.id, t.files.isNotEmpty())
            for (f in t.files) {
                // A commit or a release tag, never a moving branch.
                assertTrue("${t.id} must use a fixed version", PINNED.containsMatchIn(f.url))
                assertTrue(t.id, f.sha256.matches(Regex("[0-9a-f]{64}")))
                assertTrue(t.id, f.bytes > 5_000)
            }
            assertTrue(t.id, t.plan.all { it.file in t.files.indices && it.repeat >= 1 })
            assertTrue(t.id, t.bytes > 50_000 && t.seconds >= 40)
            assertTrue(t.id, t.energy in 0f..1f && t.gainDb in -8f..15f)
            assertTrue(t.id, t.moods.isNotEmpty() && t.moods.distinct().size == t.moods.size)
            assertTrue(t.id, t.composer.isNotBlank() && t.title.isNotBlank())
        }
        // Several pieces for every mood.
        for (m in Mood.entries) assertTrue("$m has too few pieces", MusicCatalog.forMood(m).size >= 5)
        // Every place has music of its own or of closely related places.
        for (p in Setting.entries) {
            val own = MusicCatalog.forSetting(p).size
            val near = p.related.sumOf { MusicCatalog.forSetting(it).size }
            assertTrue("$p: $own own, $near related", own + near >= 4)
        }
        // The starter pack covers every place that has pieces and stays small.
        val starter = MusicCatalog.starterPack
        for (p in Setting.entries) if (MusicCatalog.forSetting(p).isNotEmpty()) assertTrue("$p", starter.any { p in it.settings })
        assertTrue(starter.size < tracks.size / 2)
    }

    @Test
    fun loopsAreUnrolled() {
        val plan = listOf(Segment(0), Segment(1, 0f, -1f, repeat = 3), Segment(2))
        assertEquals(listOf(0, 1, 1, 1, 2), PlayPlan.expand(plan).map { it.file })
    }

    @Test
    fun pickerPrefersFittingPiecesAndAvoidsRepeats() {
        val picker = TrackPicker(seed = 7)
        val calm = MusicCatalog.forMood(Mood.CALM)
        val picks = (0 until 40).map { picker.pick(calm, Mood.CALM, null, 0.3f)!!.id }
        // No piece twice in a row and nothing within the remembered window.
        for (i in 1 until picks.size) assertNotEquals(picks[i - 1], picks[i])
        for (i in 4 until picks.size) assertTrue(picks[i] !in picks.subList(i - 4, i))
        // Pieces made for the mood come up more often than ones that merely fit.
        val primary = picks.count { MusicCatalog.byId(it)!!.primaryMood == Mood.CALM }
        assertTrue("primary $primary of ${picks.size}", primary > picks.size / 3)
        assertNull(picker.pick(emptyList(), Mood.CALM, null, 0.5f))
        // A dungeon loop is no music for a merry tavern, even if it is the only piece there is.
        assertNull(picker.pick(listOf(MusicCatalog.byId("spd/sewers_1")!!), Mood.HAPPY, Setting.TAVERN, 0.5f))
        // Nothing even close to the mood: better silence (or the synthesizer) than a misfit.
        assertNull(picker.pick(MusicCatalog.forMood(Mood.ACTION).filter { it.moods.all { m -> m == Mood.ACTION } }, Mood.SAD, null, 0.5f))
    }

    @Test
    fun placeAndTensionChooseThePiece() {
        val picker = TrackPicker(seed = 3)
        val all = MusicCatalog.tracks
        // In a dungeon: calm exploring, rising tension and the fight each get dungeon music.
        for ((mood, intensity) in listOf(Mood.MYSTERIOUS to 0.2f, Mood.TENSE to 0.55f, Mood.ACTION to 0.9f)) {
            repeat(5) {
                val t = picker.pick(all, mood, Setting.DUNGEON, intensity)!!
                assertTrue("${t.id} for $mood", Setting.DUNGEON in t.settings || t.settings.any { it in Setting.DUNGEON.related })
            }
        }
        // Forest scenes get forest music, space scenes space music.
        repeat(5) { assertTrue(Setting.FOREST in picker.pick(all, Mood.IDYLLIC, Setting.FOREST, 0.3f)!!.settings) }
        repeat(5) { assertTrue(Setting.SPACE in picker.pick(all, Mood.CALM, Setting.SPACE, 0.3f)!!.settings) }
        // Without a known place no strongly coloured music like space or Far East.
        repeat(20) {
            val t = picker.pick(all, Mood.CALM, null, 0.3f)!!
            assertTrue(t.id, t.settings.firstOrNull() !in setOf(Setting.SPACE, Setting.ASIA))
        }
    }

    @Test
    fun likedPiecesComeMoreOftenAndSkippedOnesWait() {
        val forest = MusicCatalog.forSetting(Setting.FOREST).filter { Mood.IDYLLIC in it.moods }
        val favourite = forest.last()
        fun count(liked: Set<String>): Int {
            val picker = TrackPicker(seed = 9)
            return (0 until 60).count { picker.pick(forest, Mood.IDYLLIC, Setting.FOREST, 0.3f, liked)!!.id == favourite.id }
        }
        assertTrue(count(setOf(favourite.id)) > count(emptySet()))
        val picker = TrackPicker(seed = 5)
        val first = picker.pick(forest, Mood.IDYLLIC, Setting.FOREST, 0.3f)!!
        repeat(10) { assertNotEquals(first.id, picker.pick(forest, Mood.IDYLLIC, Setting.FOREST, 0.3f, avoid = first.id)!!.id) }
        // Only one piece left: it still plays rather than nothing.
        assertEquals(first.id, picker.pick(listOf(first), Mood.IDYLLIC, Setting.FOREST, 0.3f, avoid = first.id)!!.id)
    }

    @Test
    fun energyFollowsTheScene() {
        val picker = TrackPicker(seed = 11)
        val tense = MusicCatalog.forMood(Mood.TENSE)
        fun meanEnergy(intensity: Float) = (0 until 60).map { picker.pick(tense, Mood.TENSE, null, intensity)!!.energy }.average()
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
        override fun open(mood: Mood, setting: Setting?, intensity: Float, trackId: String?): PcmStream? {
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

        // Next scene, same mood, place and tension: the piece continues instead of starting over.
        mixer.setTarget(SoundSpec(Mood.CALM, intensity = 0.45f, seed = 2))
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

        // Same mood, but a new place or a jump in tension: a fitting new piece comes in.
        mixer.setTarget(SoundSpec(Mood.TENSE, intensity = 0.8f, seed = 4, setting = Setting.DUNGEON))
        render(mixer, 0.2f)
        assertEquals(4, source.opened.size)
        mixer.setTarget(SoundSpec(Mood.TENSE, intensity = 0.85f, seed = 5, setting = Setting.DUNGEON))
        render(mixer, 0.2f)
        assertEquals("small tension changes keep the piece", 4, source.opened.size)
        mixer.setTarget(SoundSpec(Mood.TENSE, intensity = 0.2f, seed = 6, setting = Setting.DUNGEON))
        render(mixer, 0.2f)
        assertEquals(5, source.opened.size)
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
