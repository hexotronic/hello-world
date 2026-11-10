package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.RangeEnd
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.core.Timeline
import io.github.hexotronic.lauscher.music.synth.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineTest {
    private val min = 60_000L
    private fun scene(from: Long, to: Long) = Scene(from * min, to * min, Mood.CALM, 1, "", false)

    @Test
    fun coverageAndFirstGap() {
        val scenes = listOf(scene(0, 10), scene(10, 20), scene(30, 40))
        assertEquals(1f, Timeline.coverage(scenes, 0, 20 * min), 1e-6f)
        assertEquals(0.5f, Timeline.coverage(scenes, 10 * min, 30 * min), 1e-6f)
        assertEquals(0f, Timeline.coverage(scenes, 20 * min, 30 * min), 1e-6f)
        // The first gap after the start, and after a position inside a scene.
        assertEquals(20 * min, Timeline.firstGap(scenes, 0, 60 * min))
        assertEquals(40 * min, Timeline.firstGap(scenes, 35 * min, 60 * min))
        assertEquals(25 * min, Timeline.firstGap(scenes, 25 * min, 60 * min))
        // A gap of under a second between scenes is none; a book analysed to the end has none.
        val tight = listOf(Scene(0, 60_000, Mood.CALM, 1, "", false), Scene(60_500, 120_000, Mood.SAD, 1, "", false))
        assertNull(Timeline.firstGap(tight, 0, 121_000))
    }

    @Test
    fun rangeEnds() {
        val chapters = listOf(0L, 20 * min, 45 * min)
        val book = 90 * min
        assertEquals(25 * min, Timeline.rangeEnd(RangeEnd.MIN_15, 10 * min, chapters, book))
        assertEquals(book, Timeline.rangeEnd(RangeEnd.HOUR, 50 * min, chapters, book))
        assertEquals(20 * min, Timeline.rangeEnd(RangeEnd.CHAPTER, 10 * min, chapters, book))
        assertEquals(45 * min, Timeline.rangeEnd(RangeEnd.CHAPTER, 20 * min, chapters, book))
        assertEquals(book, Timeline.rangeEnd(RangeEnd.CHAPTER, 50 * min, chapters, book))
        assertEquals(book, Timeline.rangeEnd(RangeEnd.END, 0, chapters, book))
    }

    @Test
    fun zoomWindowStaysInsideTheBook() {
        assertEquals((5 * min)..(35 * min), Timeline.window(20 * min, 30 * min, 90 * min))
        assertEquals(0L..(30 * min), Timeline.window(2 * min, 30 * min, 90 * min))
        assertEquals((60 * min)..(90 * min), Timeline.window(89 * min, 30 * min, 90 * min))
        // Longer than the book: the whole book.
        assertEquals(0L..(20 * min), Timeline.window(10 * min, 30 * min, 20 * min))
    }

    @Test
    fun roundTicks() {
        // Half an hour: every 5 minutes.
        assertEquals(listOf(0L, 5, 10, 15, 20, 25, 30).map { it * min }, Timeline.ticks(0, 30 * min, 8))
        // Ticks start at the next round time.
        assertEquals(listOf(15L, 20, 25).map { it * min }, Timeline.ticks(12 * min, 27 * min, 4))
        // Twelve hours with at most 7 labels: every 2 hours.
        assertEquals(7, Timeline.ticks(0, 12 * 60 * min, 7).size)
        assertEquals("1:02:03", Timeline.clock(3_723_000))
        assertEquals("9:05", Timeline.clock(545_000))
    }
}
