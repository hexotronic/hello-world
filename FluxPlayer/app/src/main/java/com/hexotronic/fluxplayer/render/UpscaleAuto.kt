package com.hexotronic.fluxplayer.render

import com.hexotronic.fluxplayer.data.UpscalerMode
import kotlin.math.max
import kotlin.math.min

/**
 * Automatische Upscaler-Wahl: richtet sich nach der Pixelrate, die pro Sekunde hochgerechnet werden muss
 * (Auflösung × Bildrate × Tempo), ob die GPU die KI-Modelle kann, und wie oft schon wegen Rucklern
 * heruntergeschaltet wurde.
 */
object UpscaleAuto {
    /** Leiter vom stärksten zum leichtesten automatisch gewählten Modell. */
    val LADDER = listOf(UpscalerMode.AI_VL, UpscalerMode.AI_M, UpscalerMode.AI_S, UpscalerMode.EDGE)

    private const val BUDGET_VL = 854L * 480L * 8L      // z. B. 480p in Zeitlupe (≤ 8 Bilder/s)
    private const val BUDGET_M = 854L * 480L * 31L     // bis ca. 480p mit 30 fps
    private const val BUDGET_S = 1280L * 720L * 31L    // bis ca. 720p mit 30 fps

    fun choose(
        srcW: Int,
        srcH: Int,
        fps: Float,
        speed: Float,
        needsUpscale: Boolean,
        aiSupported: Boolean,
        downgrade: Int
    ): UpscalerMode {
        if (!needsUpscale) return UpscalerMode.EDGE // nur Schärfe, keine Vergrößerung nötig
        val frames = max(1f, (if (fps > 1f) fps else 30f) * speed)
        val rate = (srcW.toLong() * srcH.toLong() * frames).toLong()
        val start = when {
            !aiSupported -> 3
            rate <= BUDGET_VL -> 0
            rate <= BUDGET_M -> 1
            rate <= BUDGET_S -> 2
            else -> 3
        }
        return LADDER[min(LADDER.lastIndex, start + downgrade.coerceAtLeast(0))]
    }
}
