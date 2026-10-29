package io.github.hexotronic.lauscher.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.audio.SoundState
import io.github.hexotronic.lauscher.audio.dsp.EnhancerPreset
import io.github.hexotronic.lauscher.audio.dsp.EnhancerSettings
import io.github.hexotronic.lauscher.data.AppSettings
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.LibraryFilter
import io.github.hexotronic.lauscher.data.LibrarySort
import io.github.hexotronic.lauscher.playback.AudiobookPlayerCore
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.playback.PlaybackHub
import io.github.hexotronic.lauscher.playback.PlaybackService
import io.github.hexotronic.lauscher.playback.SleepState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface Screen {
    data object Library : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Player : Screen
    data object SoundStudio : Screen
    data class BookDetail(val bookId: Long) : Screen
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LauscherApp
    val repository = app.repository
    val scanner = app.scanner
    private val settingsStore = app.settings

    // ---------------------------------------------------------------- navigation

    val backStack = mutableStateListOf<Screen>(Screen.Library)
    val current: Screen get() = backStack.last()

    fun navigate(screen: Screen) {
        if (current == screen) return
        if (screen == Screen.Library || screen == Screen.Stats || screen == Screen.Settings) {
            backStack.clear()
            backStack.add(screen)
        } else {
            backStack.add(screen)
        }
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun toast(text: String) {
        _messages.tryEmit(text)
    }

    // ---------------------------------------------------------------- state

    val settings: StateFlow<AppSettings> = settingsStore.settings

    val searchQuery = MutableStateFlow("")

    val allBooks: StateFlow<List<Book>> =
        repository.books.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryBooks: StateFlow<List<Book>> = combine(allBooks, settings, searchQuery) { books, s, query ->
        val q = query.trim().lowercase()
        books
            .filter { b ->
                q.isEmpty() || b.title.lowercase().contains(q) || b.author?.lowercase()?.contains(q) == true ||
                    b.narrator?.lowercase()?.contains(q) == true
            }
            .filter { b ->
                when (s.libraryFilter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.IN_PROGRESS -> b.isStarted
                    LibraryFilter.NEW -> !b.isStarted && !b.finished
                    LibraryFilter.FINISHED -> b.finished
                }
            }
            .let { list ->
                when (s.librarySort) {
                    LibrarySort.RECENT -> list.sortedWith(compareByDescending<Book> { it.lastPlayedAt }.thenByDescending { it.addedAt })
                    LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
                    LibrarySort.AUTHOR -> list.sortedWith(compareBy<Book> { it.author?.lowercase() ?: "￿" }.thenBy { it.title.lowercase() })
                    LibrarySort.ADDED -> list.sortedByDescending { it.addedAt }
                    LibrarySort.PROGRESS -> list.sortedByDescending { it.progress }
                    LibrarySort.LENGTH -> list.sortedByDescending { it.durationMs }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders = repository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats = repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scanState = scanner.state
    val sound: StateFlow<SoundState> = app.sound.state

    val core: StateFlow<AudiobookPlayerCore?> = PlaybackHub.core

    val nowPlaying: StateFlow<NowPlaying> = core
        .flatMapLatest { it?.state ?: flowOf(NowPlaying()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlaying())

    val sleep: StateFlow<SleepState> = core
        .flatMapLatest { it?.sleep ?: flowOf(SleepState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepState())

    /** The book shown in the mini player when nothing is loaded yet. */
    val lastBook: StateFlow<Book?> = allBooks.combine(nowPlaying) { books, np ->
        np.book ?: books.filter { it.lastPlayedAt > 0 }.maxByOrNull { it.lastPlayedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------------------------------------------------------------- player connection

    private var controllerFuture: ListenableFuture<MediaController>? = null

    /** Binding a MediaController starts the playback service (and therefore the player core). */
    fun connect() {
        if (controllerFuture != null) return
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    fun disconnect() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private suspend fun awaitCore(): AudiobookPlayerCore? {
        connect()
        return withTimeoutOrNull(8_000) { PlaybackHub.core.filterNotNull().first() }
    }

    fun withCore(block: suspend (AudiobookPlayerCore) -> Unit) {
        viewModelScope.launch {
            val c = awaitCore()
            if (c == null) toast("Wiedergabedienst nicht erreichbar") else block(c)
        }
    }

    fun playBook(bookId: Long, openPlayer: Boolean = true, startAtMs: Long? = null) {
        withCore { c ->
            val current = c.state.value.book?.id
            if (current == bookId && startAtMs == null) {
                if (!c.exoPlayer.playWhenReady) c.play()
            } else if (!c.loadBook(bookId, play = true, startAtMs = startAtMs)) {
                toast("Hörbuch konnte nicht geladen werden")
            }
        }
        if (openPlayer) navigate(Screen.Player)
    }

    fun togglePlayPause() {
        val np = nowPlaying.value
        if (np.book == null) {
            lastBook.value?.let { playBook(it.id, openPlayer = false) }
        } else {
            withCore { it.togglePlayPause() }
        }
    }

    // ---------------------------------------------------------------- library actions

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            scanner.addFolder(uri)
            reportScan()
        }
    }

    fun addFiles(uris: List<Uri>, play: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val id = scanner.addFiles(uris)
            if (id == null) toast("Datei konnte nicht gelesen werden")
            else if (play) playBook(id)
            else toast(if (uris.size == 1) "Hörbuch hinzugefügt" else "${uris.size} Hörbücher hinzugefügt")
        }
    }

    fun rescan(force: Boolean = false) {
        viewModelScope.launch {
            scanner.scanAll(force)
            reportScan()
        }
    }

    private fun reportScan() {
        val s = scanner.state.value
        if (s is io.github.hexotronic.lauscher.scan.ScanState.Finished) {
            val text = buildString {
                append("${s.books} Hörbücher")
                if (s.added > 0) append(", ${s.added} neu")
                if (s.errors > 0) append(", ${s.errors} Fehler")
            }
            toast(text)
            scanner.clearFinishedState()
        }
    }

    fun removeFolder(uri: String) {
        viewModelScope.launch { scanner.removeFolder(uri) }
    }

    fun setFinished(book: Book, finished: Boolean) {
        viewModelScope.launch { repository.setFinished(book.id, finished) }
    }

    fun resetProgress(book: Book) {
        viewModelScope.launch { repository.resetProgress(book.id) }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            if (nowPlaying.value.book?.id == book.id) core.value?.pause()
            repository.deleteBook(book.id)
            if (current is Screen.BookDetail) back()
            toast("„${book.title}“ entfernt")
        }
    }

    fun deleteMissing() {
        viewModelScope.launch {
            val n = repository.deleteMissingBooks()
            toast(if (n == 0) "Keine fehlenden Hörbücher" else "$n fehlende Hörbücher entfernt")
        }
    }

    fun setCover(book: Book, image: Uri) {
        viewModelScope.launch {
            if (!scanner.setCustomCover(book.id, book.sourceKey, image)) toast("Bild konnte nicht gelesen werden")
        }
    }

    fun updateInfo(book: Book, title: String, author: String?, narrator: String?) {
        viewModelScope.launch {
            repository.updateInfo(book.id, title.ifBlank { book.title }, author?.ifBlank { null }, narrator?.ifBlank { null })
        }
    }

    // ---------------------------------------------------------------- bookmarks

    fun addBookmark(title: String? = null, note: String = "") {
        withCore { c ->
            if (c.addBookmark(title, note) != null) toast("Lesezeichen gesetzt")
        }
    }

    fun updateBookmark(id: Long, title: String, note: String) {
        viewModelScope.launch { repository.updateBookmark(id, title, note) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { repository.deleteBookmark(id) }
    }

    // ---------------------------------------------------------------- sound

    fun updateSound(transform: (EnhancerSettings) -> EnhancerSettings) = app.sound.update(transform)

    fun applyPreset(preset: EnhancerPreset) = app.sound.update { preset.settings.copy(enabled = true) }

    fun setSoundPerBook(enabled: Boolean) = app.sound.setPerBook(enabled)

    fun setBypass(bypass: Boolean) = app.sound.setBypass(bypass)

    // ---------------------------------------------------------------- backup

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { app.backup.export(uri) }
                .onSuccess { toast("Sicherung gespeichert") }
                .onFailure { toast("Sicherung fehlgeschlagen: ${it.message}") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching { app.backup.import(uri) }
                .onSuccess { toast("Sicherung eingespielt ($it Hörbücher)") }
                .onFailure { toast("Import fehlgeschlagen: ${it.message}") }
        }
    }

    fun clearStatistics() {
        viewModelScope.launch {
            repository.clearStatistics()
            toast("Statistik zurückgesetzt")
        }
    }

    override fun onCleared() {
        disconnect()
    }
}
