package io.github.hexotronic.lauscher.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.download.DownloadErrors
import io.github.hexotronic.lauscher.download.Listing
import io.github.hexotronic.lauscher.download.MediaSources
import io.github.hexotronic.lauscher.download.Source
import io.github.hexotronic.lauscher.download.core.ArchiveOrg
import io.github.hexotronic.lauscher.download.core.DownloadPlan
import io.github.hexotronic.lauscher.download.core.Grouping
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.download.core.OutputFormat
import io.github.hexotronic.lauscher.download.core.PlannedBook
import io.github.hexotronic.lauscher.download.Net
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** State of the download screen: LibriVox search, video search/links with selection, options. */
class DownloadBrowser(private val app: LauscherApp, private val scope: CoroutineScope, private val toast: (String) -> Unit) {
    enum class Tab(val label: String) {
        BOOKS("Hörbücher"),
        MEDIA("Videos & Links"),
        FILES("Vom Handy"),
    }

    var tab by mutableStateOf(Tab.BOOKS)
    val jobs = app.downloads.jobs
    /** Where new downloads are saved, shown on the screen. */
    var location by mutableStateOf(app.downloads.location)
        private set
    var customFolder by mutableStateOf(app.downloads.customFolder)
        private set

    /** Saves into the folder the listener picked from now on. */
    fun chooseFolder(tree: Uri) {
        runCatching { app.downloads.setFolder(tree) }
            .onSuccess { toast("Downloads landen jetzt in: ${app.downloads.location}") }
            .onFailure { toast("Dieser Ordner kann nicht verwendet werden: ${it.message}") }
        location = app.downloads.location
        customFolder = app.downloads.customFolder
    }

    fun useDefaultFolder() {
        app.downloads.setFolder(null)
        location = app.downloads.location
        customFolder = false
    }

    // ---------------------------------------------------------------- LibriVox

    var bookQuery by mutableStateOf("")
    var bookLanguage by mutableStateOf(ArchiveOrg.Language.GERMAN)
    var bookResults by mutableStateOf<List<ArchiveOrg.Item>>(emptyList())
        private set
    var bookTotal by mutableStateOf(-1)
        private set
    var bookLoading by mutableStateOf(false)
        private set
    var bookError by mutableStateOf<String?>(null)
        private set
    private var bookPage = 1

