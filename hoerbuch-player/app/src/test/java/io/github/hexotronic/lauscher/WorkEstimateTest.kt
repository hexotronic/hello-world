package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.download.core.Rates
import io.github.hexotronic.lauscher.download.core.WorkEstimate
import io.github.hexotronic.lauscher.download.core.WorkEstimate.Item
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkEstimateTest {
    @Test
    fun oneVideoIsDownloadedThenConverted() {
        // 10 MB at 1 MB/s, then 10 minutes of sound at 60× real time, after 3 s looking up the stream.
        val item = Item(bytesLeft = 10_000_000, audioLeftMs = 600_000, overheadMs = 3_000)
        assertEquals(23_000, WorkEstimate.remainingMs(listOf(item), 1_000_000.0, 60.0))
        // Half converted already: only the rest of the conversion is left.
        assertEquals(5_000, WorkEstimate.remainingMs(listOf(Item(0, 300_000)), 1_000_000.0, 60.0))
        assertEquals(0, WorkEstimate.remainingMs(emptyList(), 1_000_000.0, 60.0))
    }

    @Test
    fun downloadsAndConversionsOverlap() {
        // Each item: 2 s download, 5 s conversion. The next item downloads during the conversion,
        // so the slower stage sets the pace: 2 + 3 × 5 s instead of 3 × 7 s.
        val slowConversion = List(3) { Item(bytesLeft = 2_000_000, audioLeftMs = 300_000) }
        assertEquals(17_000, WorkEstimate.remainingMs(slowConversion, 1_000_000.0, 60.0))
        // The other way round: the conversions wait for the downloads (3 × 5 s + 2 s).
        val slowNetwork = List(3) { Item(bytesLeft = 5_000_000, audioLeftMs = 120_000) }
        assertEquals(17_000, WorkEstimate.remainingMs(slowNetwork, 1_000_000.0, 60.0))
        // Original format: nothing to convert, only the downloads count.
        assertEquals(15_000, WorkEstimate.remainingMs(List(3) { Item(5_000_000, 0) }, 1_000_000.0, 60.0))
    }

    @Test
    fun speedsAreLearnedAndKept() {
        val store = HashMap<String, Float>()
        val rates = Rates({ store[it] }, { k, v -> store[k] = v })
        assertEquals(50.0, rates.convert(), 0.0)
        // The fast LAME setting starts out 1.5× as fast and is learned on its own.
        assertEquals(75.0, rates.convert(7), 0.0)
        assertEquals(1_000_000.0, rates.network, 0.0)
        // 10 minutes in 6 s: 100× real time is taken as it is the first time …
        rates.converted(600_000, 6_000, inPieces = true)
        assertEquals(100.0, rates.convert(), 0.01)
        assertEquals(75.0, rates.convert(7), 0.0)
        rates.converted(600_000, 4_000, inPieces = true, lameQuality = 7)
        assertEquals(150.0, rates.convert(7), 0.01)
        // … later measurements are averaged in.
        rates.converted(600_000, 12_000, inPieces = true)
        assertEquals(75.0, rates.convert(), 0.01)
        // Short files say little about the speed (start-up costs dominate).
        rates.converted(10_000, 1_000, inPieces = true)
        assertEquals(75.0, rates.convert(), 0.01)
        assertEquals(20.0, rates.convertSingle(), 0.0)
        rates.downloaded(8_000_000, 2_000, audioMs = 400_000)
        assertEquals(4_000_000.0, rates.network, 1.0)
        assertEquals(20.0, rates.bytesPerAudioMs, 0.01)
        // A fresh start reads the same values back.
        assertEquals(75.0, Rates({ store[it] }, { _, _ -> }).convert(), 0.01)
    }
}
