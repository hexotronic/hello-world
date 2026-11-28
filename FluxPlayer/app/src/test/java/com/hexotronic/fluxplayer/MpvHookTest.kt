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
        for (mode in UpscalerMode.entries.filter { it.asset != null }) {
            val passes = MpvHook.parse(asset(mode))
            assertEquals("$mode gültig", null, MpvHook.validate(asset(mode)))
            assertTrue("$mode Passes", passes.size >= 5)
            assertTrue(passes.all { it.hook == "MAIN" })
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
        File(dir, "luma_in.glsl").writeText(com.hexotronic.fluxplayer.render.Shaders.LUMA_IN_FS)
        File(dir, "luma_combine.glsl").writeText(com.hexotronic.fluxplayer.render.Shaders.LUMA_COMBINE_FS)
        fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        val sources = ArrayList<Pair<String, String>>()
        File("src/main/assets/upscalers").listFiles { f -> f.name.endsWith(".glsl") }!!.forEach { sources += it.nameWithoutExtension to it.readText() }
        System.getenv("FLUX_CANDIDATES")?.let { c ->
            File(c).listFiles { f -> f.name.endsWith(".glsl") }?.forEach { sources += ("cand_" + it.nameWithoutExtension) to it.readText() }
        }
        for ((name, text) in sources) {
            val err = MpvHook.validate(text)
            assertEquals("$name muss gültig sein", null, err)
            val passes = MpvHook.parse(text)
            val json = passes.joinToString(",\n", "[\n", "\n]") { p ->
                "{\"hook\":" + q(p.hook) + ",\"binds\":[" + p.binds.joinToString(",") { q(it) } + "],\"save\":" + q(p.save) +
                    ",\"width\":" + q(p.width) + ",\"height\":" + q(p.height) + ",\"fs\":" + q(MpvHook.fragmentSource(p)) + "}"
            }
            File(dir, "ai_$name.json").writeText(json)
        }
    }

    @Test
    fun rejectsUnsupportedShaders() {
        assertTrue(MpvHook.validate("just text") != null)
        val tex = "//!TEXTURE LUT\n//!SIZE 4 4\n//!HOOK MAIN\n//!BIND MAIN\n//!WIDTH MAIN.w 2 *\nvec4 hook() { return MAIN_tex(MAIN_pos); }\n"
        assertTrue(MpvHook.validate(tex)!!.contains("TEXTURE"))
        val noUpscale = "//!HOOK MAIN\n//!BIND MAIN\nvec4 hook() { return MAIN_tex(MAIN_pos); }\n"
        assertTrue(MpvHook.validate(noUpscale)!!.contains("kein Upscaler"))
        val ok = "//!HOOK MAIN\n//!BIND HOOKED\n//!WIDTH HOOKED.w 2 *\n//!HEIGHT HOOKED.h 2 *\nvec4 hook() { return HOOKED_tex(HOOKED_pos); }\n"
        assertEquals(null, MpvHook.validate(ok))
    }
}
