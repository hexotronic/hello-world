package io.github.hexotronic.lauscher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
    BLACK("Schwarz (AMOLED)"),
}

data class AppSettings(
    val rewindSeconds: Int = 15,
    val forwardSeconds: Int = 30,
    val smartRewind: Boolean = true,
    val defaultSpeed: Float = 1f,
    val headsetSkipsChapters: Boolean = true,
    val skipSilence: Boolean = false,
    val resumeAfterCall: Boolean = true,
    val sleepDefaultMinutes: Int = 30,
    val sleepFadeOut: Boolean = true,
    val sleepShakeToExtend: Boolean = true,
    val sleepBookmark: Boolean = true,
    val sleepAuto: Boolean = false,
    val sleepAutoFromHour: Int = 22,
    val sleepAutoToHour: Int = 6,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val libraryGrid: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val libraryFilter: LibraryFilter = LibraryFilter.ALL,
    val keepScreenOn: Boolean = false,
    val autoRescan: Boolean = true,
    val showRemainingWithSpeed: Boolean = true,
    val onboardingDone: Boolean = false,
)

/** SharedPreferences backed settings exposed as a [StateFlow]. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        write(next)
    }

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            rewindSeconds = prefs.getInt("rewindSeconds", d.rewindSeconds),
            forwardSeconds = prefs.getInt("forwardSeconds", d.forwardSeconds),
            smartRewind = prefs.getBoolean("smartRewind", d.smartRewind),
            defaultSpeed = prefs.getFloat("defaultSpeed", d.defaultSpeed),
            headsetSkipsChapters = prefs.getBoolean("headsetSkipsChapters", d.headsetSkipsChapters),
            skipSilence = prefs.getBoolean("skipSilence", d.skipSilence),
            resumeAfterCall = prefs.getBoolean("resumeAfterCall", d.resumeAfterCall),
            sleepDefaultMinutes = prefs.getInt("sleepDefaultMinutes", d.sleepDefaultMinutes),
            sleepFadeOut = prefs.getBoolean("sleepFadeOut", d.sleepFadeOut),
            sleepShakeToExtend = prefs.getBoolean("sleepShakeToExtend", d.sleepShakeToExtend),
            sleepBookmark = prefs.getBoolean("sleepBookmark", d.sleepBookmark),
            sleepAuto = prefs.getBoolean("sleepAuto", d.sleepAuto),
            sleepAutoFromHour = prefs.getInt("sleepAutoFromHour", d.sleepAutoFromHour),
            sleepAutoToHour = prefs.getInt("sleepAutoToHour", d.sleepAutoToHour),
            theme = enumOr(prefs.getString("theme", null), d.theme),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            libraryGrid = prefs.getBoolean("libraryGrid", d.libraryGrid),
            librarySort = enumOr(prefs.getString("librarySort", null), d.librarySort),
            libraryFilter = enumOr(prefs.getString("libraryFilter", null), d.libraryFilter),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            autoRescan = prefs.getBoolean("autoRescan", d.autoRescan),
            showRemainingWithSpeed = prefs.getBoolean("showRemainingWithSpeed", d.showRemainingWithSpeed),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
        )
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putInt("rewindSeconds", s.rewindSeconds)
            .putInt("forwardSeconds", s.forwardSeconds)
            .putBoolean("smartRewind", s.smartRewind)
            .putFloat("defaultSpeed", s.defaultSpeed)
            .putBoolean("headsetSkipsChapters", s.headsetSkipsChapters)
            .putBoolean("skipSilence", s.skipSilence)
            .putBoolean("resumeAfterCall", s.resumeAfterCall)
            .putInt("sleepDefaultMinutes", s.sleepDefaultMinutes)
            .putBoolean("sleepFadeOut", s.sleepFadeOut)
            .putBoolean("sleepShakeToExtend", s.sleepShakeToExtend)
            .putBoolean("sleepBookmark", s.sleepBookmark)
            .putBoolean("sleepAuto", s.sleepAuto)
            .putInt("sleepAutoFromHour", s.sleepAutoFromHour)
            .putInt("sleepAutoToHour", s.sleepAutoToHour)
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("libraryGrid", s.libraryGrid)
            .putString("librarySort", s.librarySort.name)
            .putString("libraryFilter", s.libraryFilter.name)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("autoRescan", s.autoRescan)
            .putBoolean("showRemainingWithSpeed", s.showRemainingWithSpeed)
            .putBoolean("onboardingDone", s.onboardingDone)
            .apply()
    }

    /** All settings as JSON for the backup file. */
    fun toJson(): JSONObject {
        val json = JSONObject()
        for ((key, value) in prefs.all) {
            if (value != null) json.put(key, value)
        }
        return json
    }

    fun restoreJson(json: JSONObject) {
        val editor = prefs.edit()
        for (key in json.keys()) {
            when (val v = json.get(key)) {
                is Boolean -> editor.putBoolean(key, v)
                // JSON writes 1.0f as "1", so the type has to come from the key.
                is Number -> if (key in FLOAT_KEYS) editor.putFloat(key, v.toFloat()) else editor.putInt(key, v.toInt())
                is String -> editor.putString(key, v)
            }
        }
        editor.apply()
        _settings.value = read()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    private companion object {
        val FLOAT_KEYS = setOf("defaultSpeed")
    }
}
