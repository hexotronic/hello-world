package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.scan.core.ByteArraySource
import io.github.hexotronic.lauscher.scan.core.FsEntry
import io.github.hexotronic.lauscher.scan.core.Id3ChapterParser
import io.github.hexotronic.lauscher.scan.core.LibraryGrouping
import io.github.hexotronic.lauscher.scan.core.Mp4ChapterParser
import io.github.hexotronic.lauscher.scan.core.NaturalOrder
import io.github.hexotronic.lauscher.scan.core.RawChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCoreTest {
    private val expected = listOf(
        RawChapter(0, "Prolog"),
        RawChapter(1500, "Kapitel 1: Über den Wald"),
        RawChapter(3250, "Kapitel 2 – Größere Schritte"),
    )

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream(name)) { name }.use { it.readBytes() }

    private fun assertChapters(actual: List<RawChapter>) {
        assertEquals(expected.map { it.title }, actual.map { it.title })
        for ((e, a) in expected.zip(actual)) {
            assertTrue("start ${a.startMs} vs ${e.startMs}", kotlin.math.abs(e.startMs - a.startMs) <= 30)
        }
    }

    @Test
    fun mp4WithBothChapterFormats() = assertChapters(Mp4ChapterParser.parse(ByteArraySource(resource("chapters.m4b"))))

    @Test
    fun mp4QuickTimeChapterTrackOnly() =
        assertChapters(Mp4ChapterParser.parse(ByteArraySource(resource("chapters_qt_only.m4b"))))

    @Test
    fun mp4NeroChplOnly() {
        val data = resource("chapters.m4b")
        // Break the tref/chap reference so only the chpl atom is left.
        val s = String(data, Charsets.ISO_8859_1)
        val idx = s.indexOf("chap")
        assertTrue(idx > 0)
        "xxxx".toByteArray().copyInto(data, idx)
        assertChapters(Mp4ChapterParser.parse(ByteArraySource(data)))
    }

    @Test
    fun id3v23Chapters() = assertChapters(Id3ChapterParser.parse(ByteArraySource(resource("chapters_v23.mp3"))))

    @Test
    fun id3v24Chapters() = assertChapters(Id3ChapterParser.parse(ByteArraySource(resource("chapters_v24.mp3"))))

    @Test
    fun parsersIgnoreOtherFormats() {
        assertTrue(Id3ChapterParser.parse(ByteArraySource(resource("chapters.m4b"))).isEmpty())
        assertTrue(Mp4ChapterParser.parse(ByteArraySource(resource("chapters_v23.mp3"))).isEmpty())
        assertTrue(Mp4ChapterParser.parse(ByteArraySource(ByteArray(3))).isEmpty())
    }

    @Test
    fun naturalOrder() {
        val sorted = listOf("Kapitel 10.mp3", "kapitel 2.mp3", "Kapitel 1.mp3", "Kapitel 02b.mp3", "Anhang.mp3")
            .sortedWith(NaturalOrder)
        assertEquals(listOf("Anhang.mp3", "Kapitel 1.mp3", "kapitel 2.mp3", "Kapitel 02b.mp3", "Kapitel 10.mp3"), sorted)
    }

    private fun file(name: String) = FsEntry(id = "id/$name", name = name, isDirectory = false)
    private fun dir(name: String, vararg children: FsEntry) =
        FsEntry(id = "id/$name", name = name, isDirectory = true, children = children.toList())

    @Test
    fun groupingDetectsBooks() {
        val root = dir(
            "Hörbücher",
            dir(
                "Astrid Lindgren",
                dir("Ronja Räubertochter", file("02.mp3"), file("01.mp3"), file("10.mp3"), file("cover.jpg")),
                dir("Mio, mein Mio", dir("CD 2", file("1.mp3")), dir("CD 1", file("1.mp3"), file("2.mp3")), file("folder.png")),
            ),
            dir("Einzeln", file("Buch A.m4b"), file("Buch B.m4b"), file(".hidden.mp3")),
            dir("Leer", file("notes.txt")),
        )
        val books = LibraryGrouping.group(root)
        assertEquals(listOf("Mio, mein Mio", "Ronja Räubertochter", "Buch A", "Buch B"), books.map { it.title })
        val ronja = books[1]
        assertEquals(listOf("01.mp3", "02.mp3", "10.mp3"), ronja.files.map { it.name })
        assertEquals("Astrid Lindgren", ronja.parentFolderName)
        assertEquals("cover.jpg", LibraryGrouping.bestCoverImage(ronja.images)?.name)
        // CD 1 comes before CD 2 although the folders are listed the other way round.
        assertEquals(3, books[0].files.size)
        assertEquals("folder.png", LibraryGrouping.bestCoverImage(books[0].images)?.name)
        val (title, author) = LibraryGrouping.guessTitleAndAuthor(ronja, null, null, null)
        assertEquals("Ronja Räubertochter" to "Astrid Lindgren", title to author)
    }

    @Test
    fun folderNameWithAuthor() {
        val book = LibraryGrouping.group(dir("Root", dir("Michael Ende - Momo", file("a.mp3"))))[0]
        assertEquals("Momo" to "Michael Ende", LibraryGrouping.guessTitleAndAuthor(book, null, null, null))
        assertEquals("Momo (Lesung)" to "Ende", LibraryGrouping.guessTitleAndAuthor(book, "Momo (Lesung)", "Ende", null))
    }
}
