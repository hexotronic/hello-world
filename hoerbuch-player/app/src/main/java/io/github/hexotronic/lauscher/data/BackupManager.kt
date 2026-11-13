package io.github.hexotronic.lauscher.data

import android.content.Context
import android.net.Uri
import io.github.hexotronic.lauscher.audio.SoundSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Exports progress, bookmarks, statistics and settings to a JSON file and restores it,
 * e.g. when moving to a new phone. Books are matched by their storage location, then by title.
 */
class BackupManager(
    private val context: Context,
    private val repo: LibraryRepository,
    private val settings: SettingsStore,
    private val sound: SoundSettingsManager,
) {
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val books = repo.allBooksIncludingMissing()
        val byId = books.associateBy { it.id }
        val json = JSONObject()
        json.put("app", "Lauscher")
        json.put("version", 1)
        json.put("exportedAt", System.currentTimeMillis())
        json.put("settings", settings.toJson())
        json.put("sound", JSONObject(sound.globalJson()))
        json.put("books", JSONArray().apply {
            for (b in books) {
                put(JSONObject().apply {
                    put("sourceKey", b.sourceKey)
                    put("title", b.title)
                    put("author", b.author ?: JSONObject.NULL)
                    put("positionMs", b.positionMs)
                    put("speed", b.speed.toDouble())
                    put("finished", b.finished)
                    put("lastPlayedAt", b.lastPlayedAt)
                    put("audioSettings", b.audioSettingsJson ?: JSONObject.NULL)
                    put("bookmarks", JSONArray().apply {
                        for (m in repo.allBookmarks(b.id)) {
                            put(JSONObject().apply {
                                put("positionMs", m.positionMs)
                                put("title", m.title)
                                put("note", m.note)
                                put("createdAt", m.createdAt)
                            })
                        }
                    })
                })
            }
        })
        json.put("listening", JSONArray().apply {
            for ((day, bookId, ms) in repo.allListening()) {
                val b = byId[bookId]
                put(JSONObject().apply {
                    put("day", day)
                    put("sourceKey", b?.sourceKey ?: JSONObject.NULL)
                    put("title", b?.title ?: "")
                    put("ms", ms)
                })
            }
        })
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toString(2).toByteArray()) }
            ?: error("Datei konnte nicht geschrieben werden")
    }

    /** Returns the number of books whose progress was restored. */
    suspend fun import(source: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Datei konnte nicht gelesen werden")
        val json = JSONObject(text)
        require(json.optString("app") == "Lauscher") { "Keine Lauscher-Sicherung" }

        json.optJSONObject("settings")?.let { s -> withContext(Dispatchers.Main) { settings.restoreJson(s) } }
        json.optJSONObject("sound")?.let { s -> withContext(Dispatchers.Main) { sound.restoreGlobal(s.toString()) } }

        val books = repo.allBooksIncludingMissing()
        fun match(sourceKey: String?, title: String?): Book? =
            books.firstOrNull { it.sourceKey == sourceKey } ?: books.firstOrNull { it.title.equals(title, ignoreCase = true) }

        var restored = 0
        val list = json.optJSONArray("books") ?: JSONArray()
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            val book = match(o.optString("sourceKey"), o.optString("title")) ?: continue
            restored++
            if (o.optBoolean("finished")) {
                repo.setFinished(book.id, true)
            } else {
                repo.resetProgress(book.id)
                repo.savePosition(book.id, o.optLong("positionMs"), played = false)
            }
            val speed = o.optDouble("speed", 0.0).toFloat()
            if (speed > 0) repo.setSpeed(book.id, speed)
            if (!o.isNull("audioSettings")) repo.setAudioSettings(book.id, o.optString("audioSettings"))
            val existing = repo.allBookmarks(book.id).map { it.positionMs }.toSet()
            val marks = o.optJSONArray("bookmarks") ?: JSONArray()
            for (j in 0 until marks.length()) {
                val m = marks.getJSONObject(j)
                val pos = m.optLong("positionMs")
                if (pos in existing) continue
                repo.addBookmark(book.id, pos, m.optString("title"), m.optString("note"), m.optLong("createdAt", System.currentTimeMillis()))
            }
        }

        val listening = json.optJSONArray("listening") ?: JSONArray()
        if (listening.length() > 0) {
            repo.clearStatistics()
            for (i in 0 until listening.length()) {
                val o = listening.getJSONObject(i)
                val book = match(o.optString("sourceKey"), o.optString("title"))
                val day = runCatching { LocalDate.parse(o.getString("day")) }.getOrNull() ?: continue
                repo.addListening(book?.id ?: -(i + 1L), book?.title ?: o.optString("title"), o.optLong("ms"), day)
            }
        }
        restored
    }
}
