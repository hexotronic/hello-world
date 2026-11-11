package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.AssistantAction
import io.github.hexotronic.lauscher.analysis.core.AssistantTurn
import io.github.hexotronic.lauscher.analysis.core.BookAssistant
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookAssistantTest {
    private val min = 60_000L
    private val windows = listOf(
        TranscriptWindow(0, 2 * min, "Lena wohnte mit ihrer Großmutter in Weidenbach, einem Dorf am Fluss."),
        TranscriptWindow(2 * min, 4 * min, "Auf dem Markt tanzten die Leute, Musikanten spielten auf."),
        TranscriptWindow(4 * min, 6 * min, "Ein Fremder in der Taverne warnte vor dem Drachen in den Bergen."),
        TranscriptWindow(6 * min, 8 * min, "Im dunklen Wald heulten die Wölfe, Lena rannte um ihr Leben."),
        TranscriptWindow(8 * min, 10 * min, "Der Elf Aelrin schoss mit dem Bogen und rettete sie vor den Wölfen."),
        TranscriptWindow(10 * min, 12 * min, "Im Kampf gegen den Drachen fiel Aelrin."),
    )

    @Test
    fun findsTheFittingPassagesWithoutSpoilers() {
        val p = BookAssistant.passages("Wer hat Lena vor den Wölfen gerettet?", windows, untilMs = 9 * min)
        assertTrue(p.any { "Aelrin" in it.text })
        // Nothing after the position: the dragon fight is not given to the model.
        assertTrue(p.none { "fiel Aelrin" in it.text })
        // The window at the position is always part of it.
        assertTrue(p.any { it.startMs == 8 * min })
        assertEquals(p.map { it.startMs }.sorted(), p.map { it.startMs })
        // The window running at the position only up to it.
        val clipped = BookAssistant.clip(windows, 9 * min).last()
        assertEquals("Der Elf Aelrin schoss mit dem", clipped.text)
        assertEquals(9 * min, clipped.endMs)
        // Allowing spoilers finds the later passage.
        assertTrue(BookAssistant.passages("Was passierte mit Aelrin im Kampf?", windows, Long.MAX_VALUE).any { "fiel" in it.text })
    }

    @Test
    fun findsWhereSomethingHappens() {
        assertEquals(6 * min, BookAssistant.find("Wölfe heulten", windows)?.startMs)
        assertEquals(4 * min, BookAssistant.find("Drachen Taverne", windows)?.startMs)
        assertNull(BookAssistant.find("Raumschiff", windows))
    }

    @Test
    fun simpleCommandsNeedNoModel() {
        fun d(t: String) = BookAssistant.direct(t)
        assertEquals(AssistantAction.JUMP_CHAPTER to "3", d("Spring zu Kapitel 3")!!.let { it.action to it.value })
        assertEquals(AssistantAction.JUMP_CHAPTER to "12", d("kapitel 12")!!.let { it.action to it.value })
        assertEquals(AssistantAction.JUMP_TIME to "1:20:00", d("Spring zu 1:20:00")!!.let { it.action to it.value })
        assertEquals(AssistantAction.JUMP_TIME to "12:00", d("Geh zu Minute 12")!!.let { it.action to it.value })
        assertEquals(AssistantAction.SPEED, d("Tempo 1,5")!!.action)
        assertEquals(1.5f, d("Tempo 1,5")!!.number)
        assertEquals(AssistantAction.MUSIC_OFF, d("Musik aus")!!.action)
        assertEquals(AssistantAction.SLEEP_TIMER to "20", d("Schlaftimer 20 Minuten")!!.let { it.action to it.value })
        assertEquals(AssistantAction.REWIND to "30", d("30 Sekunden zurück")!!.let { it.action to it.value })
        assertEquals(AssistantAction.BOOKMARK, d("Lesezeichen")!!.action)
        assertEquals(AssistantAction.PAUSE, d("Pause")!!.action)
        // Questions are for the model.
        assertNull(d("Wer hat Lena gerettet?"))
        assertNull(d("Warum ging Lena zurück in den Wald?"))
        assertEquals(4_920_000L, BookAssistant.parseTime("1:22:00"))
        assertEquals(750_000L, BookAssistant.parseTime("12:30"))
        assertEquals(5 * min, BookAssistant.parseTime("5"))
        assertNull(BookAssistant.parseTime("bald"))
    }

    @Test
    fun promptGrammarAndParsing() {
        val user = BookAssistant.user(
            "Die Drachenhöhle", "A. Autorin", "Lena verlässt ihr Dorf.", 9 * min, "Kapitel 1", 2,
            windows.take(5), listOf(AssistantTurn("Wo wohnt Lena?", "In Weidenbach.")), "Wer rettete sie?", spoilerFree = true,
        )
        assertTrue("[8:00] Der Elf Aelrin" in user)
        assertTrue("Listener: Wo wohnt Lena?" in user && "You: In Weidenbach." in user)
        assertTrue(AssistantAction.entries.all { it.name in BookAssistant.SYSTEM })
        val g = BookAssistant.grammar()
        assertTrue("action ::=" in g && "\"\\\"FIND\\\"\"" in g)
        val r = BookAssistant.parse("""{"action":"SLEEP_TIMER","value":"25","answer":"Schlaf-Timer für 25 Minuten."}""")!!
        assertEquals(AssistantAction.SLEEP_TIMER, r.action)
        assertEquals(25f, r.number)
        assertEquals("Der Elf Aelrin.", BookAssistant.parse("""{"action":"NONE","value":"","answer":"Der Elf Aelrin."}""")!!.answer)
        assertNull(BookAssistant.parse("kein json"))
        // Written out for the grammar check with llama.cpp.
        System.getProperty("assistantGrammarOut")?.let { java.io.File(it).writeText(g) }
    }
}
