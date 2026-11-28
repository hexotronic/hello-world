package com.hexotronic.fluxplayer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(
    val uri: String,
    val title: String,
    val position: Long,
    val duration: Long,
    val lastPlayed: Long
) {
    val progress: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** Verlauf mit Wiedergabepositionen (für „Fortsetzen“). */
class HistoryStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("flux_history", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<HistoryEntry> {
        val raw = sp.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    o.getString("u"), o.optString("t"), o.optLong("p"), o.optLong("d"), o.optLong("l")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun get(uri: String): HistoryEntry? = all().firstOrNull { it.uri == uri }

    @Synchronized
    fun save(uri: String, title: String, position: Long, duration: Long) {
        val entry = HistoryEntry(uri, title, position, duration, System.currentTimeMillis())
        write(listOf(entry) + all().filter { it.uri != uri })
    }

    @Synchronized
    fun remove(uri: String) = write(all().filter { it.uri != uri })

    @Synchronized
    fun clear() = sp.edit().remove(KEY).apply()

    private fun write(list: List<HistoryEntry>) {
        val arr = JSONArray()
        for (e in list.take(MAX)) {
            arr.put(
                JSONObject().put("u", e.uri).put("t", e.title).put("p", e.position)
                    .put("d", e.duration).put("l", e.lastPlayed)
            )
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX = 300
    }
}
