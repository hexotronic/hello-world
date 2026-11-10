package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.AiLogBuffer
import io.github.hexotronic.lauscher.analysis.core.AiLogText
import io.github.hexotronic.lauscher.analysis.core.AiStep
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.library.core.MusicChoiceText
import io.github.hexotronic.lauscher.music.library.core.TrackPicker
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicChoice
import io.github.hexotronic.lauscher.music.synth.MusicFollow
import io.github.hexotronic.lauscher.music.synth.MusicMixer
import io.github.hexotronic.lauscher.music.synth.PcmStream
import io.github.hexotronic.lauscher.music.synth.RecordingSource
import io.github.hexotronic.lauscher.music.synth.Setting
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import io.github.hexotronic.lauscher.music.synth.describeScene
import io.github.hexotronic.lauscher.music.synth.tensionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFollowTest {
    private val rate = 44_100

    private class Stream(override val trackId: String, var left: Int) : PcmStream {
        override val finished get() = left == 0
        override fun read(left: FloatArray, right: FloatArray, frames: Int): Int {
            val n = minOf(frames, this.left)
            for (i in 0 until n) {
                left[i] = 0.1f
                right[i] = 0.1f
            }
            this.left -= n
            return n
        }

        override fun close() = Unit
    }

    /** Pieces for every mood; [fits] answers [stillFits] (null: the source cannot tell). */
    private class Source(val seconds: Float, var fits: ((Mood, Setting?) -> Boolean?)? = null, val none: Boolean = false) : RecordingSource {
        val opened = mutableListOf<String>()
        override fun open(mood: Mood, setting: Setting?, intensity: Float, trackId: String?): PcmStream? {
            if (none && trackId == null) return null
            val id = trackId ?: "${mood.name}-${opened.size}"
            opened += id
            return Stream(id, (seconds * 44_100).toInt())
        }

        override fun stillFits(trackId: String, mood: Mood, setting: Setting?, intensity: Float): Boolean? = fits?.invoke(mood, setting)
    }

    private fun mixer(source: Source, follow: MusicFollow, choices: MutableList<MusicChoice>? = null) = MusicMixer(rate).apply {
        recordings = source
        crossfadeSeconds = 0.5f
        this.follow = follow
        onChoice = { c -> choices?.add(c) }
    }

    private fun MusicMixer.play(seconds: Float) {
        val block = FloatArray(1024 * 2)
        repeat((seconds * rate / 1024).toInt().coerceAtLeast(1)) { render(block, 1024) }
    }

    @Test
    fun balancedChangesWithMoodPlaceOrTension() {
        val source = Source(60f)
        val m = mixer(source, MusicFollow.BALANCED)
        m.setTarget(SoundSpec(Mood.CALM, intensity = 0.2f, seed = 1, setting = Setting.FOREST))
        m.play(0.3f)
        m.setTarget(SoundSpec(Mood.CALM, intensity = 0.22f, seed = 2, setting = Setting.FOREST))
        m.play(0.3f)
        assertEquals("about the same tension keeps the piece", 1, source.opened.size)
        m.setTarget(SoundSpec(Mood.CALM, intensity = 0.55f, seed = 3, setting = Setting.FOREST))
        m.play(0.3f)
        assertEquals("one tension level more brings a new piece", 2, source.opened.size)
        m.setTarget(SoundSpec(Mood.CALM, intensity = 0.55f, seed = 4, setting = Setting.MAGIC))
        m.play(0.3f)
        assertEquals("another place brings a new piece", 3, source.opened.size)
    }

    @Test
    fun preciseFollowsEvenSmallChanges() {
        val source = Source(60f)
        val m = mixer(source, MusicFollow.PRECISE)
        m.setTarget(SoundSpec(Mood.TENSE, intensity = 0.55f, seed = 1, setting = Setting.DUNGEON))
        m.play(0.3f)
        m.setTarget(SoundSpec(Mood.TENSE, intensity = 0.55f, seed = 2, setting = Setting.DUNGEON))
        m.play(0.3f)
        assertEquals("the very same scene keeps its piece", 1, source.opened.size)
        m.setTarget(SoundSpec(Mood.TENSE, intensity = 0.65f, seed = 3, setting = Setting.DUNGEON))
        m.play(0.3f)
        assertEquals(2, source.opened.size)
    }

    @Test
    fun calmKeepsAPieceThatStillFitsAndPicksTheNextOneForTheNewScene() {
        val source = Source(1f, fits = { mood, _ -> mood != Mood.ACTION })
        val choices = mutableListOf<MusicChoice>()
        val m = mixer(source, MusicFollow.CALM, choices)
        m.setTarget(SoundSpec(Mood.CALM, intensity = 0.2f, seed = 1, setting = Setting.FOREST))
        m.play(0.3f)
        // Calm → tense in the same forest: the piece also suits the new scene and goes on.
        m.setTarget(SoundSpec(Mood.TENSE, intensity = 0.55f, seed = 2, setting = Setting.FOREST))
        m.play(0.3f)
        assertEquals(listOf("CALM-0"), source.opened)
        assertEquals(MusicChoice.What.KEPT, choices.last().what)
        assertEquals("CALM-0", choices.last().trackId)
        // When it ends, the next piece is picked for the scene that plays now.
        m.play(0.7f + 2.5f + 0.3f)
        assertEquals("TENSE-1", source.opened.last())
        assertEquals(MusicChoice.What.NEXT_PIECE, choices.last().what)
        // A clear turn (a fight) still brings a new piece.
        m.setTarget(SoundSpec(Mood.ACTION, intensity = 0.9f, seed = 3, setting = Setting.FOREST))
        m.play(0.3f)
        assertEquals("ACTION-2", source.opened.last())
        assertEquals(MusicChoice.What.PIECE, choices.last().what)
    }

    @Test
    fun calmWithoutCatalogueDataKeepsTheMoodNearThePlace() {
        val source = Source(60f, fits = null)
        val m = mixer(source, MusicFollow.CALM)
        m.setTarget(SoundSpec(Mood.SAD, intensity = 0.2f, seed = 1, setting = Setting.FOREST))
        m.play(0.3f)
        m.setTarget(SoundSpec(Mood.SAD, intensity = 0.9f, seed = 2, setting = Setting.MAGIC))
        m.play(0.3f)
        assertEquals("same mood, related place, any tension", 1, source.opened.size)
        m.setTarget(SoundSpec(Mood.SAD, intensity = 0.9f, seed = 3, setting = Setting.DESERT))
        m.play(0.3f)
        assertEquals(2, source.opened.size)
        m.setTarget(SoundSpec(Mood.HAPPY, intensity = 0.9f, seed = 4, setting = Setting.DESERT))
        m.play(0.3f)
        assertEquals(3, source.opened.size)
    }

    @Test
    fun theMixerReportsWhatItChose() {
        val choices = mutableListOf<MusicChoice>()
        val m = mixer(Source(60f, none = true), MusicFollow.CALM, choices)
        m.setTarget(SoundSpec(Mood.EERIE, intensity = 0.55f, seed = 1))
        m.play(0.2f)
        assertEquals(MusicChoice.What.SYNTH, choices.last().what)
        // The synthesizer, too, keeps playing in "Ruhig" while the mood stays.
        m.setTarget(SoundSpec(Mood.EERIE, intensity = 0.9f, seed = 2))
        m.play(0.2f)
        assertEquals(MusicChoice.What.KEPT, choices.last().what)
        m.setTarget(null)
        m.play(0.2f)
        assertEquals(MusicChoice.What.OFF, choices.last().what)
        assertEquals(3, choices.size)
    }

    @Test
    fun preciseTakesTheBestFittingPiece() {
        val forest = MusicCatalog.byId("wesnoth/silvan_sanctuary")!!
        val castle = MusicCatalog.byId("wesnoth/heroes_rite")!!
        val precise = TrackPicker(seed = 3)
        repeat(20) {
            assertEquals(forest.id, precise.pick(listOf(forest, castle), Mood.CALM, Setting.FOREST, 0.3f, follow = MusicFollow.PRECISE)!!.id)
        }
        // Over many scenes the precise choice fits at least as well on average.
        val tense = MusicCatalog.forMood(Mood.TENSE)
        fun meanCost(follow: MusicFollow): Double {
            val picker = TrackPicker(seed = 21)
            return (0 until 80).map { i ->
                val intensity = if (i % 2 == 0) 0.9f else 0.2f
                val t = picker.pick(tense, Mood.TENSE, Setting.DUNGEON, intensity, follow = follow)!!
                TrackPicker.fitCost(t, Mood.TENSE, Setting.DUNGEON, intensity).toDouble()
            }.average()
        }
        assertTrue(meanCost(MusicFollow.PRECISE) <= meanCost(MusicFollow.BALANCED))
    }

    @Test
    fun aPieceStillFitsOnlyCloseScenes() {
        val forest = MusicCatalog.byId("wesnoth/silvan_sanctuary")!!
        assertTrue(TrackPicker.stillFits(forest, Mood.IDYLLIC, Setting.FOREST, 0.2f))
        assertTrue(TrackPicker.stillFits(forest, Mood.CALM, Setting.MAGIC, 0.55f))
        assertFalse(TrackPicker.stillFits(forest, Mood.ACTION, Setting.FOREST, 0.9f))
        assertFalse(TrackPicker.stillFits(forest, Mood.IDYLLIC, Setting.DESERT, 0.2f))
    }

    @Test
    fun choicesAreExplainedInWords() {
        val forest = MusicCatalog.byId("wesnoth/silvan_sanctuary")!!
        val why = TrackPicker.describeFit(forest, Mood.IDYLLIC, Setting.FOREST, 0.4f)
        assertEquals("Gefühl „Idyllisch“ passt genau · für den Ort „Wald“ geschrieben · Energie passt zur Spannung", why.replace(Mood.IDYLLIC.label, "Idyllisch"))
        val spec = SoundSpec(Mood.CALM, intensity = 0.55f, setting = Setting.FOREST)
        assertEquals("${Mood.CALM.label} · Spannung 2 von 3 · Ort „Wald“", describeScene(spec))
        assertEquals(1, tensionLevel(0.2f))
        assertEquals(3, tensionLevel(0.9f))

        val piece = MusicChoiceText.describe(MusicChoice(spec, MusicChoice.What.PIECE, forest.id), MusicFollow.BALANCED, true, 12)
        assertTrue(piece, "Silvan Sanctuary" in piece && "Mattias Westlund" in piece && "passt auch zu" in piece)
        val kept = MusicChoiceText.describe(MusicChoice(spec, MusicChoice.What.KEPT, forest.id), MusicFollow.CALM, true, 12)
        assertTrue(kept, "läuft weiter" in kept && "Ruhig" in kept)
        val synth = MusicChoiceText.describe(MusicChoice(spec, MusicChoice.What.SYNTH), MusicFollow.BALANCED, true, 0)
        assertTrue(synth, "Synthesizer" in synth && "keine Stücke" in synth)
        val off = MusicChoiceText.describe(MusicChoice(null, MusicChoice.What.OFF), MusicFollow.BALANCED, true, 0)
        assertTrue(off, "Musik aus" in off)
    }

    @Test
    fun theLogKeepsTheLatestEntriesAndExportsThem() {
        val log = AiLogBuffer(capacity = 3)
        repeat(5) { log.add(AiStep.HEARD, "Satz $it", null, 1L, it * 60_000L, timeMs = 3_600_000L + it * 1000L) }
        val entries = log.add(AiStep.ANSWER, "Antwort", "x".repeat(AiLogBuffer.MAX_DETAIL + 50) + "\n{}", 1L, null, 3_610_000L)
        assertEquals(listOf("Satz 3", "Satz 4", "Antwort"), entries.map { it.text })
        assertTrue(entries.zipWithNext().all { (a, b) -> b.id > a.id })
        assertEquals(AiLogBuffer.MAX_DETAIL, entries.last().detail!!.length)
        assertEquals("01:00:04 Gehört @4:00: Satz 4", AiLogText.line(entries[1], 0))
        assertEquals("02:00:04", AiLogText.timeOfDay(3_604_000L, 3_600_000L))
        val text = AiLogText.export(log.snapshot(), 0)
        assertTrue(text, text.lines().any { it.startsWith("    xxx") })
        assertEquals("„eins zwei …“", AiLogText.excerpt("eins  zwei drei", max = 10))
        assertEquals(emptyList<Any>(), log.clear())
    }
}
