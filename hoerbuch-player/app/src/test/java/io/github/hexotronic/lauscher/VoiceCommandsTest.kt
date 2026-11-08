package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.voice.core.CommandMatcher
import io.github.hexotronic.lauscher.voice.core.SpeechDetector
import io.github.hexotronic.lauscher.voice.core.VoiceAction
import io.github.hexotronic.lauscher.voice.core.VoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class VoiceCommandsTest {
    private val withWake = VoiceCommands()
    private val noWake = VoiceCommands(wakeWordEnabled = false)

    private fun action(heard: String, commands: VoiceCommands = withWake) = CommandMatcher.match(heard, commands)?.action

    @Test
    fun commandsAfterTheWakeWordAreRecognised() {
        assertEquals(VoiceAction.PAUSE, action("Lauscher, Pause."))
        assertEquals(VoiceAction.PLAY, action("Lauscher weiter!"))
        assertEquals(VoiceAction.NEXT_CHAPTER, action("Lauscher, nächstes Kapitel."))
        assertEquals(VoiceAction.PREVIOUS_CHAPTER, action("Lauscher, Kapitel zurück"))
        assertEquals(VoiceAction.WHERE, action("Lauscher, wo bin ich?"))
        assertEquals(VoiceAction.LOUDER, action("Lauscher, bitte lauter"))
        // Small mishearings of the recogniser still count …
        assertEquals(VoiceAction.PAUSE, action("Lauscha, Pause."))
        assertEquals(VoiceAction.NEXT_CHAPTER, action("Lauscher, nächstes Capitel."))
        // … but without the wake word nothing happens, and short words must be exact.
        assertNull(action("Pause."))
        assertNull(action("Lauscher, Haus"))
        assertNull(action("Lauscher"))
    }

    @Test
    fun amountsAreRead() {
        val back = CommandMatcher.match("Lauscher, 30 Sekunden zurück.", withWake)!!
        assertEquals(VoiceAction.REWIND, back.action)
        assertEquals(30, back.seconds)
        assertEquals(30, CommandMatcher.match("Lauscher, dreißig Sekunden zurück", withWake)!!.seconds)
        val forward = CommandMatcher.match("Lauscher, zwei Minuten vor", withWake)!!
        assertEquals(VoiceAction.FORWARD, forward.action)
        assertEquals(120, forward.seconds)
        val sleep = CommandMatcher.match("Lauscher, Schlaftimer zwanzig Minuten", withWake)!!
        assertEquals(VoiceAction.SLEEP_TIMER, sleep.action)
        assertEquals(1200, sleep.seconds)
        assertEquals(45, CommandMatcher.number(CommandMatcher.words("fünfundvierzig").single()))
        assertEquals(13, CommandMatcher.number("dreizehn"))
        assertEquals(21, CommandMatcher.number("einundzwanzig"))
        assertNull(CommandMatcher.number("kapitel"))
    }

    @Test
    fun withoutWakeWordOnlyShortCommandsCount() {
        assertEquals(VoiceAction.PAUSE, action("Pause!", noWake))
        assertEquals(VoiceAction.PLAY, action("bitte weiter", noWake))
        assertEquals(VoiceAction.REWIND, action("15 Sekunden zurück", noWake))
        // A sentence that merely contains a command word does nothing.
        assertNull(action("Ich mache jetzt Pause und gehe dann einkaufen", noWake))
        assertNull(action("Wir fahren morgen weiter nach Hamburg", noWake))
    }

    @Test
    fun ownPhrasesAndSwitchedOffCommands() {
        val own = withWake
            .update(VoiceAction.PAUSE) { it.copy(phrases = listOf("ruhe", "moment mal")) }
            .update(VoiceAction.LOUDER) { it.copy(enabled = false) }
            .copy(wakeWord = "Hörbuch")
        assertEquals(VoiceAction.PAUSE, action("Hörbuch, Ruhe.", own))
        assertEquals(VoiceAction.PAUSE, action("Hörbuch, Moment mal!", own))
        assertNull(action("Hörbuch, Pause.", own))
        assertNull(action("Hörbuch, lauter", own))
        assertNull(action("Lauscher, Ruhe", own))
        // Kept as text and read back the same; broken text gives the defaults.
        assertEquals(own, VoiceCommands.fromJson(own.toJson()))
        assertEquals(VoiceCommands(), VoiceCommands.fromJson("kaputt"))
        assertEquals(VoiceCommands(), VoiceCommands.fromJson(null))
        assertTrue(own.recognizerHint().startsWith("Hörbuch, Weiter."))
        assertTrue("Lauter" !in own.recognizerHint())
    }

    @Test
    fun utterancesAreCutFromTheMicrophoneSignal() {
        val rate = 16_000
        val rnd = Random(4)
        var starts = 0
        val ends = ArrayList<Boolean>()
        val detector = SpeechDetector(rate, 0.5f, onStart = { starts++ }, onEnd = { ends += it })
        fun noise(seconds: Double, level: Float) = FloatArray((seconds * rate).toInt()) { (rnd.nextFloat() - 0.5f) * 2 * level }
        fun voice(seconds: Double, level: Float) = FloatArray((seconds * rate).toInt()) { i ->
            val t = i.toDouble() / rate
            (level * sin(2 * PI * 180 * t) * (0.6 + 0.4 * sin(2 * PI * 4 * t)) + (rnd.nextFloat() - 0.5f) * 0.004).toFloat()
        }
        // Background noise (about -50 dBFS), a spoken command, background again, fed in odd-sized blocks.
        val signal = noise(1.5, 0.005f) + voice(0.8, 0.3f) + noise(1.0, 0.005f) +
            voice(0.08, 0.3f) + noise(1.0, 0.005f) + // a click: too short
            voice(8.0, 0.3f) + noise(1.0, 0.005f) + // music or talking: too long
            voice(0.6, 0.2f) + noise(1.0, 0.005f)
        val found = ArrayList<FloatArray>()
        var at = 0
        while (at < signal.size) {
            val n = minOf(signal.size - at, 731)
            found += detector.feed(signal.copyOfRange(at, at + n), n)
            at += n
        }
        assertEquals("two commands found", 2, found.size)
        // Each keeps a little before (300 ms) and after (500 ms) the speech.
        assertTrue("${found[0].size}", found[0].size in (1.4 * rate).toInt()..(1.8 * rate).toInt())
        assertTrue("${found[1].size}", found[1].size in (1.2 * rate).toInt()..(1.6 * rate).toInt())
        assertEquals(4, starts)
        assertEquals(listOf(true, false, false, true), ends)
        assertTrue("noise floor ${detector.noiseFloorDb}", detector.noiseFloorDb in -60.0..-45.0)
    }
}
