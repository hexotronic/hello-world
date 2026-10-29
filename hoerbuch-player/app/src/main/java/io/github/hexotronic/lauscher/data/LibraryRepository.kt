package io.github.hexotronic.lauscher.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

private class LauscherDatabase(context: Context) : SQLiteOpenHelper(context, "lauscher.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE books(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_key TEXT NOT NULL UNIQUE,
                root_uri TEXT NOT NULL,
                title TEXT NOT NULL,
                author TEXT,
                narrator TEXT,
                year TEXT,
                description TEXT,
                cover_path TEXT,
                custom_cover INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                speed REAL NOT NULL DEFAULT 0,
                finished INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                last_played_at INTEGER NOT NULL DEFAULT 0,
                signature TEXT NOT NULL DEFAULT '',
                file_count INTEGER NOT NULL DEFAULT 0,
                audio_settings TEXT,
                missing INTEGER NOT NULL DEFAULT 0)""",
        )
        db.execSQL(
            """CREATE TABLE tracks(
                book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                idx INTEGER NOT NULL,
                uri TEXT NOT NULL,
                title TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                start_ms INTEGER NOT NULL,
                PRIMARY KEY(book_id, idx))""",
        )
        db.execSQL(
            """CREATE TABLE chapters(
                book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                idx INTEGER NOT NULL,
                title TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                PRIMARY KEY(book_id, idx))""",
        )
        db.execSQL(
            """CREATE TABLE bookmarks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL REFERENCES books(id) ON DELETE CASCADE,
                position_ms INTEGER NOT NULL,
                title TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL)""",
        )
        // No foreign key: listening statistics survive when a book is removed.
        db.execSQL(
            """CREATE TABLE listening_log(
                day TEXT NOT NULL,
                book_id INTEGER NOT NULL,
                book_title TEXT NOT NULL DEFAULT '',
                listened_ms INTEGER NOT NULL,
                PRIMARY KEY(day, book_id))""",
        )
        db.execSQL("CREATE TABLE folders(uri TEXT PRIMARY KEY, name TEXT NOT NULL, added_at INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/** Single source of truth for books, chapters, bookmarks, folders and statistics. */
class LibraryRepository(context: Context) {
    private val helper = LauscherDatabase(context)
    private val db: SQLiteDatabase by lazy { helper.writableDatabase }
    private val changes = MutableStateFlow(0L)

    private fun notifyChanged() {
        changes.value = changes.value + 1
    }

    private fun <T> observe(query: () -> T): Flow<T> =
        changes.map { query() }.flowOn(Dispatchers.IO).conflate()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    // ---------------------------------------------------------------- books

    val books: Flow<List<Book>> = observe { queryBooks("missing = 0") }

    fun observeBook(id: Long): Flow<BookDetails?> = observe { details(id) }

    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = observe { bookmarks(bookId) }

    val folders: Flow<List<LibraryFolder>> = observe { queryFolders() }

    val stats: Flow<Stats> = observe { queryStats() }

    suspend fun book(id: Long): Book? = io { queryBooks("id = ?", arrayOf(id.toString())).firstOrNull() }

    suspend fun bookDetails(id: Long): BookDetails? = io { details(id) }

    suspend fun bookBySourceKey(key: String): Book? =
        io { queryBooks("source_key = ?", arrayOf(key)).firstOrNull() }

    suspend fun allBooksIncludingMissing(): List<Book> = io { queryBooks(null) }

    suspend fun lastPlayedBook(): Book? = io {
        queryBooks("missing = 0 AND last_played_at > 0", orderBy = "last_played_at DESC").firstOrNull()
    }

    suspend fun recentBooks(limit: Int): List<Book> = io {
        queryBooks("missing = 0", orderBy = "last_played_at DESC, added_at DESC").take(limit)
    }

    private fun details(id: Long): BookDetails? {
        val book = queryBooks("id = ?", arrayOf(id.toString())).firstOrNull() ?: return null
        val tracks = db.query("tracks", null, "book_id = ?", arrayOf(id.toString()), null, null, "idx").use { c ->
            c.map {
                Track(
                    bookId = id,
                    index = c.int("idx"),
                    uri = c.str("uri")!!,
                    title = c.str("title")!!,
                    durationMs = c.long("duration_ms"),
                    startMs = c.long("start_ms"),
                )
            }
        }
        val chapters = db.query("chapters", null, "book_id = ?", arrayOf(id.toString()), null, null, "idx").use { c ->
            c.map {
                Chapter(id, c.int("idx"), c.str("title")!!, c.long("start_ms"), c.long("end_ms"))
            }
        }
        return BookDetails(book, tracks, chapters)
    }

    private fun queryBooks(
        selection: String?,
        args: Array<String>? = null,
        orderBy: String = "title COLLATE NOCASE",
    ): List<Book> = db.query("books", null, selection, args, null, null, orderBy).use { c ->
        c.map {
            Book(
                id = c.long("id"),
                sourceKey = c.str("source_key")!!,
                rootUri = c.str("root_uri")!!,
                title = c.str("title")!!,
                author = c.str("author"),
                narrator = c.str("narrator"),
                year = c.str("year"),
                description = c.str("description"),
                coverPath = c.str("cover_path"),
                customCover = c.int("custom_cover") != 0,
                durationMs = c.long("duration_ms"),
                positionMs = c.long("position_ms"),
                speed = c.getFloat(c.getColumnIndexOrThrow("speed")),
                finished = c.int("finished") != 0,
                addedAt = c.long("added_at"),
                lastPlayedAt = c.long("last_played_at"),
                signature = c.str("signature").orEmpty(),
                fileCount = c.int("file_count"),
                audioSettingsJson = c.str("audio_settings"),
                missing = c.int("missing") != 0,
            )
        }
    }

    /** Inserts or updates a scanned book while keeping progress, bookmarks and settings. */
    suspend fun saveScannedBook(
        sourceKey: String,
        rootUri: String,
        title: String,
        author: String?,
        narrator: String?,
        year: String?,
        description: String?,
        coverPath: String?,
        signature: String,
        tracks: List<Track>,
        chapters: List<Chapter>,
    ): Long = io {
        val duration = tracks.sumOf { it.durationMs }
        db.beginTransaction()
        try {
            val existing = queryBooks("source_key = ?", arrayOf(sourceKey)).firstOrNull()
            val values = ContentValues().apply {
                put("root_uri", rootUri)
                put("title", title)
                put("author", author)
                put("narrator", narrator)
                put("year", year)
                put("description", description)
                put("duration_ms", duration)
                put("signature", signature)
                put("file_count", tracks.size)
                put("missing", 0)
                if (existing == null || !existing.customCover) put("cover_path", coverPath)
            }
            val id = if (existing == null) {
                values.put("source_key", sourceKey)
                values.put("added_at", System.currentTimeMillis())
                db.insertOrThrow("books", null, values)
            } else {
                if (existing.positionMs > duration) values.put("position_ms", duration)
                db.update("books", values, "id = ?", arrayOf(existing.id.toString()))
                existing.id
            }
            db.delete("tracks", "book_id = ?", arrayOf(id.toString()))
            db.delete("chapters", "book_id = ?", arrayOf(id.toString()))
            for (t in tracks) {
                db.insertOrThrow("tracks", null, ContentValues().apply {
                    put("book_id", id)
                    put("idx", t.index)
                    put("uri", t.uri)
                    put("title", t.title)
                    put("duration_ms", t.durationMs)
                    put("start_ms", t.startMs)
                })
            }
            for (ch in chapters) {
                db.insertOrThrow("chapters", null, ContentValues().apply {
                    put("book_id", id)
                    put("idx", ch.index)
                    put("title", ch.title)
                    put("start_ms", ch.startMs)
                    put("end_ms", ch.endMs)
                })
            }
            db.setTransactionSuccessful()
            id
        } finally {
            db.endTransaction()
        }
    }.also { notifyChanged() }

    suspend fun markMissing(rootUri: String, presentKeys: Set<String>) = io {
        val books = queryBooks("root_uri = ?", arrayOf(rootUri))
        for (b in books) {
            val missing = b.sourceKey !in presentKeys
            if (missing != b.missing) {
                db.update("books", ContentValues().apply { put("missing", if (missing) 1 else 0) }, "id = ?", arrayOf(b.id.toString()))
            }
        }
    }.also { notifyChanged() }

    suspend fun touchBook(sourceKey: String) = io {
        db.update("books", ContentValues().apply { put("missing", 0) }, "source_key = ?", arrayOf(sourceKey))
    }

    suspend fun savePosition(bookId: Long, positionMs: Long, played: Boolean) = io {
        db.update("books", ContentValues().apply {
            put("position_ms", positionMs.coerceAtLeast(0))
            if (played) put("last_played_at", System.currentTimeMillis())
        }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun setFinished(bookId: Long, finished: Boolean) = io {
        val book = queryBooks("id = ?", arrayOf(bookId.toString())).firstOrNull() ?: return@io
        db.update("books", ContentValues().apply {
            put("finished", if (finished) 1 else 0)
            put("position_ms", if (finished) book.durationMs else 0L)
        }, "id = ?", arrayOf(bookId.toString()))
        Unit
    }.also { notifyChanged() }

    suspend fun resetProgress(bookId: Long) = io {
        db.update("books", ContentValues().apply {
            put("finished", 0)
            put("position_ms", 0L)
        }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun setSpeed(bookId: Long, speed: Float) = io {
        db.update("books", ContentValues().apply { put("speed", speed) }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun setAudioSettings(bookId: Long, json: String?) = io {
        db.update("books", ContentValues().apply { put("audio_settings", json) }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun setCover(bookId: Long, path: String?, custom: Boolean) = io {
        db.update("books", ContentValues().apply {
            put("cover_path", path)
            put("custom_cover", if (custom) 1 else 0)
        }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun updateInfo(bookId: Long, title: String, author: String?, narrator: String?) = io {
        db.update("books", ContentValues().apply {
            put("title", title)
            put("author", author)
            put("narrator", narrator)
        }, "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    /** Replaces an estimated track length by the real one and shifts everything after it. */
    suspend fun fixTrackDuration(bookId: Long, index: Int, oldDurationMs: Long, newDurationMs: Long) = io {
        val delta = newDurationMs - oldDurationMs
        val id = bookId.toString()
        db.beginTransaction()
        try {
            val oldEnd = db.rawQuery("SELECT start_ms FROM tracks WHERE book_id = ? AND idx = ?", arrayOf(id, index.toString()))
                .use { c -> if (c.moveToFirst()) c.getLong(0) + oldDurationMs else null }
            if (oldEnd != null) {
                db.execSQL("UPDATE tracks SET duration_ms = ? WHERE book_id = ? AND idx = ?", arrayOf<Any>(newDurationMs, bookId, index))
                db.execSQL("UPDATE tracks SET start_ms = start_ms + ? WHERE book_id = ? AND idx > ?", arrayOf<Any>(delta, bookId, index))
                db.execSQL("UPDATE chapters SET start_ms = start_ms + ? WHERE book_id = ? AND start_ms >= ?", arrayOf<Any>(delta, bookId, oldEnd))
                db.execSQL("UPDATE chapters SET end_ms = end_ms + ? WHERE book_id = ? AND end_ms >= ?", arrayOf<Any>(delta, bookId, oldEnd))
                db.execSQL("UPDATE books SET duration_ms = duration_ms + ? WHERE id = ?", arrayOf<Any>(delta, bookId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }.also { notifyChanged() }

    suspend fun deleteBook(bookId: Long) = io {
        db.delete("books", "id = ?", arrayOf(bookId.toString()))
    }.also { notifyChanged() }

    suspend fun deleteMissingBooks(): Int = io {
        db.delete("books", "missing = 1", null)
    }.also { notifyChanged() }

    // ---------------------------------------------------------------- bookmarks

    private fun bookmarks(bookId: Long): List<Bookmark> =
        db.query("bookmarks", null, "book_id = ?", arrayOf(bookId.toString()), null, null, "position_ms").use { c ->
            c.map {
                Bookmark(c.long("id"), bookId, c.long("position_ms"), c.str("title")!!, c.str("note").orEmpty(), c.long("created_at"))
            }
        }

    suspend fun allBookmarks(bookId: Long): List<Bookmark> = io { bookmarks(bookId) }

    suspend fun addBookmark(bookId: Long, positionMs: Long, title: String, note: String = "", createdAt: Long = System.currentTimeMillis()): Long = io {
        db.insertOrThrow("bookmarks", null, ContentValues().apply {
            put("book_id", bookId)
            put("position_ms", positionMs)
            put("title", title)
            put("note", note)
            put("created_at", createdAt)
        })
    }.also { notifyChanged() }

    suspend fun updateBookmark(id: Long, title: String, note: String) = io {
        db.update("bookmarks", ContentValues().apply {
            put("title", title)
            put("note", note)
        }, "id = ?", arrayOf(id.toString()))
    }.also { notifyChanged() }

    suspend fun deleteBookmark(id: Long) = io {
        db.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }.also { notifyChanged() }

    // ---------------------------------------------------------------- folders

    private fun queryFolders(): List<LibraryFolder> =
        db.query("folders", null, null, null, null, null, "name COLLATE NOCASE").use { c ->
            c.map { LibraryFolder(c.str("uri")!!, c.str("name")!!, c.long("added_at")) }
        }

    suspend fun folderList(): List<LibraryFolder> = io { queryFolders() }

    suspend fun addFolder(uri: String, name: String) = io {
        db.insertWithOnConflict("folders", null, ContentValues().apply {
            put("uri", uri)
            put("name", name)
            put("added_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }.also { notifyChanged() }

    /** Removes the folder and all books that were found in it. */
    suspend fun removeFolder(uri: String) = io {
        db.beginTransaction()
        try {
            db.delete("books", "root_uri = ?", arrayOf(uri))
            db.delete("folders", "uri = ?", arrayOf(uri))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }.also { notifyChanged() }

    // ---------------------------------------------------------------- statistics

    suspend fun addListening(bookId: Long, bookTitle: String, ms: Long, day: LocalDate = LocalDate.now()) = io {
        if (ms <= 0) return@io
        val key = day.toString()
        // Android 8 ships SQLite without UPSERT, so update first and insert if nothing changed.
        db.beginTransaction()
        try {
            val previous = db.rawQuery(
                "SELECT listened_ms FROM listening_log WHERE day = ? AND book_id = ?",
                arrayOf(key, bookId.toString()),
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            val values = ContentValues().apply {
                put("day", key)
                put("book_id", bookId)
                put("book_title", bookTitle)
                put("listened_ms", (previous ?: 0L) + ms)
            }
            db.insertWithOnConflict("listening_log", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }.also { notifyChanged() }

    private fun queryStats(): Stats {
        val days = db.rawQuery(
            "SELECT day, SUM(listened_ms) AS ms FROM listening_log GROUP BY day ORDER BY day",
            null,
        ).use { c -> c.map { DayStat(c.str("day")!!, c.long("ms")) } }
        val perBook = db.rawQuery(
            "SELECT book_id, MAX(book_title) AS title, SUM(listened_ms) AS ms FROM listening_log " +
                "GROUP BY book_id ORDER BY ms DESC",
            null,
        ).use { c -> c.map { BookStat(c.long("book_id"), c.str("title").orEmpty(), c.long("ms")) } }
        val finished = db.rawQuery("SELECT COUNT(*) FROM books WHERE finished = 1", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        return Stats(days, perBook, days.sumOf { it.listenedMs }, finished)
    }

    suspend fun allListening(): List<Triple<String, Long, Long>> = io {
        db.rawQuery("SELECT day, book_id, listened_ms FROM listening_log", null).use { c ->
            c.map { Triple(c.str("day")!!, c.long("book_id"), c.long("listened_ms")) }
        }
    }

    suspend fun clearStatistics() = io {
        db.delete("listening_log", null, null)
    }.also { notifyChanged() }

    // ---------------------------------------------------------------- cursor helpers

    private inline fun <T> Cursor.map(row: (Cursor) -> T): List<T> {
        val out = ArrayList<T>(count)
        while (moveToNext()) out.add(row(this))
        return out
    }

    private fun Cursor.str(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
}
