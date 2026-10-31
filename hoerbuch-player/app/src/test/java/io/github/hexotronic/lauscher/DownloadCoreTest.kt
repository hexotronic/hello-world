package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.download.core.ArchiveOrg
import io.github.hexotronic.lauscher.download.core.DownloadPlan
import io.github.hexotronic.lauscher.download.core.FileNames
import io.github.hexotronic.lauscher.download.core.Grouping
import io.github.hexotronic.lauscher.download.core.Id3
import io.github.hexotronic.lauscher.download.core.JobStore
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.PlannedBook
import io.github.hexotronic.lauscher.download.core.SavedBook
import io.github.hexotronic.lauscher.download.core.SavedJob
import io.github.hexotronic.lauscher.download.core.StoragePaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class DownloadCoreTest {
    @Test
    fun librivoxSearchIsRestrictedToLanguageAndWords() {
        val url = ArchiveOrg.searchUrl("Grimm: Märchen", ArchiveOrg.Language.GERMAN, page = 2)
        val q = URLDecoder.decode(url.substringAfter("q=").substringBefore('&'), "UTF-8")
        assertEquals("collection:(librivoxaudio) AND language:(German OR ger OR deu OR Deutsch) AND (title:(Grimm AND Märchen) OR creator:(Grimm AND Märchen))", q)
        assertTrue(url.contains("&page=2&") && url.endsWith("output=json"))
        assertTrue(URLDecoder.decode(url, "UTF-8").contains("&fl[]=creator"))
        val all = URLDecoder.decode(ArchiveOrg.searchUrl("  ", ArchiveOrg.Language.ALL), "UTF-8")
        assertTrue(all.contains("q=collection:(librivoxaudio)&"))
    }

    @Test
    fun librivoxSearchResultsAreRead() {
        val json = """{"responseHeader":{"status":0},"response":{"numFound":2,"start":0,"docs":[
            {"identifier":"maerchen_1_librivox","title":"Kinder- und Hausmärchen","creator":["Grimm, Jacob","Grimm, Wilhelm"],"language":"German","downloads":12345},
            {"identifier":"x_librivox","title":"X","downloads":7}]}}"""
        val page = ArchiveOrg.parseSearch(json)
        assertEquals(2, page.total)
        assertEquals("Grimm, Jacob, Grimm, Wilhelm", page.items[0].author)
        assertEquals(12345L, page.items[0].downloads)
        assertNull(page.items[1].author)
        assertEquals(0, ArchiveOrg.parseSearch("{}").items.size)
    }

    @Test
    fun librivoxBookHasChaptersInOrder() {
        val json = """{"metadata":{"identifier":"maerchen","title":"Märchen","creator":"Grimm","description":"<p>Gelesen von <b>Vielen</b>.</p>"},
          "files":[
            {"name":"maerchen_02_grimm_64kb.mp3","format":"64Kbps MP3","title":"Rotkäppchen","track":"02","length":"610.5","size":"4900000"},
            {"name":"maerchen_01_grimm_64kb.mp3","format":"64Kbps MP3","title":"Froschkönig","track":"1/3","length":"10:05","size":"4800000"},
            {"name":"maerchen_01_grimm.mp3","format":"128Kbps MP3","track":"01"},
            {"name":"maerchen_sample.mp3","format":"64Kbps MP3"},
            {"name":"maerchen_10_grimm_64kb.mp3","format":"64Kbps MP3","title":"Ohne Nummer"},
            {"name":"cover big.jpg","format":"JPEG","size":"90000"},
            {"name":"small.jpg","format":"JPEG","size":"9000"},
            {"name":"__ia_thumb.jpg","format":"Item Tile","size":"5000"}]}"""
        val book = ArchiveOrg.parseMetadata("maerchen", json)!!
        assertEquals(listOf("Froschkönig", "Rotkäppchen", "Ohne Nummer"), book.files.map { it.title })
        assertEquals(605.0, book.files[0].seconds!!, 1e-6)
        assertEquals(1215.5, book.seconds, 1e-6)
        assertEquals("https://archive.org/download/maerchen/maerchen_01_grimm_64kb.mp3", book.files[0].url)
        assertEquals("https://archive.org/download/maerchen/cover%20big.jpg", book.coverUrl)
        assertEquals("Gelesen von Vielen.", book.description)
        assertNull(ArchiveOrg.parseMetadata("leer", """{"metadata":{},"files":[{"name":"a.ogg","format":"Ogg Vorbis"}]}"""))
        assertEquals(3723.0, ArchiveOrg.parseLength("1:02:03")!!, 1e-6)
    }

    @Test
    fun linksAndSearchWordsAreTold() {
        assertEquals("https://youtu.be/abc", LinkInput.firstUrl("Hör mal: https://youtu.be/abc."))
        assertNull(LinkInput.firstUrl("Sherlock Holmes Hörbuch"))
        assertEquals(2, LinkInput.allUrls("https://a.de/1 und https://a.de/2, https://a.de/1").size)
        assertTrue(LinkInput.isDirectMedia("https://example.org/files/Kapitel%201.MP4?token=x"))
        assertFalse(LinkInput.isDirectMedia("https://www.youtube.com/watch?v=abc"))
        assertEquals("Kapitel 1", LinkInput.fileNameOf("https://example.org/files/Kapitel%201.mp4?token=x"))
    }

    @Test
    fun fileNamesAreSafeAndSorted() {
        assertEquals("AC_DC_ Live _ 1977", FileNames.safe("AC/DC: Live | 1977"))
        assertEquals("Download", FileNames.safe("..."))
        assertEquals("007 Teil_ Eins.mp3", FileNames.track(6, 120, "Teil? Eins", "mp3").replace("007", "007"))
        assertEquals("03 A.m4a", FileNames.track(2, 12, "A", "m4a"))
    }

    @Test
    fun selectedItemsBecomeBooks() {
        val items = listOf(MediaItem("u1", "Folge 1", "Kanal"), MediaItem("u2", "Folge 2", "Kanal"))
        val one = DownloadPlan.books(items, Grouping.ONE_BOOK, "Die drei Fragezeichen")
        assertEquals(1, one.size)
        assertEquals("Kanal", one[0].author)
        assertEquals(2, one[0].items.size)
        val each = DownloadPlan.books(items, Grouping.EACH_BOOK, "egal")
        assertEquals(listOf("Folge 1", "Folge 2"), each.map { it.title })
        val mixed = DownloadPlan.books(items + MediaItem("u3", "Extra", "Anderer"), Grouping.ONE_BOOK, null)
        assertEquals("Folge 1", mixed[0].title)
        assertNull(mixed[0].author)
    }

    @Test
    fun id3TagIsWellFormed() {
        val tag = Id3.tag("Kapitel 1", "Grimm", "Märchen", 1, 3, cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2))
        assertEquals("ID3", String(tag, 0, 3, Charsets.ISO_8859_1))
        assertEquals(3, tag[3].toInt())
        val size = (tag[6].toInt() shl 21) or (tag[7].toInt() shl 14) or (tag[8].toInt() shl 7) or tag[9].toInt()
        assertEquals(tag.size - 10, size)
        // Walk the frames.
        var pos = 10
        val ids = ArrayList<String>()
        while (pos < tag.size) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            val n = ((tag[pos + 4].toInt() and 0xFF) shl 24) or ((tag[pos + 5].toInt() and 0xFF) shl 16) or
                ((tag[pos + 6].toInt() and 0xFF) shl 8) or (tag[pos + 7].toInt() and 0xFF)
            if (id == "TALB") assertEquals("Märchen", String(tag, pos + 13, n - 3, Charsets.UTF_16LE))
            ids += id
            pos += 10 + n
        }
        assertEquals(listOf("TIT2", "TPE1", "TALB", "TRCK", "APIC"), ids)
        assertEquals(tag.size, pos)
    }

    @Test
    fun storageFoldersAreComparedAndNamed() {
        assertTrue(StoragePaths.contains("primary:Audiobooks", "primary:Audiobooks/Lauscher/Grimm - Märchen"))
        assertTrue(StoragePaths.contains("primary:Audiobooks", "primary:Audiobooks"))
        assertTrue(StoragePaths.contains("primary:", "primary:Music/x"))
        assertFalse(StoragePaths.contains("primary:Audio", "primary:Audiobooks/x"))
        assertFalse(StoragePaths.contains("primary:Audiobooks", "1A2B-3C4D:Audiobooks/x"))
        assertEquals("Telefonspeicher › Audiobooks › Lauscher", StoragePaths.describe("primary:Audiobooks/Lauscher"))
        assertEquals("SD-Karte › Music", StoragePaths.describe("1A2B-3C4D:Music"))
        assertEquals("Telefonspeicher", StoragePaths.describe("primary:"))
        assertNull(StoragePaths.describe("msf:1234"))
        assertEquals("primary:Hörbücher/Grimm", StoragePaths.child("primary:Hörbücher", "Grimm"))
        assertEquals("primary:Grimm", StoragePaths.child("primary:", "Grimm"))
    }

    @Test
    fun jobsSurviveTheAppBeingClosed() {
        val media = SavedJob(
            id = 12, title = "Die \"drei\" ???", detail = "3 Dateien · MP3 64 kbit/s", total = 3, done = 1, failed = 0,
            state = "RUNNING", message = null, bookIds = listOf(4L, 9L), bytes = 5_123_456_789L, bytesTotal = -1, activeMs = 61_000,
            kind = "media",
            books = listOf(
                PlannedBook("Folge 1–3", "Kanal\nZwei", listOf(MediaItem("https://y/1?a=1&b=2", "Tëil 1", "Kanal", 1234, null), MediaItem("u2", "Teil 2"))),
                PlannedBook("Extra", null, emptyList()),
            ),
            format = "MP3", quality = "SPEECH", local = false,
            bookStates = listOf(
                SavedBook(
                    done = listOf(1), saved = listOf(Triple(1, "content://media/external/audio/media/77", "02 Teil 2.mp3")),
                    cover = "/data/files/downloads/12-cover.jpg", folderDocId = "primary:Audiobooks/Lauscher/Kanal - Folge 1–3",
                ),
                SavedBook(done = emptyList(), imported = false),
            ),
        )
        val book = SavedJob(
            id = 13, title = "Märchen", detail = "LibriVox", total = 12, done = 4, failed = 1, state = "PAUSED",
            message = "1 von 12 fehlgeschlagen", bookIds = emptyList(), bytes = 40_000_000, bytesTotal = 98_000_000, activeMs = 0,
            kind = "audiobook", archiveId = "grimm_maerchen_1_librivox", limit = null, next = 4,
            saved = listOf("content://x/1" to "01 Eins.mp3"), cover = "/data/c.jpg", folderDocId = "primary:A",
        )
        val text = JobStore.encode(listOf(media, book))
        assertEquals(listOf(media, book), JobStore.decode(text))
        // Broken or foreign files do not crash the app.
        assertTrue(JobStore.decode("").isEmpty())
        assertTrue(JobStore.decode("{\"jobs\":[{\"title\":\"ohne id\"}]}").isEmpty())
        assertTrue(JobStore.decode("nicht json").isEmpty())
    }

    @Test
    fun videoJobsOfTheEarlierFormatAreRead() {
        // Written when the items of a video job ran one after the other.
        val old = """{"version":1,"jobs":[{"id":3,"title":"Zwei","detail":"","total":4,"done":3,"failed":0,"state":"RUNNING",
            "message":null,"bookIds":[8],"bytes":10,"bytesTotal":-1,"activeMs":5,"kind":"media","archiveId":null,"limit":null,"next":0,
            "books":[{"title":"A","author":null,"items":[{"url":"u1","title":"a1","uploader":null,"duration":-1,"thumbnail":null},
            {"url":"u2","title":"a2","uploader":null,"duration":-1,"thumbnail":null}]},
            {"title":"B","author":null,"items":[{"url":"u3","title":"b1","uploader":null,"duration":-1,"thumbnail":null},
            {"url":"u4","title":"b2","uploader":null,"duration":-1,"thumbnail":null}]}],
            "format":"MP3","quality":"STANDARD","local":false,"bookIndex":1,"itemIndex":1,"doneItems":3,
            "saved":[["content://s/3","01 b1.mp3"]],"cover":"/c.jpg","folderDocId":"primary:B"}]}"""
        val job = JobStore.decode(old).single()
        assertEquals(
            listOf(
                SavedBook(done = listOf(0, 1), imported = true),
                SavedBook(done = listOf(0), saved = listOf(Triple(0, "content://s/3", "01 b1.mp3")), cover = "/c.jpg", folderDocId = "primary:B"),
            ),
            job.bookStates,
        )
        assertTrue(job.saved.isEmpty() && job.cover == null)
    }
}
