package com.hexotronic.fluxplayer

import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.render.UpscaleAuto
import org.junit.Assert.assertEquals
import org.junit.Test

class UpscaleAutoTest {
    @Test
    fun picksModelByPixelRate() {
        assertEquals(UpscalerMode.AI_M, UpscaleAuto.choose(854, 480, 30f, 1f, true, true, 0))
        assertEquals(UpscalerMode.AI_S, UpscaleAuto.choose(1280, 720, 30f, 1f, true, true, 0))
        assertEquals(UpscalerMode.EDGE, UpscaleAuto.choose(1280, 720, 60f, 1f, true, true, 0))
        // Zeitlupe: weniger Bilder pro Sekunde -> stärkeres Modell
        assertEquals(UpscalerMode.AI_S, UpscaleAuto.choose(1280, 720, 60f, 0.25f, true, true, 0))
        assertEquals(UpscalerMode.AI_M, UpscaleAuto.choose(1280, 720, 60f, 0.2f, true, true, 0))
        assertEquals(UpscalerMode.AI_VL, UpscaleAuto.choose(854, 480, 24f, 0.25f, true, true, 0))
    }

    @Test
    fun respectsGpuAndDowngrades() {
        assertEquals(UpscalerMode.EDGE, UpscaleAuto.choose(854, 480, 30f, 1f, true, false, 0))
        assertEquals(UpscalerMode.AI_S, UpscaleAuto.choose(854, 480, 30f, 1f, true, true, 1))
        assertEquals(UpscalerMode.EDGE, UpscaleAuto.choose(854, 480, 30f, 1f, true, true, 2))
        assertEquals(UpscalerMode.AI_M, UpscaleAuto.choose(854, 480, 24f, 0.25f, true, true, 1))
        assertEquals(UpscalerMode.EDGE, UpscaleAuto.choose(854, 480, 30f, 1f, true, true, 9))
    }

    @Test
    fun noUpscaleNeededMeansSharpenOnly() {
        assertEquals(UpscalerMode.EDGE, UpscaleAuto.choose(1920, 1080, 24f, 1f, false, true, 0))
    }
}
