package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.Anchor
import io.github.hexotronic.lauscher.analysis.core.BookTextParser
import io.github.hexotronic.lauscher.analysis.core.SamplingPlan
import io.github.hexotronic.lauscher.analysis.core.ScenePrompt
import io.github.hexotronic.lauscher.analysis.core.TextAligner
import io.github.hexotronic.lauscher.analysis.core.WindowPlanner
import io.github.hexotronic.lauscher.audio.dsp.TimeStretch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class BookTextTest {
    private val story = (1..3).joinToString("\n\n") { ch ->
        "Kapitel $ch\n\n" + (1..120).joinToString(" ") { w -> "wort$ch$w" } + " Ende des Kapitels $ch."
    }

    @Test
    fun plainTextFindsChapters() {
        val book = BookTextParser.fromPlain("Vorwort\r\n\r\nEin paar Worte.\r\n\r\n\r\n" + story)
        assertEquals(listOf("Vorwort", "Kapitel 1", "Kapitel 2", "Kapitel 3"), book.chapters.map { it.title })
        assertTrue(book.text.substring(book.chapters[2].start).startsWith("Kapitel 2"))
        assertTrue("\r" !in book.text && "\n\n\n" !in book.text)
    }

    @Test
    fun htmlBecomesText() {
        val t = BookTextParser.normalize(
            BookTextParser.htmlToText("<html><head><title>x</title></head><body><h1>Kapitel&nbsp;1</h1><p>Er sagte &quot;Hallo&quot; &amp; ging.<br/>Weiter&#33;</p></body></html>"),
        )
        assertTrue(t, t.startsWith("Kapitel 1\n") && t.contains("Er sagte \"Hallo\" & ging.\nWeiter!"))
    }

    @Test
    fun epubInReadingOrder() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun put(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
                put("mimetype", "application/epub+zip")
                put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="OEBPS/book.opf"/></rootfiles></container>""")
                put(
                    "OEBPS/book.opf",
                    """<package><manifest><item id="c2" href="text/two.xhtml"/><item id="c1" href="text/one.xhtml"/>
                    <item id="cov" href="cover.xhtml"/></manifest><spine><itemref idref="cov"/><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
                )
                put("OEBPS/cover.xhtml", "<html><body><p>Titelbild</p></body></html>")
                put("OEBPS/text/one.xhtml", "<html><body><h2>Erstes Kapitel</h2><p>" + "eins ".repeat(400) + "</p></body></html>")
                put("OEBPS/text/two.xhtml", "<html><body><h2>Zweites Kapitel</h2><p>" + "zwei ".repeat(400) + "</p></body></html>")
            }
        }.toByteArray()
        val book = BookTextParser.fromEpub(ByteArrayInputStream(bytes))
        assertEquals(listOf("Erstes Kapitel", "Zweites Kapitel"), book.chapters.map { it.title })
        assertTrue(book.text.startsWith("Titelbild"))
        assertTrue(book.text.indexOf("eins") < book.text.indexOf("zwei"))
    }

    @Test
    fun chaptersAnchorTheTextToTime() {
        val book = BookTextParser.fromPlain(story)
        val audio = listOf(
            TextAligner.AudioChapter("Kapitel 1", 0),
            TextAligner.AudioChapter("Kapitel 2", 600_000),
            TextAligner.AudioChapter("Kapitel 3", 1_500_000),
        )
        val anchors = TextAligner.initialAnchors(book, audio, 2_000_000)
        assertEquals(book.chapters[1].start, TextAligner.charAt(anchors, 600_000))
        // Halfway through chapter 2 in time is halfway through its text.
        val mid = TextAligner.charAt(anchors, 1_050_000)
        assertTrue(mid > book.chapters[1].start && mid < book.chapters[2].start)
        val slice = TextAligner.slice(book.text, anchors, 1_500_000, 1_600_000)
        assertTrue(slice, slice.startsWith("Kapitel 3"))
        // Different chapter counts: matching by number.
        val partial = TextAligner.initialAnchors(book, audio.drop(1) + TextAligner.AudioChapter("Bonus", 1_900_000), 2_000_000)
        assertEquals(book.chapters[1].start, TextAligner.charAt(partial, 600_000))
        assertEquals("12", TextAligner.chapterKey("Kapitel 12: Der Sturm"))
        assertEquals("4", TextAligner.chapterKey("Chapter IV"))
    }

    @Test
    fun heardSpeechIsFoundInTheText() {
        val words = (1..3000).map { "w${it % 997}x${it / 7}" }
        val text = words.joinToString(" ")
        val index = TextAligner.Index(text)
        // A recognised sample from word 1500 on, with a few errors and missing words.
        val heard = words.subList(1500, 1530).mapIndexed { i, w -> if (i % 7 == 3) "falsch" else w }.filterIndexed { i, _ -> i % 11 != 5 }.joinToString(" ")
        val pos = TextAligner.locate(index, heard, expectedChar = text.length / 3, radiusChars = text.length)
        assertNotNull(pos)
        assertTrue(abs(pos!! - text.indexOf(words[1500])) < 40)
        assertNull(TextAligner.locate(index, "ganz andere worte die nirgends vorkommen hier", text.length / 2, text.length))
        // A measured anchor replaces contradicting ones.
        val anchors = TextAligner.withAnchor(listOf(Anchor(0, 0), Anchor(1000, 5000), Anchor(2000, 9000)), Anchor(1500, 4000))
        assertEquals(listOf(Anchor(0, 0), Anchor(1500, 4000), Anchor(2000, 9000)), anchors)
    }

    @Test
    fun spedUpTranscriptsAreComparedWordByWord() {
        val normal = "Sie rannte am Ufer entlang, schneller und schneller, während der Fremde ihr folgte."
        assertEquals(1f, TextAligner.agreement(normal, normal.uppercase()), 1e-6f)
        // Two words misheard, one missing: 10 of 13 words agree.
        val fast = "sie rannte am ufer entlang schneller und schnell während der fremde sie"
        assertEquals(10f / 13f, TextAligner.agreement(normal, fast), 1e-6f)
        assertEquals(0f, TextAligner.agreement(normal, "Musik"), 1e-6f)
        assertEquals(0f, TextAligner.agreement("", "irgendwas"), 1e-6f)
    }

    @Test
    fun pendingWindowsSurviveANewWindowLength() {
        val analysed = WindowPlanner.windows(600_000, 120_000)
        // Switching to 3-minute windows: all of them are covered already.
        assertTrue(WindowPlanner.pending(WindowPlanner.windows(600_000, 180_000), analysed).isEmpty())
        // Beyond the analysed part, windows are still to do.
        assertEquals(3, WindowPlanner.pending(WindowPlanner.windows(1_000_000, 180_000), analysed).size)
    }

    @Test
    fun samplingPlanEstimates() {
        val p = SamplingPlan(windowMs = 120_000, sampleMs = 24_000, speed = 2f)
        assertEquals(0.2f, p.coverage, 1e-4f)
        assertEquals(30f, p.windowsPerHour, 1e-3f)
        // 30 windows × (12 s audio × 0.25 + 5 s model) = 240 s.
        assertEquals(240f, p.secondsPerHour(0.25f, 5f), 1e-2f)
        assertEquals(150f, p.secondsPerHour(0.25f, 5f, withSpeech = false), 1e-2f)
        assertTrue(SamplingPlan(120_000, Long.MAX_VALUE, 1f).full)
    }

    @Test
    fun excerptKeepsBeginningMiddleAndEnd() {
        val text = (1..400).joinToString(" ") { "w$it" }
        val e = ScenePrompt.excerpt(text, 120)
        assertTrue(e.length <= 140)
        assertTrue(e.startsWith("w1 ") && e.contains(" … ") && e.endsWith("w400"))
        assertEquals("kurz", ScenePrompt.excerpt("kurz", 120))
    }

    @Test
    fun speechIsFasterButKeepsItsPitch() {
        val rate = 16_000
        val input = FloatArray(rate * 4) { i ->
            // A 220 Hz voice-like tone with a syllable rhythm.
            (sin(2 * PI * 220 * i / rate) * (0.6 + 0.4 * sin(2 * PI * 3 * i / rate))).toFloat()
        }
        val fast = TimeStretch.speedUp(input, rate, 2f)
        assertTrue("length ${fast.size}", abs(fast.size - input.size / 2) < rate / 20)
        fun crossingsPerSecond(x: FloatArray): Double {
            var c = 0
            for (i in 1 until x.size) if ((x[i - 1] < 0) != (x[i] < 0)) c++
            return c / (x.size.toDouble() / rate)
        }
        assertEquals(crossingsPerSecond(input), crossingsPerSecond(fast), 20.0)
        assertTrue(TimeStretch.speedUp(input, rate, 1f) === input)
    }
}
