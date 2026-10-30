package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.hexotronic.lauscher.analysis.core.Anchor
import io.github.hexotronic.lauscher.analysis.core.KeywordMoods
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.ScenePrompt
import io.github.hexotronic.lauscher.analysis.core.TextAligner
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.analysis.core.WindowMood
import io.github.hexotronic.lauscher.analysis.core.WindowPlanner
import io.github.hexotronic.lauscher.audio.dsp.TimeStretch
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.Track
import io.github.hexotronic.lauscher.llm.LlamaNative
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
    data class Running(
        val bookId: Long,
        val phase: String,
        val done: Int,
        val total: Int,
        val automatic: Boolean,
        /** Estimated remaining time, once a few windows have been measured. */
        val remainingSeconds: Int? = null,
    ) : AnalysisStatus
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
        if (!s.analyzeAhead || isRunning) return
        scope.launch {
            // Without speech recognition only books with an imported text can be analysed.
            if (!hasSpeechModel && !repo.hasBookText(bookId)) return@launch
            val book = repo.book(bookId) ?: return@launch
            val windowMs = s.samplingPlan.windowMs
            val upcoming = WindowPlanner.windows(book.durationMs, windowMs).filter {
                it.last >= positionMs && it.first < positionMs + AHEAD_MS
            }
            if (WindowPlanner.pending(upcoming, repo.analyzedRanges(bookId)).isNotEmpty()) {
                analyze(bookId, fromMs = positionMs, untilMs = positionMs + AHEAD_MS + 4 * windowMs, automatic = true)
            }
        }
    }

    private suspend fun runAnalysis(bookId: Long, fromMs: Long, untilMs: Long, automatic: Boolean) {
        val details = repo.bookDetails(bookId) ?: return
        val s = settings.current
        val plan = s.samplingPlan
        val todo = WindowPlanner.pending(
            WindowPlanner.windows(details.book.durationMs, plan.windowMs).filter { it.last >= fromMs && it.first < untilMs },
            repo.analyzedRanges(bookId),
        )
        if (todo.isEmpty()) {
            _status.value = AnalysisStatus.Finished(bookId, 0, hasLanguageModel)
            return
        }
        _status.value = AnalysisStatus.Running(bookId, "Bereite vor …", 0, todo.size, automatic)
        // Speech recognition is only loaded when it is needed (no transcript or book text yet).
        var whisper: WhisperNative? = null
        fun speech(): WhisperNative = whisper ?: run {
            val file = models.fileFor(s.whisperModel)
                ?: error("Kein Spracherkennungsmodell installiert – bitte unter „KI-Modelle“ herunterladen oder den Buchtext einfügen")
            (WhisperNative.load(file.absolutePath) ?: error("Spracherkennungsmodell konnte nicht geladen werden")).also { whisper = it }
        }
        // An imported book text replaces listening; a few speech samples line it up with the audio.
        val bookText = repo.bookText(bookId)
        var anchors = bookText?.let { stored ->
            val chapters = details.chapters.map { TextAligner.AudioChapter(it.title, it.startMs) }
            val initial = TextAligner.initialAnchors(stored.text, chapters, details.book.durationMs)
            stored.anchors.fold(initial) { acc, a -> TextAligner.withAnchor(acc, a) }
        }
        val textIndex = if (bookText != null && s.alignWithSpeech && hasSpeechModel) TextAligner.Index(bookText.text.text) else null
        Log.i(
            TAG,
            "Variante: Whisper ${WhisperNative.variant}, LLM ${LlamaNative.variant}, ${LlamaNative.defaultThreads()} Threads, " +
                "alle ${plan.windowMs / 1000} s je ${if (plan.full) "alles" else "${plan.sampleMs / 1000} s"}, Tempo ${plan.speed}×" +
                (bookText?.let { ", Buchtext ${it.text.text.length} Zeichen, ${anchors?.size} Anker" } ?: ""),
        )
        var llm: LlamaNative? = null
        var analysed = 0
        val started = System.nanoTime()
        var speechNanos = 0L
        var speechAudioMs = 0L
        var modelNanos = 0L
        var modelWindows = 0
        fun remaining(): Int? = if (analysed < 2) null else {
            val perWindow = (System.nanoTime() - started) / 1e9 / analysed
            (perWindow * (todo.size - analysed)).toInt()
        }
        try {
            for (batch in todo.chunked(BATCH)) {
                coroutineContext.ensureActive()
                val windows = ArrayList<TranscriptWindow>()
                val t0 = System.nanoTime()
                for (w in batch) {
                    coroutineContext.ensureActive()
                    val known = anchors
                    val text = if (bookText != null && known != null) {
                        var a: List<Anchor> = known
                        _status.value = AnalysisStatus.Running(bookId, "Lese: ${ScenePrompt.clock(w.first)}", analysed, todo.size, automatic, remaining())
                        val mid = (w.first + w.last) / 2
                        if (textIndex != null && TextAligner.nearestAnchorDistance(a, mid) > ALIGN_EVERY_MS) {
                            // Listen briefly and find the heard words in the text.
                            val heardStart = System.nanoTime()
                            val heard = transcribe(speech(), details.tracks, mid until mid + ALIGN_SAMPLE_MS, Long.MAX_VALUE, s.language, plan.speed)
                            speechNanos += System.nanoTime() - heardStart
                            speechAudioMs += (ALIGN_SAMPLE_MS / plan.speed).toLong()
                            val expected = TextAligner.charAt(a, mid)
                            val radius = maxOf(5_000, TextAligner.charAt(a, mid + ALIGN_RADIUS_MS) - TextAligner.charAt(a, mid - ALIGN_RADIUS_MS))
                            val found = TextAligner.locate(textIndex, heard, expected, radius)
                            if (found != null) {
                                a = TextAligner.withAnchor(a, Anchor(mid, found))
                                anchors = a
                                repo.saveAnchors(bookId, a.filter { it.timeMs != 0L && it.timeMs != details.book.durationMs })
                            }
                            Log.i(TAG, "Abgleich bei ${ScenePrompt.clock(mid)}: " + (found?.let { "Text gefunden (erwartet $expected, gefunden $it)" } ?: "nicht gefunden"))
                        }
                        TextAligner.slice(bookText.text.text, a, w.first, w.last + 1)
                    } else {
                        _status.value = AnalysisStatus.Running(bookId, "Höre zu: ${ScenePrompt.clock(w.first)}", analysed, todo.size, automatic, remaining())
                        val existing = repo.transcriptAt(bookId, w.first)
                        existing?.text ?: run {
                            val heardStart = System.nanoTime()
                            val heard = transcribe(speech(), details.tracks, w, plan.sampleMs, s.language, plan.speed)
                            speechNanos += System.nanoTime() - heardStart
                            speechAudioMs += (minOf(plan.sampleMs, w.last + 1 - w.first) / plan.speed).toLong()
                            heard
                        }
                    }
                    val window = TranscriptWindow(w.first, w.last + 1, text)
                    repo.saveTranscript(bookId, window)
                    windows.add(window)
                    analysed++
                }
                val t1 = System.nanoTime()
                _status.value = AnalysisStatus.Running(bookId, "Verstehe die Szene …", analysed, todo.size, automatic, remaining())
                if (llm == null && hasLanguageModel) {
                    llm = models.fileFor(s.llmModel)?.let { LlamaNative.load(it.absolutePath, CONTEXT) }
                    currentLlm = llm
                }
                val t2 = System.nanoTime()
                val moods = classify(details.book, windows, llm)
                repo.saveWindowMoods(bookId, moods)
                val t3 = System.nanoTime()
                if (llm != null) {
                    modelNanos += t3 - t2
                    modelWindows += batch.size
                }
                Log.i(
                    TAG,
                    "Zeit für ${batch.size} Abschnitte: Spracherkennung ${(t1 - t0) / 1_000_000} ms, " +
                        "Modell laden ${(t2 - t1) / 1_000_000} ms, Sprachmodell ${(t3 - t2) / 1_000_000} ms",
                )
            }
            _status.value = AnalysisStatus.Finished(bookId, analysed, llm != null)
            Log.i(TAG, "Analyse fertig: $analysed Abschnitte in ${(System.nanoTime() - started) / 1_000_000} ms")
        } finally {
            whisper?.close()
            llm?.close()
            currentLlm = null
            rememberSpeed(speechNanos, speechAudioMs, modelNanos, modelWindows)
        }
    }

    /** Keeps a running average of this device's speed for the time estimate in the settings. */
    private fun rememberSpeed(speechNanos: Long, speechAudioMs: Long, modelNanos: Long, modelWindows: Int) {
        if (speechAudioMs < 5_000 && modelWindows == 0) return
        settings.update { cur ->
            val speech = if (speechAudioMs >= 5_000) (speechNanos / 1e6 / speechAudioMs).toFloat() else null
            val model = if (modelWindows > 0) (modelNanos / 1e9 / modelWindows).toFloat() else null
            cur.copy(
                measuredSpeechFactor = speech?.let { if (cur.measuredSpeechFactor > 0) cur.measuredSpeechFactor * 0.6f + it * 0.4f else it }
                    ?: cur.measuredSpeechFactor,
                measuredModelSeconds = model?.let { if (cur.measuredModelSeconds > 0) cur.measuredModelSeconds * 0.6f + it * 0.4f else it }
                    ?: cur.measuredModelSeconds,
            )
        }
    }

    /**
     * Transcribes a sample of [window]. At [speed] > 1 the audio is sped up first (same pitch),
     * so the recognition has less to listen to.
     */
    private fun transcribe(whisper: WhisperNative, tracks: List<Track>, window: LongRange, sampleMs: Long, language: String, speed: Float): String {
        val sample = WindowPlanner.sampleOf(window, sampleMs)
        // One whisper pass handles 30 s of (sped-up) audio; longer samples are cut into chunks.
        return WindowPlanner.chunks(sample, (30_000 * speed).toLong()).joinToString(" ") { chunk ->
            val (index, offset) = AudiobookPlayerCore.locate(tracks, chunk.first)
            val track = tracks.getOrNull(index) ?: return@joinToString ""
            val length = minOf(chunk.last + 1 - chunk.first, (track.durationMs - offset).coerceAtLeast(0))
            if (length < 2_000) return@joinToString ""
            recognize(whisper, AudioSegmentDecoder.decode(context, Uri.parse(track.uri), offset, length), language, speed)
        }.trim()
    }

    /** Speeds [decoded] (16 kHz mono, at most 30 s × [speed]) up and recognises it. */
    private fun recognize(whisper: WhisperNative, decoded: FloatArray, language: String, speed: Float): String {
        val pcm = TimeStretch.speedUp(decoded, WhisperNative.SAMPLE_RATE, speed)
        if (pcm.size < WhisperNative.SAMPLE_RATE) return ""
        return whisper.transcribe(pcm, language).joinToString(" ") { it.text }.trim()
    }

    /**
     * Test aid (adb extra "speech_check"): recognises the start of [file] at each of [speeds] and
     * logs time and text, so the effect of the speed setting can be compared on a real device.
     */
    fun speechCheck(file: java.io.File, speeds: List<Float>) {
        val previous = job
        scope.launch(native) {
            previous?.join()
            val model = models.fileFor(settings.current.whisperModel)
            val whisper = model?.let { WhisperNative.load(it.absolutePath) }
            if (whisper == null) {
                Log.w(TAG, "Sprachtest fertig: kein Spracherkennungsmodell")
                return@launch
            }
            try {
                val decoded = AudioSegmentDecoder.decode(context, Uri.fromFile(file), 0, SPEECH_CHECK_MS)
                for (speed in speeds) {
                    val started = System.nanoTime()
                    val chunk = (WhisperNative.SAMPLE_RATE * 30 * speed).toInt()
                    val text = (decoded.indices step chunk).joinToString(" ") { from ->
                        recognize(whisper, decoded.copyOfRange(from, minOf(decoded.size, from + chunk)), settings.current.language, speed)
                    }.trim()
                    val words = text.split(' ').count { it.isNotBlank() }
                    Log.i(TAG, "Sprachtest $speed×: ${(System.nanoTime() - started) / 1_000_000} ms, $words Wörter: $text")
                }
                Log.i(TAG, "Sprachtest fertig")
            } finally {
                whisper.close()
            }
        }
    }

    private suspend fun classify(book: Book, windows: List<TranscriptWindow>, llm: LlamaNative?): List<WindowMood> {
        if (llm != null) {
            val story = repo.story(book.id)
            var maxChars = 450
            var prompt: String
            // The listener's corrections nearest to these windows teach the model their taste.
            val corrections = if (settings.current.learnFromFeedback) {
                ScenePrompt.nearestCorrections(repo.corrections(book.id), windows.first().startMs)
            } else {
                emptyList()
            }
            do {
                prompt = llm.chatPrompt(ScenePrompt.SYSTEM, ScenePrompt.user(book.title, book.author, story, windows, maxChars, corrections))
                maxChars -= 100
            } while (llm.countTokens(prompt) + maxTokens(windows.size) > llm.contextSize && maxChars > 100)
            // Greedy decoding: deterministic and the fastest path through the JSON grammar.
            val output = llm.generate(prompt, ScenePrompt.grammar(windows.size), maxTokens(windows.size), temperature = 0f)
            val parsed = output?.let { ScenePrompt.parse(it, windows) }
            if (parsed != null) {
                if (parsed.story.isNotBlank()) repo.saveStory(book.id, parsed.story)
                // Where the model saw no clear place, place words in the text may still name one.
                val result = parsed.windows.mapIndexed { i, w -> if (w.setting != null) w else w.copy(setting = KeywordMoods.settingOf(windows[i].text)) }
                Log.i(TAG, "Szenen: " + result.joinToString { "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}/${it.level}@${it.setting ?: "?"} ${it.title}" })
                return result
            }
            Log.w(TAG, "Language model gave no usable answer, using keywords")
        }
        return windows.map { w ->
            val (mood, level) = KeywordMoods.classify(w.text)
            WindowMood(w.startMs, w.endMs, mood, level, "", SceneMerger.SOURCE_KEYWORDS, KeywordMoods.settingOf(w.text))
        }.also { list ->
            Log.i(TAG, "Szenen (Schlüsselwörter): " + list.joinToString { "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}@${it.setting ?: "?"}" })
        }
    }

    private fun maxTokens(windows: Int) = 120 + 48 * windows

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
        /** Windows per language-model call: the instructions are read once for all of them. */
        private const val BATCH = 8
        private const val CONTEXT = 4096
        private const val SPEECH_CHECK_MS = 90_000L
        private const val AHEAD_MS = 8 * 60_000L

        /** With a book text: listen briefly where the nearest anchor is further away than this. */
        private const val ALIGN_EVERY_MS = 10 * 60_000L
        private const val ALIGN_SAMPLE_MS = 12_000L

        /** How far around the expected place the heard words are searched (in time). */
        private const val ALIGN_RADIUS_MS = 15 * 60_000L
    }
}
