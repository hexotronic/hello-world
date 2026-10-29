package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.hexotronic.lauscher.analysis.core.KeywordMoods
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.ScenePrompt
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.analysis.core.WindowMood
import io.github.hexotronic.lauscher.analysis.core.WindowPlanner
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.Track
import io.github.hexotronic.lauscher.llm.LlamaNative
import io.github.hexotronic.lauscher.music.AnalysisDepth
import io.github.hexotronic.lauscher.music.MusicSettingsStore
import io.github.hexotronic.lauscher.playback.AudiobookPlayerCore
import io.github.hexotronic.lauscher.whisper.WhisperNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

sealed interface AnalysisStatus {
    data object Idle : AnalysisStatus
    data class Running(val bookId: Long, val phase: String, val done: Int, val total: Int, val automatic: Boolean) : AnalysisStatus
    data class Finished(val bookId: Long, val windows: Int, val usedLanguageModel: Boolean) : AnalysisStatus
    data class Failed(val message: String) : AnalysisStatus
}

/**
 * Listens to the audiobook with an on-device speech model (whisper.cpp) and lets an
 * on-device language model (llama.cpp) decide which background music suits each scene.
 * Nothing leaves the phone. Without a language model a keyword heuristic is used.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisManager(
    private val context: Context,
    private val repo: LibraryRepository,
    private val models: ModelManager,
    private val settings: MusicSettingsStore,
    private val scope: CoroutineScope,
) {
    /** The native engines are not thread-safe, so all their work runs on one thread. */
    private val native = Dispatchers.IO.limitedParallelism(1)
    private val _status = MutableStateFlow<AnalysisStatus>(AnalysisStatus.Idle)
    val status: StateFlow<AnalysisStatus> = _status.asStateFlow()
    private var job: Job? = null
    private var currentLlm: LlamaNative? = null

    val hasSpeechModel: Boolean get() = models.isInstalled(settings.current.whisperModel)
    val hasLanguageModel: Boolean get() = models.isInstalled(settings.current.llmModel)
    val isRunning: Boolean get() = job?.isActive == true

    fun stop() {
        currentLlm?.cancel()
        job?.cancel()
        job = null
        if (_status.value is AnalysisStatus.Running) _status.value = AnalysisStatus.Idle
    }

    /** Analyses all not yet analysed windows between [fromMs] and [untilMs]. */
    fun analyze(bookId: Long, fromMs: Long = 0, untilMs: Long = Long.MAX_VALUE, automatic: Boolean = false) {
        if (isRunning) return
        job = scope.launch(native) {
            try {
                runAnalysis(bookId, fromMs, untilMs, automatic)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Analysis failed", e)
                _status.value = AnalysisStatus.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Called regularly while listening: keeps the next minutes analysed. */
    fun ensureAhead(bookId: Long, positionMs: Long) {
        val s = settings.current
        if (!s.analyzeAhead || isRunning || !hasSpeechModel) return
        scope.launch {
            val done = repo.analyzedWindowStarts(bookId)
            val book = repo.book(bookId) ?: return@launch
            val upcoming = WindowPlanner.windows(book.durationMs).filter {
                it.last >= positionMs && it.first < positionMs + AHEAD_MS
            }
            if (upcoming.any { it.first !in done }) {
                analyze(bookId, fromMs = positionMs, untilMs = positionMs + AHEAD_MS + 4 * WindowPlanner.WINDOW_MS, automatic = true)
            }
        }
    }

    private suspend fun runAnalysis(bookId: Long, fromMs: Long, untilMs: Long, automatic: Boolean) {
        val details = repo.bookDetails(bookId) ?: return
        val s = settings.current
        val done = repo.analyzedWindowStarts(bookId)
        val todo = WindowPlanner.windows(details.book.durationMs)
            .filter { it.last >= fromMs && it.first < untilMs && it.first !in done }
        if (todo.isEmpty()) {
            _status.value = AnalysisStatus.Finished(bookId, 0, hasLanguageModel)
            return
        }
        val whisperFile = models.fileFor(s.whisperModel)
            ?: error("Kein Spracherkennungsmodell installiert – bitte unter „KI-Modelle“ herunterladen")
        _status.value = AnalysisStatus.Running(bookId, "Lade Spracherkennung …", 0, todo.size, automatic)
        val whisper = WhisperNative.load(whisperFile.absolutePath) ?: error("Spracherkennungsmodell konnte nicht geladen werden")
        var llm: LlamaNative? = null
        var analysed = 0
        try {
            for (batch in todo.chunked(BATCH)) {
                coroutineContext.ensureActive()
                val windows = ArrayList<TranscriptWindow>()
                for (w in batch) {
                    coroutineContext.ensureActive()
                    _status.value = AnalysisStatus.Running(bookId, "Höre zu: ${ScenePrompt.clock(w.first)}", analysed, todo.size, automatic)
                    val existing = repo.transcriptAt(bookId, w.first)
                    val text = existing?.text ?: transcribe(whisper, details.tracks, w, s.depth == AnalysisDepth.FULL, s.language)
                    val window = TranscriptWindow(w.first, w.last + 1, text)
                    if (existing == null) repo.saveTranscript(bookId, window)
                    windows.add(window)
                    analysed++
                }
                _status.value = AnalysisStatus.Running(bookId, "Verstehe die Szene …", analysed, todo.size, automatic)
                if (llm == null && hasLanguageModel) {
                    llm = models.fileFor(s.llmModel)?.let { LlamaNative.load(it.absolutePath, CONTEXT) }
                    currentLlm = llm
                }
                val moods = classify(details.book, windows, llm)
                repo.saveWindowMoods(bookId, moods)
            }
            _status.value = AnalysisStatus.Finished(bookId, analysed, llm != null)
        } finally {
            whisper.close()
            llm?.close()
            currentLlm = null
        }
    }

    private fun transcribe(whisper: WhisperNative, tracks: List<Track>, window: LongRange, full: Boolean, language: String): String {
        val sample = WindowPlanner.sampleOf(window, full)
        val (index, offset) = AudiobookPlayerCore.locate(tracks, sample.first)
        val track = tracks.getOrNull(index) ?: return ""
        val length = minOf(sample.last + 1 - sample.first, (track.durationMs - offset).coerceAtLeast(0))
        if (length < 2_000) return ""
        val pcm = AudioSegmentDecoder.decode(context, Uri.parse(track.uri), offset, length)
        if (pcm.size < WhisperNative.SAMPLE_RATE) return ""
        return whisper.transcribe(pcm, language).joinToString(" ") { it.text }.trim()
    }

    private suspend fun classify(book: Book, windows: List<TranscriptWindow>, llm: LlamaNative?): List<WindowMood> {
        if (llm != null) {
            val story = repo.story(book.id)
            var maxChars = 900
            var prompt: String
            do {
                prompt = llm.chatPrompt(ScenePrompt.SYSTEM, ScenePrompt.user(book.title, book.author, story, windows, maxChars))
                maxChars -= 200
            } while (llm.countTokens(prompt) + maxTokens(windows.size) > llm.contextSize && maxChars > 100)
            val output = llm.generate(prompt, ScenePrompt.grammar(windows.size), maxTokens(windows.size), temperature = 0.2f)
            val parsed = output?.let { ScenePrompt.parse(it, windows) }
            if (parsed != null) {
                if (parsed.story.isNotBlank()) repo.saveStory(book.id, parsed.story)
                Log.i(TAG, "Szenen: " + parsed.windows.joinToString { "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}/${it.level} ${it.title}" })
                return parsed.windows
            }
            Log.w(TAG, "Language model gave no usable answer, using keywords")
        }
        return windows.map { w ->
            val (mood, level) = KeywordMoods.classify(w.text)
            WindowMood(w.startMs, w.endMs, mood, level, "", SceneMerger.SOURCE_KEYWORDS)
        }.also { list ->
            Log.i(TAG, "Szenen (Schlüsselwörter): " + list.joinToString { "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}" })
        }
    }

    private fun maxTokens(windows: Int) = 200 + 60 * windows

    /** "Was bisher geschah": a spoiler-free recap from the transcripts up to [positionMs]. */
    suspend fun recap(bookId: Long, positionMs: Long): Result<String> {
        stop()
        return withContext(native) {
            runCatching {
                val book = repo.book(bookId) ?: error("Hörbuch nicht gefunden")
                val file = models.fileFor(settings.current.llmModel)
                    ?: error("Für den Rückblick wird ein Sprachmodell gebraucht – bitte unter „KI-Modelle“ herunterladen")
                val story = repo.story(bookId)
                val recent = repo.transcripts(bookId, untilMs = positionMs)
                if (recent.isEmpty() && story.isBlank()) {
                    error("Noch nichts transkribiert. Starte unter „Hintergrundmusik“ die Analyse oder höre mit aktivierter Szenen-Musik weiter.")
                }
                val llm = LlamaNative.load(file.absolutePath, CONTEXT) ?: error("Sprachmodell konnte nicht geladen werden")
                try {
                    currentLlm = llm
                    var chars = 5000
                    var prompt: String
                    do {
                        prompt = llm.chatPrompt(ScenePrompt.RECAP_SYSTEM, ScenePrompt.recapUser(book.title, story, recent, chars))
                        chars -= 1000
                    } while (llm.countTokens(prompt) + 400 > llm.contextSize && chars > 500)
                    llm.generate(prompt, null, maxTokens = 400, temperature = 0.4f)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: error("Das Sprachmodell hat keine Antwort geliefert")
                } finally {
                    currentLlm = null
                    llm.close()
                }
            }
        }
    }

    companion object {
        private const val TAG = "LauscherAnalyse"
        private const val BATCH = 5
        private const val CONTEXT = 4096
        private const val AHEAD_MS = 8 * 60_000L
    }
}
