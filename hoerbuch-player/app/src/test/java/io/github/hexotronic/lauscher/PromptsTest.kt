package io.github.hexotronic.lauscher

import io.github.hexotronic.lauscher.analysis.core.PromptKind
import io.github.hexotronic.lauscher.analysis.core.PromptSettings
import io.github.hexotronic.lauscher.analysis.core.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test
    fun builtInInstructionsNameEveryAllowedValue() {
        for (kind in PromptKind.entries) {
            assertEquals(kind.name, emptyList<String>(), Prompts.warnings(kind, Prompts.default(kind)))
        }
        // The windows are two minutes long, as in the app.
        assertTrue("two minutes" in Prompts.default(PromptKind.SCENES))
    }

    @Test
    fun ownTextAndAddition() {
        var s = PromptSettings()
        assertEquals(Prompts.default(PromptKind.RECAP), Prompts.effective(PromptKind.RECAP, s))
        s = s.withExtra(PromptKind.SCENES, "Kampfszenen immer mit Stärke 3.")
        val scenes = Prompts.effective(PromptKind.SCENES, s)
        assertTrue(scenes.startsWith(Prompts.default(PromptKind.SCENES)))
        assertTrue(scenes.endsWith("${Prompts.EXTRA_HEADER}\nKampfszenen immer mit Stärke 3."))
        s = s.withCustom(PromptKind.RECAP, "Fasse in drei Sätzen zusammen.")
        assertEquals("Fasse in drei Sätzen zusammen.", Prompts.effective(PromptKind.RECAP, s))
        assertTrue(s.changed && s.isCustom(PromptKind.RECAP) && s.hasExtra(PromptKind.SCENES))
        // The built-in text saved unchanged counts as no change; reset removes both.
        assertFalse(s.withCustom(PromptKind.ASSISTANT, Prompts.default(PromptKind.ASSISTANT) + "\n").isCustom(PromptKind.ASSISTANT))
        assertFalse(s.reset(PromptKind.SCENES).isChanged(PromptKind.SCENES))
        assertEquals(s, PromptSettings.fromJson(s.toJson()))
        assertEquals(PromptSettings(), PromptSettings.fromJson("kaputt"))
    }

    @Test
    fun editedTextsAreChecked() {
        val cut = Prompts.default(PromptKind.SCENES).replace("CAVE = any cave", "Höhle")
        val w = Prompts.warnings(PromptKind.SCENES, cut)
        assertEquals(1, w.size)
        assertTrue(w.first(), w.first().startsWith("Orte fehlen: CAVE"))
        assertTrue(Prompts.warnings(PromptKind.ASSISTANT, "Antworte kurz.").first().startsWith("Aktionen fehlen"))
        assertTrue(Prompts.warnings(PromptKind.RECAP, "x".repeat(Prompts.MAX_CHARS + 1)).first().startsWith("Sehr lang"))
    }

    @Test
    fun examplesShowWhatTheModelGets() {
        for (kind in PromptKind.entries) assertTrue(kind.name, Prompts.example(kind).isNotBlank())
        assertTrue("Taverne" in Prompts.example(PromptKind.SCENES))
        assertTrue("Listener: Wer hat Lena vor den Wölfen gerettet?" in Prompts.example(PromptKind.ASSISTANT))
        assertTrue("Scene A: HAPPY" in Prompts.example(PromptKind.SCENE_CUT))
    }
}
