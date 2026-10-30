package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.Correction
import io.github.hexotronic.lauscher.analysis.core.KeywordMoods
import io.github.hexotronic.lauscher.analysis.core.MiniJson
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.ScenePrompt
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.analysis.core.WindowMood
import io.github.hexotronic.lauscher.analysis.core.WindowPlanner
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.Setting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SceneAnalysisTest {
    private val windows = listOf(
        TranscriptWindow(0, 90_000, "Am Morgen trank Anna ihren Tee und sah aus dem Fenster."),
        TranscriptWindow(90_000, 180_000, "Plötzlich hörte sie Schritte. Ein Schatten lauerte im Garten."),
        TranscriptWindow(180_000, 270_000, "Sie rannte los, die Verfolger dicht hinter ihr."),
    )

    @Test
    fun grammarHasOneRulePerWindow() {
        val g = ScenePrompt.grammar(3)
        assertTrue(g.contains("item0 ::="))
        assertTrue(g.contains("item2 ::="))
        assertTrue(!g.contains("item3 ::="))
        assertTrue(g.contains("\"\\\"TENSE\\\"\""))
        assertTrue(g.contains("place ::="))
        assertTrue(g.contains("\"\\\"DUNGEON\\\"\""))
        assertTrue(g.contains("\"\\\"OTHER\\\"\""))
        // Written out for the Python grammar check in the harness.
        System.getProperty("grammarOut")?.let { File(it).writeText(g) }
    }

    @Test
    fun titlesCopiedFromTheInstructionsAreDropped() {
        val out = """{"windows":[{"w":0,"mood":"CALM","level":1,"place":"SEA","title":"a short scene title in the"},
            {"w":1,"mood":"CALM","level":1,"place":"SEA","title":"Quiet morning at the lake"}],"story":""}"""
        val windows = listOf(TranscriptWindow(0, 90_000, "a"), TranscriptWindow(90_000, 180_000, "b"))
        val result = ScenePrompt.parse(out, windows)!!
        assertEquals("", result.windows[0].title)
        assertEquals("Quiet morning at the lake", result.windows[1].title)
    }

    @Test
    fun parsesModelOutput() {
        val out = """{"windows": [{"w": 0, "mood": "CALM", "level": 1, "place": "HOME", "title": "Morgentee"},
            {"w":1,"mood":"TENSE","level":2,"place":"OTHER","title":"Schritte im Garten"},
            {"w": 2, "mood": "ACTION", "level": 3, "place": "DUNGEON", "title": "Die Flucht \"beginnt\""}],
            "story": "Anna wird verfolgt.\nSie flieht."}"""
        val result = ScenePrompt.parse(out, windows)
        assertNotNull(result)
        result!!
        assertEquals(listOf(Mood.CALM, Mood.TENSE, Mood.ACTION), result.windows.map { it.mood })
        assertEquals("Die Flucht \"beginnt\"", result.windows[2].title)
        assertEquals(3, result.windows[2].level)
        assertEquals(listOf(Setting.HOME, null, Setting.DUNGEON), result.windows.map { it.setting })
        assertEquals("Anna wird verfolgt.\nSie flieht.", result.story)
    }

    @Test
    fun noneMeansNoMusicAndMissingEntriesFallBack() {
        val out = """{"windows":[{"w":0,"mood":"NONE","level":1,"title":"Vorwort"}],"story":""}"""
        val result = ScenePrompt.parse(out, windows)!!
        assertNull(result.windows[0].mood)
        assertEquals(Mood.NEUTRAL, result.windows[1].mood)
        assertNull(ScenePrompt.parse("kein json", windows))
    }

    @Test
    fun mergesScenesAndFindsPosition() {
        val moods = listOf(
            WindowMood(0, 90_000, Mood.CALM, 1, "A", SceneMerger.SOURCE_LLM),
            WindowMood(90_000, 180_000, Mood.CALM, 1, "B", SceneMerger.SOURCE_LLM),
            WindowMood(180_000, 270_000, Mood.ACTION, 3, "C", SceneMerger.SOURCE_LLM),
            WindowMood(270_000, 360_000, null, 1, "D", SceneMerger.SOURCE_LLM),
        )
        val scenes = SceneMerger.merge(moods)
        assertEquals(3, scenes.size)
        assertEquals(180_000, scenes[0].endMs)
        assertEquals(Mood.ACTION, SceneMerger.sceneAt(scenes, 200_000)?.mood)
        assertNull(SceneMerger.sceneAt(scenes, 300_000)?.mood)
        assertNull(SceneMerger.sceneAt(scenes, 999_999))
        assertEquals(scenes.last(), SceneMerger.sceneAt(scenes, 360_000))
    }

    @Test
    fun aNewPlaceStartsANewScene() {
        val moods = listOf(
            WindowMood(0, 90_000, Mood.TENSE, 2, "A", SceneMerger.SOURCE_LLM, Setting.FOREST),
            WindowMood(90_000, 180_000, Mood.TENSE, 2, "B", SceneMerger.SOURCE_LLM, Setting.FOREST),
            WindowMood(180_000, 270_000, Mood.TENSE, 2, "C", SceneMerger.SOURCE_LLM, Setting.DUNGEON),
        )
        val scenes = SceneMerger.merge(moods)
        assertEquals(listOf(Setting.FOREST, Setting.DUNGEON), scenes.map { it.setting })
        assertEquals(180_000, scenes[0].endMs)
    }

    @Test
    fun correctionsGuideTheModel() {
        val all = listOf(
            10_000L to Correction("Er schlich durch das Verlies", Mood.TENSE, 3, Setting.DUNGEON),
            500_000L to Correction("Später am Hafen", Mood.CALM, 1, Setting.SEA),
            90_000L to Correction("Die Wachen schliefen", Mood.MYSTERIOUS, 1, Setting.DUNGEON),
            200_000L to Correction("Im Thronsaal", null, 1, Setting.CASTLE),
            150_000L to Correction("Weitere", Mood.SAD, 2, null),
        )
        // Nearest earlier corrections first, at most three.
        val near = ScenePrompt.nearestCorrections(all, 210_000L)
        assertEquals(listOf("Im Thronsaal", "Weitere", "Die Wachen schliefen"), near.map { it.text })
        val prompt = ScenePrompt.user("Buch", null, null, windows, corrections = near)
        assertTrue(prompt.contains("\"Im Thronsaal\" -> mood NONE, place CASTLE, level 1"))
        assertTrue(prompt.contains("\"Weitere\" -> mood SAD, place OTHER, level 2"))
        // Without corrections the prompt stays as it was.
        assertTrue(!ScenePrompt.user("Buch", null, null, windows).contains("corrected"))
    }

    @Test
    fun keywordPlaces() {
        assertEquals(Setting.DUNGEON, KeywordMoods.settingOf("Im Kerker war es kalt. Die Zelle stank, das Verlies hatte kein Fenster."))
        assertEquals(Setting.SEA, KeywordMoods.settingOf("Das Schiff verließ den Hafen, die Wellen schlugen gegen den Bug."))
        assertEquals(Setting.WESTERN, KeywordMoods.settingOf("Der Sheriff betrat den Saloon und legte den Colt auf den Tisch."))
        assertNull(KeywordMoods.settingOf("Er dachte lange über die Frage nach."))
    }

    @Test
    fun keywordFallback() {
        assertEquals(Mood.ACTION, KeywordMoods.classify("Er rannte, die Verfolger schossen, ein Schuss, Flucht über die Dächer").first)
        assertEquals(Mood.SAD, KeywordMoods.classify("Beim Begräbnis weinte sie, Tränen liefen, der Abschied war schwer").first)
        assertEquals(Mood.NEUTRAL, KeywordMoods.classify("Das Kapitel behandelt die Verwaltung der Steuern.").first)
    }

    @Test
    fun planner() {
        val w = WindowPlanner.windows(400_000)
        assertEquals(4, w.size)
        assertEquals(360_000L until 400_000L, w.last())
        val sample = WindowPlanner.sampleOf(0L until 120_000L, 25_000L)
        assertEquals(25_000L, sample.last + 1 - sample.first)
        assertEquals(0L until 120_000L, WindowPlanner.sampleOf(0L until 120_000L, Long.MAX_VALUE))
        val chunks = WindowPlanner.chunks(0L until 100_000L)
        assertEquals(listOf(0L until 30_000L, 30_000L until 60_000L, 60_000L until 90_000L, 90_000L until 100_000L), chunks)
    }

    @Test
    fun miniJsonHandlesEscapes() {
        val v = MiniJson.parse("""{"a":[1,-2.5e1,true,null],"b":"ä\n"}""") as Map<*, *>
        assertEquals(listOf(1.0, -25.0, true, null), v["a"])
        assertEquals("ä\n", v["b"])
    }
}
