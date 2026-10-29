package io.github.hexotronic.lauscher.data

data class Book(
    val id: Long,
    val sourceKey: String,
    val rootUri: String,
    val title: String,
    val author: String?,
    val narrator: String?,
    val year: String?,
    val description: String?,
    val coverPath: String?,
    val customCover: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** 0 means "use the default speed from the settings". */
    val speed: Float,
    val finished: Boolean,
    val addedAt: Long,
    val lastPlayedAt: Long,
    val signature: String,
    val fileCount: Int,
    val audioSettingsJson: String?,
    val missing: Boolean,
) {
    val progress: Float
        get() = if (finished) 1f else if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long get() = if (finished) 0 else (durationMs - positionMs).coerceAtLeast(0)

    val isStarted: Boolean get() = !finished && (positionMs > 0 || lastPlayedAt > 0)
}

data class Track(
    val bookId: Long,
    val index: Int,
    val uri: String,
    val title: String,
    val durationMs: Long,
    val startMs: Long,
) {
    val endMs: Long get() = startMs + durationMs
}

data class Chapter(
    val bookId: Long,
    val index: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

data class Bookmark(
    val id: Long,
    val bookId: Long,
    val positionMs: Long,
    val title: String,
    val note: String,
    val createdAt: Long,
)

data class LibraryFolder(val uri: String, val name: String, val addedAt: Long)

data class BookDetails(val book: Book, val tracks: List<Track>, val chapters: List<Chapter>)

data class DayStat(val day: String, val listenedMs: Long)

data class BookStat(val bookId: Long, val title: String, val listenedMs: Long)

data class Stats(
    val days: List<DayStat>,
    val perBook: List<BookStat>,
    val totalMs: Long,
    val finishedBooks: Int,
)

enum class LibrarySort(val label: String) {
    RECENT("Zuletzt gehört"),
    TITLE("Titel"),
    AUTHOR("Autor"),
    ADDED("Neu hinzugefügt"),
    PROGRESS("Fortschritt"),
    LENGTH("Länge"),
}

enum class LibraryFilter(val label: String) {
    ALL("Alle"),
    IN_PROGRESS("Angefangen"),
    NEW("Neu"),
    FINISHED("Beendet"),
}
