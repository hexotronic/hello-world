package com.hexotronic.fluxplayer

import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.render.MpvHook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvHookTest {

    private fun asset(mode: UpscalerMode) = File("src/main/assets/" + mode.asset).readText()

    @Test
    fun parsesAllAnime4KModels() {
        val expected = mapOf(UpscalerMode.AI_S to 5, UpscalerMode.AI_M to 9, UpscalerMode.AI_L to 10)
        for ((mode, count) in expected) {
            val passes = MpvHook.parse(asset(mode))
            assertEquals("$mode Passes", count, passes.size)
            assertEquals("MAIN", passes.last().save)
            assertTrue(passes.last().width.endsWith("2 *"))
            assertTrue(passes.all { it.body.contains("vec4 hook()") })
            assertTrue(passes.first().binds.contains("MAIN"))
            // Shader-Quelle muss vollständig sein
            val fs = MpvHook.fragmentSource(passes.last())
            assertTrue(fs.startsWith("#version 300 es"))
            assertTrue(fs.contains("fragColor = hook();"))
        }
    }

    @Test
    fun evaluatesSizeExpressions() {
        val sizes = mapOf("MAIN" to intArrayOf(854, 480), "conv2d_last_tf" to intArrayOf(854, 480))
        assertEquals(854, MpvHook.evalSize("MAIN.w", sizes))
        assertEquals(960, MpvHook.evalSize("conv2d_last_tf.h 2 *", sizes))
        assertEquals(427, MpvHook.evalSize("MAIN.w 2 /", sizes))
        assertEquals(1708, MpvHook.evalSize("MAIN.w MAIN.w +", sizes))
    }

    @Test
    fun dumpPassesForGpuTest() {
        val dir = File("build/shaders").apply { mkdirs() }
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        for (mode in listOf(UpscalerMode.AI_S, UpscalerMode.AI_M, UpscalerMode.AI_L)) {
            val passes = MpvHook.parse(asset(mode))
            val json = passes.joinToString(",\n", "[\n", "\n]") { p ->
                "{\"binds\":[" + p.binds.joinToString(",") { q(it) } + "],\"save\":" + q(p.save) +
                    ",\"width\":" + q(p.width) + ",\"height\":" + q(p.height) + ",\"fs\":" + q(MpvHook.fragmentSource(p)) + "}"
            }
            File(dir, "ai_${mode.name}.json").writeText(json)
        }
    }
}
