package com.hexotronic.fluxplayer.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.hexotronic.fluxplayer.data.Prefs
import kotlin.math.log10
import kotlin.math.roundToInt

/** Equalizer und Lautstärke-Boost (bis 200 %) auf der Audio-Session des Players. */
class AudioEffects(private val prefs: Prefs) {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId = 0
    var boostPercent: Int = 100
        private set

    val available: Boolean get() = equalizer != null

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && equalizer != null) return
        release()
        if (audioSessionId <= 0) return
        sessionId = audioSessionId
        equalizer = try {
            Equalizer(0, audioSessionId).also { eq ->
                val bands = prefs.eqBands
                if (prefs.eqPreset in 0 until eq.numberOfPresets) {
                    eq.usePreset(prefs.eqPreset.toShort())
                } else if (bands.size == eq.numberOfBands.toInt()) {
                    bands.forEachIndexed { i, level -> eq.setBandLevel(i.toShort(), level.toShort()) }
                }
                eq.enabled = prefs.eqEnabled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer nicht verfügbar", e)
            null
        }
        loudness = try {
            LoudnessEnhancer(audioSessionId)
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer nicht verfügbar", e)
            null
        }
        setBoost(boostPercent)
    }

    fun setBoost(percent: Int) {
        boostPercent = percent.coerceIn(100, 200)
        val le = loudness ?: return
        try {
            val gainMb = (2000.0 * log10(boostPercent / 100.0)).roundToInt()
            le.setTargetGain(gainMb)
            le.enabled = gainMb > 0
        } catch (e: Throwable) {
            Log.w(TAG, "Boost fehlgeschlagen", e)
        }
    }

    // --- Equalizer
    var enabled: Boolean
        get() = equalizer?.enabled == true
        set(v) {
            prefs.eqEnabled = v
            try {
                equalizer?.enabled = v
            } catch (_: Throwable) {
            }
        }

    fun presets(): List<String> {
        val eq = equalizer ?: return emptyList()
        return try {
            List(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun currentPreset(): Int = prefs.eqPreset

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        try {
            eq.usePreset(index.toShort())
            prefs.eqPreset = index
            prefs.eqBands = bandLevels()
        } catch (_: Throwable) {
        }
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 0

    fun levelRange(): Pair<Int, Int> {
        val r = equalizer?.bandLevelRange ?: return -1500 to 1500
        return r[0].toInt() to r[1].toInt()
    }

    fun centerFrequencyHz(band: Int): Int = (equalizer?.getCenterFreq(band.toShort()) ?: 0) / 1000

    fun bandLevels(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
    }

    fun setBandLevel(band: Int, level: Int) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(band.toShort(), level.toShort())
            prefs.eqPreset = -1
            prefs.eqBands = bandLevels()
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (_: Throwable) {
        }
        try {
            loudness?.release()
        } catch (_: Throwable) {
        }
        equalizer = null
        loudness = null
        sessionId = 0
    }

    private companion object {
        const val TAG = "AudioEffects"
    }
}
