package io.github.hexotronic.lauscher.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import io.github.hexotronic.lauscher.analysis.BookTextReader
import io.github.hexotronic.lauscher.analysis.core.BookText
import io.github.hexotronic.lauscher.analysis.core.BookTextParser
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.Setting
import io.github.hexotronic.lauscher.music.MusicSettings
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.LibraryDownload
import io.github.hexotronic.lauscher.music.library.MusicLibrary
import io.github.hexotronic.lauscher.music.library.core.LibraryTrack
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.ModelManager
import io.github.hexotronic.lauscher.analysis.ModelKind
import io.github.hexotronic.lauscher.analysis.ModelInfo
import io.github.hexotronic.lauscher.analysis.AnalysisStatus
import io.github.hexotronic.lauscher.analysis.SpeedCheck
import io.github.hexotronic.lauscher.download.core.LinkInput
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.hexotronic.lauscher.audio.SoundState
import io.github.hexotronic.lauscher.audio.dsp.EnhancerPreset
import io.github.hexotronic.lauscher.audio.dsp.EnhancerSettings
import io.github.hexotronic.lauscher.data.AppSettings
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.LibraryFilter
import io.github.hexotronic.lauscher.data.LibrarySort
import io.github.hexotronic.lauscher.playback.AudiobookPlayerCore
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.playback.PlaybackHub
import io.github.hexotronic.lauscher.playback.PlaybackService
import io.github.hexotronic.lauscher.playback.SleepState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
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

/** Index of the "KI-Analyse" tab in the book details. */
const val BOOK_TAB_ANALYSIS = 4

sealed interface Screen {
    data object Library : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Player : Screen
    data object SoundStudio : Screen
    data object Music : Screen
    data object MusicLibrary : Screen
    data object Downloads : Screen
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

    /** Downloading audiobooks and videos, converting files (see DownloadScreen). */
    val downloads = DownloadBrowser(app, viewModelScope, ::toast)

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
        controllerFuture?.let { future ->
            // Reconnect if the service was stopped in the meantime (e.g. notification swiped away).
            val stale = future.isDone && runCatching { !future.get().isConnected }.getOrDefault(true)
            if (!stale) return
            MediaController.releaseFuture(future)
        }
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

    /** @param quiet only report when something changed (used for the automatic scan at start). */
    fun rescan(force: Boolean = false, quiet: Boolean = false) {
        viewModelScope.launch {
            scanner.scanAll(force)
            reportScan(quiet)
        }
    }

    private fun reportScan(quiet: Boolean = false) {
        val s = scanner.state.value
        if (s is io.github.hexotronic.lauscher.scan.ScanState.Finished) {
            if (quiet && s.added == 0 && s.errors == 0) {
                scanner.clearFinishedState()
                return
            }
            val text = buildString {
                append("${s.books} Hörbücher")
                if (s.added > 0) append(", ${s.added} neu")
                if (s.errors > 0) append(", ${s.errors} Fehler")
            }
            toast(text)
            scanner.clearFinishedState()
        }
    }

    fun createDemo(play: Boolean) {
        viewModelScope.launch {
            val id = io.github.hexotronic.lauscher.scan.DemoBook.create(getApplication(), repository)
            if (play) playBook(id) else toast("Demo-Hörbuch hinzugefügt")
        }
    }

    /** Deep links used by the launcher shortcut / CI screenshots: library, player, studio, stats, settings, detail. */
    fun openScreen(name: String) {
        when (name) {
            "library" -> navigate(Screen.Library)
            "player" -> navigate(Screen.Player)
            "studio" -> navigate(Screen.SoundStudio)
            "stats" -> navigate(Screen.Stats)
            "settings" -> navigate(Screen.Settings)
            "music" -> navigate(Screen.Music)
            "musiclibrary" -> navigate(Screen.MusicLibrary)
            "downloads" -> navigate(Screen.Downloads)
            "feedback" -> {
                openFeedback = true
                navigate(Screen.Player)
            }
            "detail" -> (nowPlaying.value.book ?: lastBook.value)?.let { navigate(Screen.BookDetail(it.id)) }
            "analysis" -> (nowPlaying.value.book ?: lastBook.value)?.let {
                requestedBookTab = BOOK_TAB_ANALYSIS
                navigate(Screen.BookDetail(it.id))
            }
        }
    }

