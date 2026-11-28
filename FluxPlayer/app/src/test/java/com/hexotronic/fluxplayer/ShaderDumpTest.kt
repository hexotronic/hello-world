package com.hexotronic.fluxplayer

import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.render.Shaders
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Schreibt die generierten Shader nach build/shaders (für den WebGL-Funktionstest). */
class ShaderDumpTest {
    @Test
    fun dumpShaders() {
        val dir = File("build/shaders").apply { mkdirs() }
        File(dir, "vs_copy.glsl").writeText(Shaders.VS_COPY)
        File(dir, "fs_copy_oes.glsl").writeText(Shaders.FS_COPY_OES)
        File(dir, "vs_es3.glsl").writeText(Shaders.VS_ES3)
        File(dir, "median.glsl").writeText(Shaders.MEDIAN_FS)
        File(dir, "display.glsl").writeText(Shaders.DISPLAY_FS)
        File(dir, "upscale.glsl").writeText(Shaders.UPSCALE_FS)
        File(dir, "blit.glsl").writeText(Shaders.BLIT_FS)
        File(dir, "boundary.glsl").writeText(Shaders.boundaryFs(0.02f))
        for (q in FrameGenQuality.entries) {
            File(dir, "coarse_${q.name}.glsl").writeText(Shaders.coarseFs(q.coarseRadius, 0.012f))
            File(dir, "refine_${q.name}.glsl").writeText(Shaders.refineFs(q.fineRadius, 0.010f, 0.015f))
        }
        for (f in dir.listFiles()!!) {
            val text = f.readText()
            if (f.name != "vs_copy.glsl" && f.name != "fs_copy_oes.glsl") {
                assertTrue("${f.name} muss mit #version beginnen", text.startsWith("#version 300 es"))
            }
        }
    }
}
