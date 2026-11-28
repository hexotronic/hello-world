package com.hexotronic.fluxplayer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/** Zentrale Einstellungen (SharedPreferences). */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("flux_prefs", Context.MODE_PRIVATE)

    // Frame-Generierung
    var frameGenMode: FrameGenMode
        get() = enumValue("frame_gen_mode", FrameGenMode.MOTION)
        set(v) = putString("frame_gen_mode", v.name)
    var frameGenQuality: FrameGenQuality
        get() = enumValue("frame_gen_quality", FrameGenQuality.BALANCED)
        set(v) = putString("frame_gen_quality", v.name)
    var highRefreshRate: Boolean
        get() = sp.getBoolean("high_refresh", true)
        set(v) = putBool("high_refresh", v)
    var slowMoBoost: Boolean
        get() = sp.getBoolean("slowmo_boost", true)
        set(v) = putBool("slowmo_boost", v)
    /** Gewähltes Modell für „Selbst bestimmen“ (nie OFF – dafür gibt es den Schalter). */
    var upscaler: UpscalerMode
        get() = enumValue("upscaler", UpscalerMode.EDGE).let { if (it == UpscalerMode.OFF) UpscalerMode.EDGE else it }
        set(v) = putString("upscaler", (if (v == UpscalerMode.OFF) UpscalerMode.EDGE else v).name)
    var upscalerEnabled: Boolean
        get() = if (sp.contains("upscaler_on")) sp.getBoolean("upscaler_on", true)
        else enumValue("upscaler", UpscalerMode.EDGE) != UpscalerMode.OFF
        set(v) = putBool("upscaler_on", v)
    /** true = Player wählt Modell und Faktor selbst, false = selbst bestimmen. */
    var upscalerAuto: Boolean
        get() = sp.getBoolean("upscaler_auto", true)
        set(v) = putBool("upscaler_auto", v)
    var upscaleFactor: UpscaleFactor
        get() = enumValue("upscale_factor", UpscaleFactor.AUTO)
        set(v) = putString("upscale_factor", v.name)
    var sharpness: Float
        get() = sp.getFloat("sharpness", 0.3f)
        set(v) = sp.edit().putFloat("sharpness", v).apply()
    var showStats: Boolean
        get() = sp.getBoolean("show_stats", false)
        set(v) = putBool("show_stats", v)

    // Wiedergabe
    var resumePlayback: Boolean
        get() = sp.getBoolean("resume", true)
        set(v) = putBool("resume", v)
    var backgroundPlayback: Boolean
        get() = sp.getBoolean("background", false)
        set(v) = putBool("background", v)
    var autoPip: Boolean
        get() = sp.getBoolean("auto_pip", true)
        set(v) = putBool("auto_pip", v)
    var seekStepSeconds: Int
        get() = sp.getInt("seek_step", 10)
        set(v) = sp.edit().putInt("seek_step", v).apply()
    var defaultAspect: AspectMode
        get() = enumValue("default_aspect", AspectMode.FIT)
        set(v) = putString("default_aspect", v.name)
    var decoder: DecoderPreference
        get() = enumValue("decoder", DecoderPreference.AUTO)
        set(v) = putString("decoder", v.name)
    var orientation: OrientationMode
        get() = enumValue("orientation", OrientationMode.AUTO)
        set(v) = putString("orientation", v.name)
    var closeAtEnd: Boolean
        get() = sp.getBoolean("close_at_end", false)
        set(v) = putBool("close_at_end", v)

    // Gesten
    var gestureSeek: Boolean
        get() = sp.getBoolean("g_seek", true)
        set(v) = putBool("g_seek", v)
    var gestureBrightness: Boolean
        get() = sp.getBoolean("g_brightness", true)
        set(v) = putBool("g_brightness", v)
    var gestureVolume: Boolean
        get() = sp.getBoolean("g_volume", true)
        set(v) = putBool("g_volume", v)
    var gestureDoubleTap: Boolean
        get() = sp.getBoolean("g_double_tap", true)
        set(v) = putBool("g_double_tap", v)
    var gestureZoom: Boolean
        get() = sp.getBoolean("g_zoom", true)
        set(v) = putBool("g_zoom", v)
    var longPressSpeed: Boolean
        get() = sp.getBoolean("g_long_press", true)
        set(v) = putBool("g_long_press", v)
    var volumeBoost: Boolean
        get() = sp.getBoolean("volume_boost", true)
        set(v) = putBool("volume_boost", v)

    // Untertitel
    var subtitleScale: Float
        get() = sp.getFloat("sub_scale", 1f)
        set(v) = sp.edit().putFloat("sub_scale", v).apply()
    var subtitleBackground: Boolean
        get() = sp.getBoolean("sub_bg", false)
        set(v) = putBool("sub_bg", v)

    // Netzwerk
    var userAgent: String
        get() = sp.getString("user_agent", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        set(v) = putString("user_agent", v)
    var largeBuffer: Boolean
        get() = sp.getBoolean("large_buffer", false)
        set(v) = putBool("large_buffer", v)

    // Bibliothek
    var sortOrder: SortOrder
        get() = enumValue("sort", SortOrder.NAME)
        set(v) = putString("sort", v.name)
    var sortDescending: Boolean
        get() = sp.getBoolean("sort_desc", false)
        set(v) = putBool("sort_desc", v)

    // Equalizer
    var eqEnabled: Boolean
        get() = sp.getBoolean("eq_enabled", false)
        set(v) = putBool("eq_enabled", v)
    var eqPreset: Int
        get() = sp.getInt("eq_preset", -1)
        set(v) = sp.edit().putInt("eq_preset", v).apply()
    var eqBands: List<Int>
        get() = sp.getString("eq_bands", null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        set(v) = putString("eq_bands", v.joinToString(","))

    // Zuletzt genutzte Netzwerk-Adressen
    var urlHistory: List<String>
        get() {
            val raw = sp.getString("url_history", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                List(arr.length()) { arr.getString(it) }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(v) = putString("url_history", JSONArray(v.take(50)).toString())

    fun addUrlToHistory(url: String) {
        urlHistory = listOf(url) + urlHistory.filter { it != url }
    }

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T {
        val name = sp.getString(key, null) ?: return default
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    private fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    private fun putBool(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