    /** Imports an audio file from the app's own storage (used by tests and power users via adb). */
    fun importLocalFile(path: String, analyze: Boolean) {
        viewModelScope.launch {
            val id = scanner.addFiles(listOf(Uri.fromFile(java.io.File(path))))
            if (id == null) {
                toast("Datei konnte nicht gelesen werden")
                return@launch
            }
            playBook(id)
            if (analyze) {
                kotlinx.coroutines.delay(1500)
                app.analysis.analyze(id)
            }
        }
    }

    /** Test aid: recognises an audio file at 1×, 1.25×, 1.5× and 2× and logs the results. */
    fun speechCheck(path: String) = app.analysis.speechCheck(java.io.File(path), listOf(1f, 1.25f, 1.5f, 2f))

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

    // ---------------------------------------------------------------- background music & local AI

    val musicSettings: StateFlow<MusicSettings> = app.musicSettings.settings
    val analysisStatus: StateFlow<AnalysisStatus> = app.analysis.status
    val speedCheck: StateFlow<SpeedCheck?> = app.analysis.lastSpeedCheck
    val modelDownload = app.models.download
    val modelChanges = app.models.changes
    val models: ModelManager = app.models

    val scenes: StateFlow<List<Scene>> = nowPlaying
        .map { it.book?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeScenes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateMusic(transform: (MusicSettings) -> MusicSettings) = app.musicSettings.update(transform)

    fun analyzeBook(fromCurrentPosition: Boolean) {
        val np = nowPlaying.value
        val book = np.book ?: lastBook.value ?: return
        viewModelScope.launch {
            // With an imported book text no speech recognition is needed.
            if (!app.analysis.hasSpeechModel && !repository.hasBookText(book.id)) {
                toast("Bitte zuerst ein Spracherkennungsmodell laden oder den Buchtext einfügen")
                return@launch
            }
            val from = if (fromCurrentPosition && np.book != null) np.positionMs else 0L
            app.analysis.analyze(book.id, fromMs = from)
        }
    }

    /** Analyses [bookId] from [fromMs] on (from the "KI-Analyse" tab of any book). */
    fun analyzeBookFrom(bookId: Long, fromMs: Long) {
        viewModelScope.launch {
            if (!app.analysis.hasSpeechModel && !repository.hasBookText(bookId)) {
                toast("Bitte zuerst ein Spracherkennungsmodell laden oder den Buchtext einfügen")
                return@launch
            }
            app.analysis.analyze(bookId, fromMs = fromMs)
        }
    }

    /** Set before opening a book's details on a certain tab (e.g. 4 = "KI-Analyse"). */
    var requestedBookTab by mutableStateOf<Int?>(null)

    fun stopAnalysis() = app.analysis.stop()

    /** Deletes the analysis of the current book, or of all books when [allBooks] is set. */
    fun clearAnalysis(allBooks: Boolean = false, keepCorrections: Boolean = false, transcripts: Boolean = true) {
        val book = nowPlaying.value.book ?: lastBook.value
        if (!allBooks && book == null) return
        app.analysis.stop()
        viewModelScope.launch {
            repository.clearAnalysis(if (allBooks) null else book!!.id, keepCorrections, transcripts)
            refreshAnalysisStats()
            toast(if (allBooks) "Alle Analysen gelöscht" else "Analyse dieses Buchs gelöscht")
        }
    }

    // ------------------------------------------------------------------ feedback on scenes and music

    private val _analysisStats = MutableStateFlow<LibraryRepository.AnalysisStats?>(null)
    val analysisStats: StateFlow<LibraryRepository.AnalysisStats?> = _analysisStats

    fun refreshAnalysisStats() {
        viewModelScope.launch { _analysisStats.value = repository.analysisStats() }
    }

    /** "Fits": the scene stays as it is and serves as an example for the rest of the book. */
    fun confirmScene(scene: Scene, bookId: Long? = null) {
        val id = bookId ?: nowPlaying.value.book?.id ?: return
        viewModelScope.launch {
            repository.confirmScene(id, scene.startMs, scene.endMs)
            toast("Danke – die Szene bleibt so")
        }
    }

    /** Forgets the analysis of a scene; with [reanalyze] the language model judges it again. */
    fun deleteScene(scene: Scene, reanalyze: Boolean, bookId: Long? = null) {
        val id = bookId ?: nowPlaying.value.book?.id ?: return
        viewModelScope.launch {
            if (app.analysis.isRunning) app.analysis.stop()
            repository.deleteSceneAnalysis(id, scene.startMs, scene.endMs)
            if (reanalyze) {
                app.analysis.analyze(id, fromMs = scene.startMs, untilMs = scene.endMs)
                toast("Szene wird neu analysiert")
            } else {
                toast("Analyse der Szene gelöscht")
            }
        }
    }

    fun skipMusicTrack() = withCore { it.skipMusicTrack() }

    // ------------------------------------------------------------------ book texts

    fun bookTextInfo(bookId: Long) = repository.observeBookTextInfo(bookId)

    /** Reads a TXT, HTML or EPUB file as the written text of the book. */
    fun importBookText(bookId: Long, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { BookTextReader.read(getApplication<Application>(), uri) } }
            result.onSuccess { (text, source) -> saveBookText(bookId, text, source) }
                .onFailure { toast("Text konnte nicht gelesen werden: ${it.message}") }
        }
    }

    /** Takes the text from the clipboard, e.g. copied from an e-book app. */
    fun pasteBookText(bookId: Long) {
        val clipboard = getApplication<Application>().getSystemService(android.content.ClipboardManager::class.java)
        val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(getApplication<Application>())?.toString()
        if (text.isNullOrBlank()) {
            toast("Die Zwischenablage enthält keinen Text")
            return
        }
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.Default) { BookTextParser.fromPlain(text) }
            saveBookText(bookId, parsed, "Zwischenablage")
        }
    }

    private suspend fun saveBookText(bookId: Long, text: BookText, source: String) {
        if (text.text.length < 200) {
            toast("Das ist zu wenig Text für ein Buch")
            return
        }
        repository.saveBookText(bookId, text, source)
        toast("Buchtext übernommen: ${text.text.length / 1000} Tsd. Zeichen, ${text.chapters.size} Kapitel erkannt")
    }

    /** Analyses the whole book again, now reading its text (corrections are kept). */
    fun reanalyzeWithText(bookId: Long) {
        app.analysis.stop()
        viewModelScope.launch {
            repository.clearAnalysis(bookId, keepCorrections = true, transcripts = true)
            app.analysis.analyze(bookId)
            toast("Szenen werden mit dem Buchtext neu analysiert")
        }
    }

    /** For tests and adb: a text file on the device becomes the text of the current book. */
    fun importBookTextFile(path: String, reanalyze: Boolean) {
        viewModelScope.launch {
            val book = nowPlaying.value.book ?: lastBook.value ?: allBooks.value.firstOrNull() ?: return@launch
            val result = withContext(Dispatchers.IO) { runCatching { BookTextReader.read(getApplication<Application>(), Uri.fromFile(java.io.File(path))) } }
            val (text, source) = result.getOrElse {
                toast("Text konnte nicht gelesen werden: ${it.message}")
                return@launch
            }
            saveBookText(book.id, text, source)
            if (reanalyze) reanalyzeWithText(book.id)
        }
    }

    fun removeBookText(bookId: Long) {
        viewModelScope.launch {
            repository.deleteBookText(bookId)
            toast("Buchtext entfernt")
        }
    }

    /** Text shared from another app, waiting for the listener to pick its book. */
    data class SharedText(val text: BookText, val source: String)

    private val _sharedText = MutableStateFlow<SharedText?>(null)
    val sharedText: StateFlow<SharedText?> = _sharedText

    fun receiveSharedText(text: String?, uri: Uri?) {
        // A shared video or sound file: convert it into an audiobook.
        val mime = uri?.let { getApplication<Application>().contentResolver.getType(it) }.orEmpty()
        if (uri != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
            downloads.tab = DownloadBrowser.Tab.FILES
            navigate(Screen.Downloads)
            downloads.convertFiles(listOf(uri))
            return
        }
        // Shared links (from YouTube, NewPipe, a browser …): open them on the download screen.
        val urls = text?.let { LinkInput.allUrls(it) }.orEmpty()
        if (uri == null && urls.isNotEmpty() && text!!.length - urls.sumOf { it.length } < 200) {
            downloads.tab = DownloadBrowser.Tab.MEDIA
            downloads.mediaInput = urls.joinToString("\n")
            navigate(Screen.Downloads)
            downloads.go()
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        uri != null -> BookTextReader.read(getApplication<Application>(), uri).let { (t, src) -> SharedText(t, src) }
                        !text.isNullOrBlank() -> SharedText(BookTextParser.fromPlain(text), "Geteilter Text")
                        else -> null
                    }
                }
            }
            result.onSuccess { _sharedText.value = it }.onFailure { toast("Text konnte nicht gelesen werden: ${it.message}") }
        }
    }

    fun assignSharedText(bookId: Long?) {
        val shared = _sharedText.value ?: return
        _sharedText.value = null
        if (bookId != null) viewModelScope.launch { saveBookText(bookId, shared.text, shared.source) }
    }

    /** Set by a deep link: the player opens its "Passt die Musik?" sheet. */
    var openFeedback by mutableStateOf(false)

    fun toggleLikedTrack(id: String) = updateMusic { s ->
        s.copy(likedTracks = if (id in s.likedTracks) s.likedTracks - id else s.likedTracks + id, hiddenTracks = s.hiddenTracks - id)
    }

    /** Hidden pieces never play again (until shown again); a playing one is replaced right away. */
    fun toggleHiddenTrack(id: String) {
        val hide = id !in app.musicSettings.current.hiddenTracks
        updateMusic { s ->
            s.copy(hiddenTracks = if (hide) s.hiddenTracks + id else s.hiddenTracks - id, likedTracks = s.likedTracks - id)
        }
        if (hide) toast("Dieses Stück wird nicht mehr gespielt")
    }

    fun showAllTracks() = updateMusic { it.copy(hiddenTracks = emptySet()) }

    fun overrideScene(scene: Scene, mood: Mood?, level: Int, title: String, setting: Setting?, bookId: Long? = null) {
        val id = bookId ?: nowPlaying.value.book?.id ?: return
        viewModelScope.launch { repository.overrideScene(id, scene.startMs, scene.endMs, mood, level, title, setting) }
    }

    fun previewMusic(spec: SoundSpec) = withCore { it.previewMusic(spec) }

    // ------------------------------------------------------------------ fantasy music library

    val musicLibrary: MusicLibrary = app.musicLibrary
    val libraryInstalled: StateFlow<Set<String>> = app.musicLibrary.installed
    val libraryDownload: StateFlow<LibraryDownload?> = app.musicLibrary.download

    /** Downloads pieces and switches the music to the library, so they are used right away. */
    fun downloadMusic(ids: Collection<String>) {
        app.musicLibrary.download(ids)
        updateMusic { it.copy(source = MusicSource.LIBRARY) }
    }

    fun cancelMusicDownload() = app.musicLibrary.cancel()

    fun stopMusicPreview() = withCore { it.stopPreview() }

    fun dismissMusicDownloadError() = app.musicLibrary.clearError()

    fun deleteMusic(ids: Collection<String>) {
        app.musicLibrary.delete(ids)
        if (app.musicLibrary.installed.value.isEmpty()) updateMusic { it.copy(source = MusicSource.SYNTH) }
    }

    fun previewTrack(track: LibraryTrack) = withCore {
        it.previewMusic(SoundSpec(mood = track.primaryMood, intensity = 0.6f, trackId = track.id), seconds = 30)
    }

    fun downloadModel(model: ModelInfo) {
        val job = viewModelScope.launch {
            if (app.models.downloadModel(model)) {
                selectModel(model.kind, model.id)
                toast("${model.label} ist bereit")
            }
        }
        app.models.setJob(job)
    }

    fun cancelDownload() = app.models.cancelDownload()

    fun importModel(kind: ModelKind, uri: Uri) {
        viewModelScope.launch {
            val id = app.models.importModel(kind, uri)
            if (id != null) {
                selectModel(kind, id)
                toast("Eigenes Modell übernommen")
            } else {
                toast("Datei konnte nicht übernommen werden")
            }
        }
    }

    fun deleteModel(id: String) {
        app.models.delete(id)
        updateMusic { s ->
            s.copy(
                whisperModel = s.whisperModel.takeIf { it != id },
                llmModel = s.llmModel.takeIf { it != id },
            )
        }
    }

    fun selectModel(kind: ModelKind, id: String) = updateMusic {
        if (kind == ModelKind.SPEECH) it.copy(whisperModel = id) else it.copy(llmModel = id)
    }

    suspend fun recap(): Result<String> {
        val np = nowPlaying.value
        val book = np.book ?: return Result.failure(IllegalStateException("Kein Hörbuch geladen"))
        return app.analysis.recap(book.id, np.positionMs)
    }

    suspend fun searchTranscript(query: String): List<TranscriptWindow> {
        val book = nowPlaying.value.book ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return repository.searchTranscripts(book.id, query)
    }

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
