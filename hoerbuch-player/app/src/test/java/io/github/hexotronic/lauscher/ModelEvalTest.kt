package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.AssistantAction
import io.github.hexotronic.lauscher.analysis.core.AssistantReply
import io.github.hexotronic.lauscher.analysis.core.BookAssistant
import io.github.hexotronic.lauscher.analysis.core.MiniJson
import io.github.hexotronic.lauscher.analysis.core.ModelEval
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.MusicCue
import io.github.hexotronic.lauscher.music.synth.Setting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelEvalTest {
    @Test
    fun labelsUseRealNames() {
        val moods = Mood.entries.map { it.name } + "NONE"
        val places = Setting.entries.map { it.name }
        val cues = MusicCue.entries.map { it.name }
        for (w in ModelEval.windows) {
            assertTrue(w.moods.toString(), w.moods.all { it in moods })
            assertTrue(w.places.toString(), w.places.all { it in places })
            assertTrue(w.cues.toString(), w.cues.all { it in cues })
        }
        assertEquals(16, ModelEval.windows.size)
    }

    @Test
    fun theRetrievalGivesTheModelWhatItNeeds() {
        val all = ModelEval.transcript()
        for (q in ModelEval.questions) {
            val p = BookAssistant.passages(q.question, all, q.positionMs)
            // Never anything after the position.
            assertTrue(q.question, p.all { it.startMs < q.positionMs })
            if (q.forbid.isNotEmpty()) continue
            // The passage that holds the answer is among the excerpts.
            assertTrue(q.question + " → " + p.map { it.startMs / 60_000 }, p.any { w -> q.expectAny.any { it in w.text.lowercase() } })
        }
        // The spoiler trap really has nothing to tell before 16:00.
        val trap = ModelEval.questions.first { it.forbid.isNotEmpty() }
        assertTrue(BookAssistant.passages(trap.question, all, trap.positionMs).none { w -> trap.forbid.any { it in w.text.lowercase() } })
    }

    @Test
    fun commandsInEverydayWordsNeedTheModel() {
        for (c in ModelEval.commands) assertNull(c.text, BookAssistant.direct(c.text))
    }

    @Test
    fun scoring() {
        val r = 0 until 8
        val good = """{"windows":[""" + listOf(
            """{"w":0,"mood":"NONE","level":1,"place":"OTHER","pace":"SLOW","cues":[],"title":"Vorspann"}""",
            """{"w":1,"mood":"CALM","level":1,"place":"HOME","pace":"SLOW","cues":["HOME"],"title":"Brot"}""",
            """{"w":2,"mood":"HAPPY","level":2,"place":"TOWN","pace":"FAST","cues":["FEAST"],"title":"Fest"}""",
            """{"w":3,"mood":"TENSE","level":2,"place":"TAVERN","pace":"MEDIUM","cues":["DANGER"],"title":"Fremder"}""",
            """{"w":4,"mood":"CALM","level":1,"place":"OPEN_LAND","pace":"SLOW","cues":["NIGHT"],"title":"Nacht"}""",
            """{"w":5,"mood":"ACTION","level":3,"place":"FOREST","pace":"FAST","cues":["CHASE"],"title":"Wölfe"}""",
            """{"w":6,"mood":"ACTION","level":3,"place":"FOREST","pace":"FAST","cues":["BATTLE"],"title":"Aelrin"}""",
            """{"w":7,"mood":"SAD","level":3,"place":"CAVE","pace":"SLOW","cues":["GRIEF"],"title":"Feuer"}""",
        ).joinToString(",") + """],"story":"Lena zieht los."}"""
        val hits = ModelEval.scoreScenes(r, good)
        assertTrue(hits.valid)
        assertEquals(7, hits.moods)
        assertEquals(7, hits.placeCount)
        assertEquals(6, hits.places)
        assertEquals(7, hits.levels)
        assertEquals(6, hits.cues)
        assertFalse(ModelEval.scoreScenes(r, "{kaputt").valid)

        val trap = ModelEval.questions.first { it.forbid.isNotEmpty() }
        assertTrue(ModelEval.answerRight(trap, AssistantReply("Das weiß ich bis zu deiner Stelle noch nicht.")))
        assertFalse(ModelEval.answerRight(trap, AssistantReply("Aelrin fiel, aber der Drache wurde nicht besiegt.")))
        assertFalse(ModelEval.spoilerSafe(trap, AssistantReply("Aelrin fiel im Kampf.")))
        val rescue = ModelEval.questions[1]
        assertTrue(ModelEval.answerRight(rescue, AssistantReply("Der Elf Aelrin hat sie gerettet.")))

        val find = ModelEval.commands[0]
        assertTrue(ModelEval.commandRight(find, AssistantReply("", AssistantAction.FIND, "Wölfe heulen")))
        assertFalse(ModelEval.commandRight(find, AssistantReply("", AssistantAction.JUMP_TIME, "10:00")))
        assertTrue(ModelEval.commandRight(ModelEval.commands[3], AssistantReply("", AssistantAction.SPEED, "1,2")))
        assertTrue(ModelEval.commandRight(ModelEval.commands[7], AssistantReply("", AssistantAction.JUMP_TIME, "0:25:00")))
        assertTrue(ModelEval.commandRight(ModelEval.commands[7], AssistantReply("", AssistantAction.JUMP_TIME, "25")))
        assertTrue(ModelEval.commandRight(ModelEval.commands[2], AssistantReply("", AssistantAction.REWIND, "120")))
        assertFalse(ModelEval.commandRight(ModelEval.commands[2], AssistantReply("", AssistantAction.REWIND, "10")))
    }

    @Test
    fun resultsSurviveSaving() {
        val r = io.github.hexotronic.lauscher.analysis.core.EvalReport(
            "Qwen2.5 1.5B", 0.75f, 0.5f, 1f, 0.25f, 1f, 0.875f, 1f, 0.6f, 120.5f, 14.2f, 31.5f, 6.25f, 1.5f, quick = true,
        )
        val json = MiniJson.write(mapOf("qwen2.5-1.5b" to r.toMap()))
        val back = io.github.hexotronic.lauscher.analysis.core.EvalReport.fromMap((MiniJson.parse(json) as Map<*, *>)["qwen2.5-1.5b"] as Map<*, *>)
        assertEquals(r, back)
        assertEquals((0.75f + 0.5f + 1f + 0.25f + 0.875f + 0.6f) / 6f, r.understanding, 1e-6f)
        // 31.5 s for 8 windows of 2 minutes: 30 windows per hour.
        assertEquals(31.5f / 8 * 30, r.modelSecondsPerHour, 1e-3f)
        assertEquals("""{"a":"x\"y\n","b":[1,2.5,true,null]}""", MiniJson.write(mapOf("a" to "x\"y\n", "b" to listOf(1, 2.5, true, null))))
    }

    @Test
    fun exportForTheBenchmark() {
        val json = ModelEval.exportJson()
        val root = MiniJson.parse(json) as Map<*, *>
        val calls = (root["scene"] as Map<*, *>)["calls"] as List<*>
        assertEquals(2, calls.size)
        val assistant = root["assistant"] as Map<*, *>
        assertEquals(ModelEval.questions.size, (assistant["questions"] as List<*>).size)
        assertTrue("\"windowsPerHour\":30" in json)
        System.getProperty("evalOut")?.let { java.io.File(it).apply { parentFile?.mkdirs() }.writeText(json) }
    }
}
