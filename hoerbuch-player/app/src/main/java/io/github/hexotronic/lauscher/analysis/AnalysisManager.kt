package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.hexotronic.lauscher.analysis.core.AiLogText
import io.github.hexotronic.lauscher.analysis.core.AiStep
import io.github.hexotronic.lauscher.analysis.core.Anchor
import io.github.hexotronic.lauscher.analysis.core.AssistantReply
import io.github.hexotronic.lauscher.analysis.core.AssistantTurn
import io.github.hexotronic.lauscher.analysis.core.BookAssistant
import io.github.hexotronic.lauscher.analysis.core.EvalReport
import io.github.hexotronic.lauscher.analysis.core.MiniJson
import io.github.hexotronic.lauscher.analysis.core.ModelEval
import io.github.hexotronic.lauscher.analysis.core.KeywordMoods
import io.github.hexotronic.lauscher.analysis.core.SceneCut
import io.github.hexotronic.lauscher.analysis.core.SceneCuts
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
import io.github.hexotronic.lauscher.music.synth.Pace
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

/** Result of comparing speech recognition at [speed] with normal speed on a sample of a book. */
data class SpeedCheck(val bookId: Long, val speed: Float, val agreement: Float, val accepted: Boolean)

/** How far "Modelle testen" is: model [model] of [models], step [step] of [steps]. */
data class EvalProgress(val label: String, val model: Int, val models: Int, val step: Int, val steps: Int)

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
    /** Every step is written here for the "KI-Protokoll" window. */
    private val log: AiLog,
) {
    /** The native engines are not thread-safe, so all their work runs on one thread. */
    private val native = Dispatchers.IO.limitedParallelism(1)
    private val _status = MutableStateFlow<AnalysisStatus>(AnalysisStatus.Idle)
    val status: StateFlow<AnalysisStatus> = _status.asStateFlow()
    private var job: Job? = null
    private var currentLlm: LlamaNative? = null
    private val speedChecks = HashMap<Long, SpeedCheck>()
    private val _lastSpeedCheck = MutableStateFlow<SpeedCheck?>(null)
    val lastSpeedCheck: StateFlow<SpeedCheck?> = _lastSpeedCheck.asStateFlow()

    val hasSpeechModel: Boolean get() = models.isInstalled(settings.current.whisperModel)
    val hasLanguageModel: Boolean get() = models.isInstalled(settings.current.llmModel)
    val isRunning: Boolean get() = job?.isActive == true

    fun stop() {
        if (job?.isActive == true) log.add(AiStep.INFO, "Analyse angehalten")
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
                log.add(AiStep.PROBLEM, "Analyse fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}", bookId = bookId)
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
            val aheadMs = s.aheadMinutes * 60_000L
            val upcoming = WindowPlanner.windows(book.durationMs, windowMs).filter {
                it.last >= positionMs && it.first < positionMs + aheadMs
            }
            if (WindowPlanner.pending(upcoming, repo.analyzedRanges(bookId)).isNotEmpty()) {
                analyze(bookId, fromMs = positionMs, untilMs = positionMs + aheadMs + 4 * windowMs, automatic = true)
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
        log.add(
            AiStep.INFO,
            (if (automatic) "Liest voraus: " else "Analyse: ") +
                "${todo.size} Abschnitte ab ${ScenePrompt.clock(todo.first().first)} – alle ${formatMinutes(plan.windowMs)} " +
                (if (plan.full) "alles" else "je ${plan.sampleMs / 1000} s") +
                (if (bookText != null) ", mit Buchtext" else ", Spracherkennung ${s.whisperModel ?: "–"}") +
                ", " + (if (hasLanguageModel) "Sprachmodell ${s.llmModel}" else "ohne Sprachmodell (Schlüsselwörter)"),
            bookId = bookId,
        )
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
        // Faster speech is only used once a sample of this book showed that recognition hardly
        // suffers from it: the first sample is heard at both speeds and compared.
        val knownCheck = speedChecks[bookId]?.takeIf { it.speed == plan.speed }
        var speed = if (plan.speed > 1.01f && knownCheck?.accepted == true) plan.speed else 1f
        var probing = plan.speed > 1.01f && knownCheck == null
        fun listen(range: LongRange, sampleMs: Long): String {
            val heardStart = System.nanoTime()
            val audioMs = minOf(sampleMs, range.last + 1 - range.first)
            val heard = transcribe(speech(), details.tracks, range, sampleMs, s.language, speed)
            speechAudioMs += (audioMs / speed).toLong()
            if (probing && TextAligner.words(heard).size >= SPEED_PROBE_WORDS) {
                probing = false
                val fast = transcribe(speech(), details.tracks, range, sampleMs, s.language, plan.speed)
                speechAudioMs += (audioMs / plan.speed).toLong()
                val agreement = TextAligner.agreement(heard, fast)
                val check = SpeedCheck(bookId, plan.speed, agreement, agreement >= MIN_SPEED_AGREEMENT)
                speedChecks[bookId] = check
                _lastSpeedCheck.value = check
                if (check.accepted) speed = plan.speed
                log.add(
                    AiStep.INFO,
                    "Tempoprobe ${plan.speed}×: ${(agreement * 100).toInt()} % der Wörter wie bei 1× – " +
                        if (check.accepted) "wird genutzt" else "zu ungenau, es bleibt bei 1×",
                    bookId = bookId,
                    positionMs = range.first,
                )
                Log.i(
                    TAG,
                    "Tempoprobe ${plan.speed}×: ${(agreement * 100).toInt()} % der Wörter wie bei 1× – " +
                        if (check.accepted) "wird genutzt" else "zu ungenau, es bleibt bei 1×",
                )
            }
            speechNanos += System.nanoTime() - heardStart
            return heard
        }
        try {
            for (batch in WindowPlanner.batches(todo, FIRST_BATCH, BATCH)) {
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
                            val heard = listen(mid until mid + ALIGN_SAMPLE_MS, Long.MAX_VALUE)
                            val expected = TextAligner.charAt(a, mid)
                            val radius = maxOf(5_000, TextAligner.charAt(a, mid + ALIGN_RADIUS_MS) - TextAligner.charAt(a, mid - ALIGN_RADIUS_MS))
                            val found = TextAligner.locate(textIndex, heard, expected, radius)
                            if (found != null) {
                                a = TextAligner.withAnchor(a, Anchor(mid, found))
                                anchors = a
                                repo.saveAnchors(bookId, a.filter { it.timeMs != 0L && it.timeMs != details.book.durationMs })
                            }
                            Log.i(TAG, "Abgleich bei ${ScenePrompt.clock(mid)}: " + (found?.let { "Text gefunden (erwartet $expected, gefunden $it)" } ?: "nicht gefunden"))
                            log.add(
                                AiStep.HEARD,
                                "Abgleich mit dem Buchtext: ${AiLogText.excerpt(heard, 80)} – " + if (found != null) "Stelle im Text gefunden" else "im Text nicht gefunden",
                                heard,
                                bookId,
                                mid,
                            )
                        }
                        TextAligner.slice(bookText.text.text, a, w.first, w.last + 1).also { slice ->
                            log.add(AiStep.READ, "Buchtext ${ScenePrompt.clock(w.first)}–${ScenePrompt.clock(w.last + 1)}: ${AiLogText.excerpt(slice)}", slice, bookId, w.first)
                        }
                    } else {
                        _status.value = AnalysisStatus.Running(bookId, "Höre zu: ${ScenePrompt.clock(w.first)}", analysed, todo.size, automatic, remaining())
                        val existing = repo.transcriptAt(bookId, w.first)
                        if (existing != null) {
                            log.add(AiStep.HEARD, "Schon gehört (gespeichert): ${AiLogText.excerpt(existing.text)}", existing.text, bookId, w.first)
                            existing.text
                        } else {
                            val heardAt = System.nanoTime()
                            listen(w, plan.sampleMs).also { heard ->
                                val ms = (System.nanoTime() - heardAt) / 1_000_000
                                val sample = if (plan.full) "alles" else "${minOf(plan.sampleMs, w.last + 1 - w.first) / 1000} s"
                                log.add(
                                    AiStep.HEARD,
                                    if (heard.isBlank()) {
                                        "Nichts verstanden ($sample angehört, $ms ms) – Musik, Stille oder Geräusche?"
                                    } else {
                                        "$sample angehört in ${"%.1f".format(ms / 1000f)} s: ${AiLogText.excerpt(heard)}"
                                    },
                                    heard.ifBlank { null },
                                    bookId,
                                    w.first,
                                )
                            }
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

                // Where the music changes, find the scene change more precisely than the window grid:
                // probe between the two samples and ask the model which scene the passage belongs to.
                val model = llm
                if (model != null && s.refineSceneCuts && (bookText != null || hasSpeechModel)) {
                    val batchStarts = batch.map { it.first }.toSet()
                    val done = repo.sceneCuts(bookId).map { it.afterMs }.toSet()
                    val candidates = SceneCuts.candidates(repo.analysedWindows(bookId), done)
                        .filter { (a, b) -> a.startMs in batchStarts || b.startMs in batchStarts }
                    for ((a, b) in candidates) {
                        coroutineContext.ensureActive()
                        _status.value = AnalysisStatus.Running(
                            bookId, "Suche den Szenenwechsel bei ${ScenePrompt.clock(b.startMs)} …", analysed, todo.size, automatic, remaining(),
                        )
                        val cutStarted = System.nanoTime()
                        val textA = repo.transcriptAt(bookId, a.startMs)?.text
                        val textB = repo.transcriptAt(bookId, b.startMs)?.text
                        val known = anchors
                        var questions = 0
                        val asked = StringBuilder()
                        val cut = try {
                            SceneCuts.bisect(sampleCentre(a, plan.sampleMs), sampleCentre(b, plan.sampleMs), CUT_STEPS) { t ->
                                val probe = (t - CUT_PROBE_MS / 2) until (t + CUT_PROBE_MS / 2)
                                val passage = if (bookText != null && known != null) {
                                    TextAligner.slice(bookText.text.text, known, probe.first, probe.last + 1)
                                } else {
                                    listen(probe, Long.MAX_VALUE)
                                }
                                if (TextAligner.words(passage).size < 4) {
                                    null
                                } else {
                                    questions++
                                    val prompt = model.chatPrompt(ScenePrompt.CUT_SYSTEM, ScenePrompt.cutUser(a, b, textA, textB, passage))
                                    val answer = model.generate(prompt, ScenePrompt.CUT_GRAMMAR, maxTokens = 2, temperature = 0f)
                                    asked.append("Bei ${ScenePrompt.clock(t)}: ${AiLogText.excerpt(passage, 100)} → gehört zu ${answer?.trim() ?: "?"}\n")
                                    ScenePrompt.parseCut(answer)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Szenenwechsel bei ${ScenePrompt.clock(b.startMs)} nicht gefunden", e)
                            b.startMs
                        }
                        repo.saveSceneCut(bookId, SceneCut(b.startMs, cut))
                        log.add(
                            AiStep.CUT,
                            "Szenenwechsel bei ${ScenePrompt.clock(cut)} statt ${ScenePrompt.clock(b.startMs)} " +
                                "(${a.mood?.label ?: "ohne Musik"} → ${b.mood?.label ?: "ohne Musik"}, $questions Fragen ans Sprachmodell)",
                            asked.toString().trim().ifBlank { null },
                            bookId,
                            cut,
                        )
                        Log.i(
                            TAG,
                            "Szenenwechsel bei ${ScenePrompt.clock(cut)} statt ${ScenePrompt.clock(b.startMs)} " +
                                "(${a.mood ?: "NONE"} → ${b.mood ?: "NONE"}, $questions Fragen, ${(System.nanoTime() - cutStarted) / 1_000_000} ms)",
                        )
                    }
                }
            }
            _status.value = AnalysisStatus.Finished(bookId, analysed, llm != null)
            log.add(AiStep.INFO, "Analyse fertig: $analysed Abschnitte in ${"%.0f".format((System.nanoTime() - started) / 1e9)} s", bookId = bookId)
            Log.i(TAG, "Analyse fertig: $analysed Abschnitte in ${(System.nanoTime() - started) / 1_000_000} ms")
        } finally {
            whisper?.close()
            llm?.close()
            currentLlm = null
            rememberSpeed(speechNanos, speechAudioMs, modelNanos, modelWindows)
        }
    }

    /** Middle of the part of [w] that was listened to (or read). */
    private fun sampleCentre(w: WindowMood, sampleMs: Long): Long {
        val r = WindowPlanner.sampleOf(w.startMs until w.endMs, sampleMs)
        return (r.first + r.last) / 2
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
            log.add(
                AiStep.ASKED,
                "Sprachmodell gefragt: ${windows.size} ${if (windows.size == 1) "Abschnitt" else "Abschnitte"} ab " +
                    "${ScenePrompt.clock(windows.first().startMs)} (${llm.countTokens(prompt)} Token" +
                    (if (corrections.isNotEmpty()) ", ${corrections.size} deiner Korrekturen als Beispiel" else "") + ")",
                prompt,
                book.id,
                windows.first().startMs,
            )
            // Greedy decoding: deterministic and the fastest path through the JSON grammar.
            val asked = System.nanoTime()
            val output = llm.generate(prompt, ScenePrompt.grammar(windows.size), maxTokens(windows.size), temperature = 0f)
            log.add(
                AiStep.ANSWER,
                "Antwort nach ${"%.1f".format((System.nanoTime() - asked) / 1e9)} s" + if (output.isNullOrBlank()) " – leer" else "",
                output,
                book.id,
                windows.first().startMs,
            )
            val parsed = output?.let { ScenePrompt.parse(it, windows) }
            if (parsed != null) {
                if (parsed.story.isNotBlank()) {
                    repo.saveStory(book.id, parsed.story)
                    log.add(AiStep.INFO, "Zusammenfassung „Was bisher geschah“ aktualisiert", parsed.story, book.id)
                }
                // Where the model saw no clear place or no cues, words in the text may still name them;
                // without a pace the mood and its strength give one.
                val result = parsed.windows.mapIndexed { i, w ->
                    w.copy(
                        setting = w.setting ?: KeywordMoods.settingOf(windows[i].text),
                        cues = w.cues.ifEmpty { KeywordMoods.cuesOf(windows[i].text) },
                        pace = w.pace ?: Pace.of(w.mood, w.level),
                    )
                }
                result.forEachIndexed { i, w ->
                    val fromWords = parsed.windows[i].setting == null && w.setting != null
                    log.add(AiStep.SCENE, sceneLine(w) + if (fromWords) " (Ort aus Ortswörtern im Text)" else "", null, book.id, w.startMs)
                }
                Log.i(
                    TAG,
                    "Szenen: " + result.joinToString {
                        "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}/${it.level}@${it.setting ?: "?"} ${it.title} " +
                            "[${it.cues.joinToString("+")}/${it.pace ?: "?"}]"
                    },
                )
                return result
            }
            Log.w(TAG, "Language model gave no usable answer, using keywords")
            log.add(AiStep.PROBLEM, "Antwort des Sprachmodells nicht verwertbar – Szenen nach Schlüsselwörtern", output, book.id, windows.first().startMs)
        }
        return windows.map { w ->
            val (mood, level) = KeywordMoods.classify(w.text)
            WindowMood(
                w.startMs, w.endMs, mood, level, "", SceneMerger.SOURCE_KEYWORDS, KeywordMoods.settingOf(w.text),
                KeywordMoods.cuesOf(w.text), Pace.of(mood, level),
            )
        }.also { list ->
            list.forEach { log.add(AiStep.SCENE, sceneLine(it) + " (nach Schlüsselwörtern)", null, book.id, it.startMs) }
            Log.i(TAG, "Szenen (Schlüsselwörter): " + list.joinToString { "${ScenePrompt.clock(it.startMs)}=${it.mood ?: "NONE"}@${it.setting ?: "?"}" })
        }
    }

    /** Room for the answer: the story plus per window mood, level, place, pace, cues and title. */
    private fun maxTokens(windows: Int) = 120 + 72 * windows

    /** "0:00–2:00: Ruhig · Spannung 1 · Ort „Wald“ – „Kindheit im Garten“" for the AI log. */
    private fun sceneLine(w: WindowMood): String {
        val what = w.mood?.let { "${it.label} · Spannung ${w.level.coerceIn(1, 3)} von 3" } ?: "keine Musik"
        val place = w.setting?.let { "Ort „${it.label}“" } ?: "Ort unklar"
        val music = (w.cues.map { it.label } + listOfNotNull(w.pace?.let { "Tempo ${it.label}" })).takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = " · Musik: ") ?: ""
        return "${ScenePrompt.clock(w.startMs)}–${ScenePrompt.clock(w.endMs)}: $what · $place$music" + (w.title.takeIf { it.isNotBlank() }?.let { " – „$it“" } ?: "")
    }

    private fun formatMinutes(ms: Long): String {
        val minutes = ms / 60_000f
        return if (minutes == minutes.toInt().toFloat()) "${minutes.toInt()} Min." else "${"%.1f".format(minutes)} Min."
    }

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
                    log.add(AiStep.ASKED, "Rückblick „Was bisher geschah“ bis ${ScenePrompt.clock(positionMs)} erbeten", prompt, bookId, positionMs)
                    val answer = llm.generate(prompt, null, maxTokens = 400, temperature = 0.4f)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: error("Das Sprachmodell hat keine Antwort geliefert")
                    log.add(AiStep.ANSWER, "Rückblick geschrieben", answer, bookId, positionMs)
                    answer
                } finally {
                    currentLlm = null
                    llm.close()
                }
            }
        }
    }

    // ------------------------------------------------------------------ "Frag das Buch"

    /** The model stays loaded while the listener asks one question after the other. */
    private var chatLlm: LlamaNative? = null
    private var chatModelPath: String? = null

    /** Frees the memory of the question model (when the side window closes). */
    fun releaseAssistant() {
        scope.launch(native) {
            chatLlm?.close()
            chatLlm = null
            chatModelPath = null
        }
    }

    /**
     * Answers a question about the book or turns a command into an action. Simple commands are
     * recognised without the model. With [spoilerFree] only what was transcribed up to [positionMs]
     * is given to the model. The look-ahead analysis pauses meanwhile and starts again by itself.
     */
    suspend fun ask(bookId: Long, positionMs: Long, question: String, history: List<AssistantTurn>, spoilerFree: Boolean): Result<AssistantReply> {
        if (evalJob?.isActive == true) return Result.failure(IllegalStateException("Der Modelltest läuft gerade – bitte kurz warten"))
        BookAssistant.direct(question)?.let {
            log.add(AiStep.INFO, "Befehl ohne Sprachmodell erkannt: „$question“ → ${it.action}${it.value.takeIf { v -> v.isNotEmpty() }?.let { v -> " $v" } ?: ""}", null, bookId, positionMs)
            return Result.success(it)
        }
        stop()
        return withContext(native) {
            runCatching {
                val details = repo.bookDetails(bookId) ?: error("Hörbuch nicht gefunden")
                val book = details.book
                val file = models.fileFor(settings.current.llmModel)
                    ?: error("Für Fragen wird ein Sprachmodell gebraucht – bitte unter „Hintergrundmusik → KI“ eines herunterladen")
                val llm = chatLlm?.takeIf { chatModelPath == file.absolutePath } ?: run {
                    chatLlm?.close()
                    val started = System.nanoTime()
                    (LlamaNative.load(file.absolutePath, CONTEXT) ?: error("Sprachmodell konnte nicht geladen werden")).also {
                        chatLlm = it
                        chatModelPath = file.absolutePath
                        log.add(AiStep.INFO, "Sprachmodell für Fragen geladen (${"%.1f".format((System.nanoTime() - started) / 1e9)} s)", null, bookId)
                    }
                }
                val until = if (spoilerFree) positionMs else Long.MAX_VALUE
                val windows = repo.transcripts(bookId)
                val passages = BookAssistant.passages(question, windows, until)
                // The running summary may already know later parts when the analysis went far ahead.
                val analysedUntil = repo.analyzedRanges(bookId).maxOfOrNull { it.last } ?: 0L
                val story = repo.story(bookId).takeIf { !spoilerFree || analysedUntil <= positionMs + STORY_SLACK_MS }
                val chapter = details.chapters.lastOrNull { it.startMs <= positionMs }
                var keep = passages
                var prompt: String
                do {
                    prompt = llm.chatPrompt(
                        BookAssistant.SYSTEM,
                        BookAssistant.user(book.title, book.author, story, positionMs, chapter?.title, details.chapters.size, keep, history, question, spoilerFree),
                    )
                    if (llm.countTokens(prompt) + ModelEval.ASSISTANT_MAX_TOKENS <= llm.contextSize || keep.isEmpty()) break
                    keep = keep.drop(1)
                } while (true)
                log.add(AiStep.ASKED, "Frage ans Buch: „$question“ (${keep.size} passende Stellen, ${llm.countTokens(prompt)} Token)", prompt, bookId, positionMs)
                currentLlm = llm
                val asked = System.nanoTime()
                val output = try {
                    llm.generate(prompt, BookAssistant.grammar(), ModelEval.ASSISTANT_MAX_TOKENS, temperature = 0f)
                } finally {
                    currentLlm = null
                }
                log.add(AiStep.ANSWER, "Antwort nach ${"%.1f".format((System.nanoTime() - asked) / 1e9)} s", output, bookId, positionMs)
                output?.let { BookAssistant.parse(it) } ?: error("Das Sprachmodell hat keine verwertbare Antwort gegeben")
            }
        }
    }

    // ------------------------------------------------------------------ "Modelle testen"

    private val evalPrefs = context.getSharedPreferences("model_eval", Context.MODE_PRIVATE)
    private val _evalResults = MutableStateFlow(loadEvalResults())

    /** The last test result per model id. */
    val evalResults: StateFlow<Map<String, EvalReport>> = _evalResults.asStateFlow()
    private val _evalProgress = MutableStateFlow<EvalProgress?>(null)
    val evalProgress: StateFlow<EvalProgress?> = _evalProgress.asStateFlow()
    private var evalJob: Job? = null

    private fun loadEvalResults(): Map<String, EvalReport> = runCatching {
        (MiniJson.parse(evalPrefs.getString("results", "{}") ?: "{}") as Map<*, *>).mapNotNull { (k, v) ->
            (v as? Map<*, *>)?.let { EvalReport.fromMap(it) }?.let { k as String to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    fun cancelEvaluation() {
        currentLlm?.cancel()
        evalJob?.cancel()
        evalJob = null
        _evalProgress.value = null
    }

    /**
     * Runs the model test (a German test story with known answers) on the given installed models,
     * one after the other, and keeps understanding and speed per model.
     */
    fun evaluate(ids: List<String>, quick: Boolean) {
        if (evalJob?.isActive == true) return
        stop()
        evalJob = scope.launch(native) {
            val todo = ids.mapNotNull { id -> models.fileFor(id)?.let { Triple(id, models.info(id)?.label ?: id, it) } }
            val steps = ModelEval.batches(quick).size + (if (quick) ModelEval.quickQuestions().size + ModelEval.quickCommands().size else ModelEval.questions.size + ModelEval.commands.size)
            log.add(AiStep.INFO, "Modelltest gestartet (${if (quick) "kurz" else "ganz"}): ${todo.joinToString { it.second }}")
            try {
                todo.forEachIndexed { i, (id, label, file) ->
                    val report = evalModel(label, file, quick) { step -> _evalProgress.value = EvalProgress(label, i + 1, todo.size, step, steps) }
                    _evalResults.value = _evalResults.value + (id to report)
                    evalPrefs.edit().putString("results", MiniJson.write(_evalResults.value.mapValues { it.value.toMap() })).apply()
                    log.add(
                        AiStep.INFO,
                        "Modelltest $label: Verstehen ${(report.understanding * 100).toInt()} %, ${"%.1f".format(report.secondsPerQuestion)} s je Frage, " +
                            "${"%.0f".format(report.promptTokensPerSecond)} Token/s lesen, ${"%.1f".format(report.generatedTokensPerSecond)} Token/s schreiben",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Model test failed", e)
                log.add(AiStep.PROBLEM, "Modelltest fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _evalProgress.value = null
            }
        }
    }

    private suspend fun evalModel(label: String, file: java.io.File, quick: Boolean, progress: (Int) -> Unit): EvalReport {
        chatLlm?.close()
        chatLlm = null
        chatModelPath = null
        val loadStart = System.nanoTime()
        val llm = LlamaNative.load(file.absolutePath, CONTEXT) ?: error("$label konnte nicht geladen werden")
        val load = (System.nanoTime() - loadStart) / 1e9f
        currentLlm = llm
        var step = 0
        // The prompt is read once alone (one token), then answered from the cache: so reading and
        // writing are timed apart, as the phone does them.
        var coldTokens = 0
        var coldSeconds = 0.0
        var genTokens = 0
        var genSeconds = 0.0
        fun timed(system: String, user: String, grammar: String, maxTokens: Int, cold: Boolean): Pair<String?, Double> {
            val prompt = llm.chatPrompt(system, user)
            val t0 = System.nanoTime()
            llm.generate(prompt, null, maxTokens = 1, temperature = 0f)
            val read = (System.nanoTime() - t0) / 1e9
            val t1 = System.nanoTime()
            val out = llm.generate(prompt, grammar, maxTokens, temperature = 0f)
            val write = (System.nanoTime() - t1) / 1e9
            if (cold) {
                coldTokens += llm.countTokens(prompt)
                coldSeconds += read
            }
            genTokens += out?.let { llm.countTokens(it) } ?: 0
            genSeconds += write
            return out to read + write
        }
        try {
            var moods = 0
            var places = 0
            var placeCount = 0
            var levels = 0
            var cues = 0
            var cueCount = 0
            var windows = 0
            var valid = 0
            val batches = ModelEval.batches(quick)
            var sceneSeconds = 0.0
            batches.forEachIndexed { i, r ->
                coroutineContext.ensureActive()
                progress(++step)
                val (out, sec) = timed(ScenePrompt.SYSTEM, ModelEval.sceneUser(r), ScenePrompt.grammar(r.count()), ModelEval.sceneMaxTokens(r.count()), cold = i == 0)
                sceneSeconds += sec
                val h = ModelEval.scoreScenes(r, out)
                moods += h.moods; places += h.places; placeCount += h.placeCount; levels += h.levels
                cues += h.cues; cueCount += h.cueCount; windows += h.windows; if (h.valid) valid++
            }
            val questions = if (quick) ModelEval.quickQuestions() else ModelEval.questions
            val commands = if (quick) ModelEval.quickCommands() else ModelEval.commands
            var right = 0
            var traps = 0
            var safe = 0
            var askSeconds = 0.0
            questions.forEachIndexed { i, q ->
                coroutineContext.ensureActive()
                progress(++step)
                val (out, sec) = timed(BookAssistant.SYSTEM, ModelEval.assistantUser(q.question, q.positionMs), BookAssistant.grammar(), ModelEval.ASSISTANT_MAX_TOKENS, cold = i == 0)
                askSeconds += sec
                val reply = out?.let { BookAssistant.parse(it) }
                if (ModelEval.answerRight(q, reply)) right++
                if (q.forbid.isNotEmpty()) {
                    traps++
                    if (ModelEval.spoilerSafe(q, reply)) safe++
                }
            }
            var done = 0
            for (c in commands) {
                coroutineContext.ensureActive()
                progress(++step)
                val (out, sec) = timed(BookAssistant.SYSTEM, ModelEval.assistantUser(c.text, c.positionMs), BookAssistant.grammar(), ModelEval.ASSISTANT_MAX_TOKENS, cold = false)
                askSeconds += sec
                if (ModelEval.commandRight(c, out?.let { BookAssistant.parse(it) })) done++
            }
            fun share(a: Int, b: Int) = if (b == 0) 0f else a.toFloat() / b
            return EvalReport(
                label, share(moods, windows), share(places, placeCount), share(levels, windows), share(cues, cueCount), share(valid, batches.size),
                share(right, questions.size), if (traps == 0) 1f else share(safe, traps), share(done, commands.size),
                if (coldSeconds > 0) (coldTokens / coldSeconds).toFloat() else 0f, if (genSeconds > 0) (genTokens / genSeconds).toFloat() else 0f,
                (sceneSeconds / batches.size).toFloat(), (askSeconds / (questions.size + commands.size)).toFloat(), load, quick,
            )
        } finally {
            currentLlm = null
            llm.close()
        }
    }

    companion object {
        private const val TAG = "LauscherAnalyse"

        /** The running summary is only used spoiler-free when the analysis is at most this far ahead. */
        private const val STORY_SLACK_MS = 10 * 60_000L
        /** Windows per language-model call: the instructions are read once for all of them. */
        private const val BATCH = 8
        private const val CONTEXT = 4096
        private const val SPEECH_CHECK_MS = 90_000L

        /** Bisection steps per scene change: the error shrinks to an eighth of a window. */
        private const val CUT_STEPS = 2

        /** Length of each probe (listened to or read) while looking for a scene change. */
        private const val CUT_PROBE_MS = 12_000L

        /** A faster speed must recognise at least this share of the words heard at normal speed. */
        private const val MIN_SPEED_AGREEMENT = 0.7f

        /** Samples with fewer words (music, silence) say nothing about the speed. */
        private const val SPEED_PROBE_WORDS = 15

        /** The first model call covers only this many windows, so music for the current spot is ready sooner. */
        private const val FIRST_BATCH = 2

        /** With a book text: listen briefly where the nearest anchor is further away than this. */
        private const val ALIGN_EVERY_MS = 10 * 60_000L
        private const val ALIGN_SAMPLE_MS = 12_000L

        /** How far around the expected place the heard words are searched (in time). */
        private const val ALIGN_RADIUS_MS = 15 * 60_000L
    }
}
