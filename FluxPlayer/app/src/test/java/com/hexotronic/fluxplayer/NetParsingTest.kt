package com.hexotronic.fluxplayer

import com.hexotronic.fluxplayer.data.NaturalOrder
import com.hexotronic.fluxplayer.net.HtmlSniffer
import com.hexotronic.fluxplayer.net.PlaylistParser
import com.hexotronic.fluxplayer.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetParsingTest {

    @Test
    fun parsesIptvM3uWithVlcOptions() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="a" tvg-logo="x.png" group-title="News",Kanal Eins HD
            #EXTVLCOPT:http-user-agent=MyAgent/1.0
            #EXTVLCOPT:http-referrer=https://example.org/
            http://example.com/live/1.m3u8
            #EXTINF:-1 tvg-name="Zwei",
            streams/two.ts
        """.trimIndent()
        assertFalse(PlaylistParser.isHls(text))
        assertTrue(PlaylistParser.looksLikeM3u(text))
        val e = PlaylistParser.parseM3u(text, "https://host.tv/lists/all.m3u")
        assertEquals(2, e.size)
        assertEquals("Kanal Eins HD", e[0].title)
        assertEquals("MyAgent/1.0", e[0].headers["User-Agent"])
        assertEquals("https://example.org/", e[0].headers["Referer"])
        assertEquals("Zwei", e[1].title)
        assertEquals("https://host.tv/lists/streams/two.ts", e[1].uri)
        assertTrue(e[1].headers.isEmpty())
    }

    @Test
    fun detectsHls() {
        val hls = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nlow.m3u8\n"
        assertTrue(PlaylistParser.isHls(hls))
    }

    @Test
    fun parsesPls() {
        val pls = "[playlist]\nFile1=http://a/radio.mp3\nTitle1=Radio A\nFile2=b.aac\nNumberOfEntries=2\n"
        assertTrue(PlaylistParser.looksLikePls(pls))
        val e = PlaylistParser.parsePls(pls, "http://srv/x.pls")
        assertEquals(2, e.size)
        assertEquals("Radio A", e[0].title)
        assertEquals("http://srv/b.aac", e[1].uri)
    }

    @Test
    fun sniffsVideoFromHtml() {
        val html = """
            <html><head><title>Mein Video &amp; mehr</title>
            <meta property="og:video" content="https://cdn.example.com/v/clip.mp4?sig=1&amp;e=2">
            </head><body>
            <video controls><source src="/media/movie.webm" type="video/webm"></video>
            <script>var cfg = {"hls":"https:\/\/stream.example.com\/master.m3u8?token=abc"};</script>
            </body></html>
        """.trimIndent()
        val found = HtmlSniffer.find(html, "https://www.example.com/page/1")
        val urls = found.map { it.url }
        assertEquals("https://stream.example.com/master.m3u8?token=abc", urls.first())
        assertTrue(urls.contains("https://cdn.example.com/v/clip.mp4?sig=1&e=2"))
        assertTrue(urls.contains("https://www.example.com/media/movie.webm"))
        assertEquals("Mein Video & mehr", HtmlSniffer.pageTitle(html))
    }

    @Test
    fun naturalSort() {
        val list = listOf("Folge 10.mkv", "Folge 2.mkv", "folge 1.mkv").sortedWith(NaturalOrder)
        assertEquals(listOf("folge 1.mkv", "Folge 2.mkv", "Folge 10.mkv"), list)
    }

    @Test
    fun formatting() {
        assertEquals("01:05", Format.time(65_000))
        assertEquals("1:01:05", Format.time(3_665_000))
        assertEquals("1x", Format.speed(1f))
        assertEquals("1,5x", Format.speed(1.5f))
        assertEquals("1,25x", Format.speed(1.25f))
        assertEquals("0,75x", Format.speed(0.75f))
    }
}