    fun searchBooks(more: Boolean = false) {
        if (bookLoading) return
        bookLoading = true
        bookError = null
        val page = if (more) bookPage + 1 else 1
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ArchiveOrg.parseSearch(Net.text(ArchiveOrg.searchUrl(bookQuery, bookLanguage, page))) }
            }.onSuccess { result ->
                bookPage = page
                bookTotal = result.total
                bookResults = if (more) (bookResults + result.items).distinctBy { it.id } else result.items
            }.onFailure {
                bookError = "Suche fehlgeschlagen: ${DownloadErrors.describe(it)}"
            }
            bookLoading = false
        }
    }

    fun downloadBook(item: ArchiveOrg.Item, limit: Int? = null) {
        app.downloads.downloadAudiobook(item, limit)
        toast("Wird geladen: ${item.title}")
    }

    // ---------------------------------------------------------------- videos and links

    var source by mutableStateOf(Source.YOUTUBE)
    var mediaInput by mutableStateOf("")
    var mediaItems by mutableStateOf<List<MediaItem>>(emptyList())
        private set
    var mediaTitle by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var mediaLoading by mutableStateOf(false)
        private set
    var mediaError by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var grouping by mutableStateOf(Grouping.ONE_BOOK)
    var format by mutableStateOf(OutputFormat.MP3)
    var quality by mutableStateOf(Mp3Quality.STANDARD)
    private var more: (() -> Listing)? = null
    /** Name for "one book": the playlist or channel, or the search words. */
    private var collectionTitle: String? = null

    /** Searches the chosen platform, or opens the pasted link(s) (video, playlist, channel, file). */
    fun go() {
        val input = mediaInput.trim()
        if (input.isEmpty() || mediaLoading) return
        val urls = LinkInput.allUrls(input)
        load(reset = true) {
            if (urls.isEmpty()) {
                collectionTitle = input
                MediaSources.search(source, input)
            } else {
                val listings = urls.map { MediaSources.resolve(it) }
                collectionTitle = listings.singleOrNull()?.title
                if (listings.size == 1) {
                    listings[0]
                } else {
                    Listing("${urls.size} Links", listings.flatMap { it.items }, null)
                }
            }
        }
    }

    fun loadMore() {
        val next = more ?: return
        load(reset = false) { next() }
    }

    /** Loads every page of the playlist, channel or search (up to 500 items) and selects all. */
    fun loadAll() {
        val next = more ?: return
        load(reset = false, selectAll = true) {
            MediaSources.loadAll(Listing(null, emptyList(), next))
        }
    }

    private fun load(reset: Boolean, selectAll: Boolean = false, block: () -> Listing) {
        if (mediaLoading) return
        mediaLoading = true
        mediaError = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { listing ->
                    val items = if (reset) listing.items else (mediaItems + listing.items).distinctBy { it.url }
                    mediaItems = items
                    if (reset) {
                        mediaTitle = listing.title
                        // A link to one playlist or video: everything is meant; search results are picked by hand.
                        selected = if (LinkInput.firstUrl(mediaInput) != null) items.map { it.url }.toSet() else emptySet()
                    }
                    if (selectAll) selected = items.map { it.url }.toSet()
                    more = listing.more
                    hasMore = listing.more != null
                }
                .onFailure {
                    mediaError = DownloadErrors.describe(it)
                    if (reset) {
                        mediaItems = emptyList()
                        hasMore = false
                    }
                }
            mediaLoading = false
        }
    }

    fun toggle(item: MediaItem) {
        selected = if (item.url in selected) selected - item.url else selected + item.url
    }

    fun selectAll() {
        selected = mediaItems.map { it.url }.toSet()
    }

    fun selectNone() {
        selected = emptySet()
    }

    fun downloadSelected() {
        val items = mediaItems.filter { it.url in selected }
        if (items.isEmpty()) return
        val books = DownloadPlan.books(items, grouping, collectionTitle)
        app.downloads.downloadMedia(books, format, quality)
        toast(if (books.size == 1) "Wird geladen: ${books[0].title}" else "${books.size} Hörbücher werden geladen")
        selected = emptySet()
    }

    // ---------------------------------------------------------------- files on the phone

    /** Converts picked audio or video files (as one book or each on its own). */
    fun convertFiles(uris: List<Uri>, title: String? = null) {
        if (uris.isEmpty()) return
        val resolver = app.contentResolver
        // Kept readable after a restart, so an interrupted conversion can go on.
        uris.forEach { runCatching { resolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        val items = uris.map { uri ->
            val name = runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "Datei"
            MediaItem(uri.toString(), name)
        }
        val books = DownloadPlan.books(items, grouping, title ?: items.first().title.substringBeforeLast('.'))
            .map { b -> if (b.items.size == 1) b.copy(title = b.title.substringBeforeLast('.')) else b }
        app.downloads.convertFiles(books, format, quality)
        toast(if (items.size == 1) "Wird umgewandelt: ${items[0].title}" else "${items.size} Dateien werden umgewandelt")
    }

    /**
     * Test aid (adb extras, see MainActivity): finds [value] – LibriVox words, a link, or
     * "quelle:suchwörter" – and downloads the first [limit] results, or converts the file [value].
     * With [pauseAfterMs] the download is paused after that time and continued 3 s later.
     */
    fun automate(kind: String, value: String, limit: Int, original: Boolean, pauseAfterMs: Long? = null) {
        val format = if (original) OutputFormat.ORIGINAL else OutputFormat.MP3
        scope.launch {
            runCatching {
                when (kind) {
                    "audiobook" -> {
                        val page = withContext(Dispatchers.IO) {
                            ArchiveOrg.parseSearch(Net.text(ArchiveOrg.searchUrl(value, ArchiveOrg.Language.GERMAN)))
                        }
                        val item = page.items.firstOrNull() ?: error("LibriVox: nichts gefunden für „$value“")
                        Log.i(TAG, "LibriVox: ${page.total} Treffer, lade „${item.title}“")
                        val id = app.downloads.downloadAudiobook(item, limit)
                        if (pauseAfterMs != null) {
                            kotlinx.coroutines.delay(pauseAfterMs)
                            app.downloads.pause(id)
                            kotlinx.coroutines.delay(3_000)
                            app.downloads.resume(id)
                        }
                    }
                    "media" -> {
                        val listing = withContext(Dispatchers.IO) {
                            if (LinkInput.firstUrl(value) != null) {
                                MediaSources.resolve(value)
                            } else {
                                val (name, query) = value.split(':', limit = 2)
                                MediaSources.search(Source.valueOf(name.uppercase()), query)
                            }
                        }
                        Log.i(TAG, "Gefunden (${listing.title}): " + listing.items.take(5).joinToString(" | ") { it.title })
                        val items = listing.items.take(limit).ifEmpty { error("Keine Treffer") }
                        app.downloads.downloadMedia(DownloadPlan.books(items, Grouping.ONE_BOOK, listing.title), format, Mp3Quality.SPEECH)
                    }
                    "convert" -> {
                        // Several paths (comma-separated) become one job with a book for each file.
                        val books = value.split(',').map { path ->
                            val file = java.io.File(path.trim())
                            PlannedBook(file.name.substringBeforeLast('.'), null, listOf(MediaItem(Uri.fromFile(file).toString(), file.name)))
                        }
                        app.downloads.convertFiles(books, format, Mp3Quality.SPEECH)
                    }
                }
            }.onFailure { Log.w(TAG, "Automatischer Download fehlgeschlagen: ${it.message}", it) }
        }
    }

    fun cancel(id: Long) = app.downloads.cancel(id)

    fun pause(id: Long) = app.downloads.pause(id)

    fun resume(id: Long) = app.downloads.resume(id)

    fun pauseAll() = app.downloads.pauseAll()

    fun resumeAll() = app.downloads.resumeAll()

    fun clearFinished() = app.downloads.clearFinished()

    private companion object {
        const val TAG = "LauscherDownload"
    }
}
