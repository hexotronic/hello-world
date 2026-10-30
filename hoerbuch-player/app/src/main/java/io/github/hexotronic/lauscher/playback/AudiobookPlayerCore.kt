package io.github.hexotronic.lauscher.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.BookDetails
import io.github.hexotronic.lauscher.data.Chapter
import io.github.hexotronic.lauscher.data.Track
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.music.MusicEngine
import io.github.hexotronic.lauscher.music.MusicMode
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import io.github.hexotronic.lauscher.widget.WidgetUpdater
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max

data class NowPlaying(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val speed: Float = 1f,
    val skipSilence: Boolean = false,
    val error: String? = null,
    /** Position before the last big jump, offered as "undo". */
    val undoPositionMs: Long? = null,
    /** Scene at the current position (from the music analysis), if known. */
    val scene: Scene? = null,
    /** True while background music is audible. */
    val musicPlaying: Boolean = false,
    /** Downloaded piece that is playing as background music (for its credit), if any. */
    val musicTrackId: String? = null,
) {
    val chapter: Chapter? get() = chapters.getOrNull(chapterIndex)
    val chapterPositionMs: Long get() = chapter?.let { (positionMs - it.startMs).coerceAtLeast(0) } ?: positionMs
    val chapterDurationMs: Long get() = chapter?.durationMs ?: durationMs
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
}

enum class SleepMode { OFF, TIMER, END_OF_CHAPTER }

data class SleepState(val mode: SleepMode = SleepMode.OFF, val remainingMs: Long = 0, val totalMs: Long = 0)

/**
 * Audiobook logic on top of ExoPlayer: book-wide positions across files, chapters, smart
 * rewind, sleep timer, progress saving, listening statistics and the widget.
 * Lives as long as the playback service; the UI talks to it through [PlaybackHub].
 */
@OptIn(UnstableApi::class)
class AudiobookPlayerCore(private val context: Context, val exoPlayer: ExoPlayer) {
    private val app = context.applicationContext as LauscherApp
    private val repo = app.repository
    private val settingsStore = app.settings
    private val scope = MainScope()

    /** The player handed to the MediaSession: maps headset/notification buttons to audiobook actions. */
    val player: Player = BookPlayer(exoPlayer)

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()

    private var details: BookDetails? = null
    private var tickerJob: Job? = null
    private var lastTick = 0L
    private var lastSavedAt = 0L
    private var pendingListenMs = 0L
    private var pausedAt = 0L
    private var suppressedAt = 0L
    private var undoPosition: Long? = null
    private var undoSetAt = 0L
    private var error: String? = null
    private val shake = ShakeDetector(context) { onShake() }
    private val music = MusicEngine(app.musicLibrary)
    private var scenes: List<Scene> = emptyList()
    private var scenesJob: Job? = null
    private var lastChapterIndex = -1
    private var lastAheadCheck = 0L
    private var previewUntil = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                error = null
                if (suppressedAt > 0) {
                    // Resumed after a call or navigation announcement.
                    applySmartRewind(suppressedAt)
                    suppressedAt = 0
                }
                maybeStartAutoSleepTimer()
                startTicker()
            } else {
                if (exoPlayer.playWhenReady && exoPlayer.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                    suppressedAt = SystemClock.elapsedRealtime()
                } else if (!exoPlayer.playWhenReady) {
                    pausedAt = SystemClock.elapsedRealtime()
                }
                saveProgress()
                flushStats()
            }
            updateShake()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS &&
                !settingsStore.current.resumeAfterCall
            ) {
                exoPlayer.pause()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> onBookEnded()
                Player.STATE_READY -> checkTrackDuration()
                else -> Unit
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val bookId = mediaItem?.mediaId?.let(::bookIdOf) ?: return
            if (details?.book?.id != bookId) {
                scope.launch {
                    details = repo.bookDetails(bookId)
                    refresh()
                }
            }
            saveProgress()
        }

        override fun onPlayerError(e: PlaybackException) {
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                -> "Datei nicht gefunden – bitte Bibliothek neu scannen"
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                -> "Dieses Audioformat wird nicht unterstützt"
                else -> "Wiedergabefehler: ${e.errorCodeName}"
            }
            refresh()
        }
    }

    init {
        exoPlayer.addListener(listener)
        scope.launch {
            settingsStore.settings.collect { s -> exoPlayer.skipSilenceEnabled = s.skipSilence }
        }
        scope.launch {
            var source = app.musicSettings.current.source
            app.musicSettings.settings.collect { s ->
                // Another source, or the playing piece was just hidden: choose again.
                if (s.source != source || music.currentTrackId in s.hiddenTracks) {
                    source = s.source
                    music.refresh()
                }
                refresh()
            }
        }
    }

    // ------------------------------------------------------------------ loading

    /** Loads a book (resuming at its saved position) into the player. */
    suspend fun loadBook(bookId: Long, play: Boolean, startAtMs: Long? = null): Boolean {
        val prepared = prepareBook(bookId, startAtMs) ?: return false
        exoPlayer.setMediaItems(prepared.mediaItems, prepared.startIndex, prepared.startPositionMs)
        exoPlayer.prepare()
        if (play) play() else refresh()
        return true
    }

    /**
     * Prepares the core for a book and returns the playlist; used both by [loadBook] and by
     * the MediaSession (Android Auto, Bluetooth resume), which then sets the items itself.
     */
    suspend fun prepareBook(bookId: Long, startAtMs: Long? = null): MediaSession.MediaItemsWithStartPosition? {
        val loaded = repo.bookDetails(bookId) ?: return null
        if (loaded.tracks.isEmpty()) return null
        if (details?.book?.id != bookId) {
            saveProgress()
            flushStats()
        }
        var start = startAtMs ?: loaded.book.positionMs
        var d = loaded
        if (loaded.book.finished && startAtMs == null) {
            repo.resetProgress(bookId)
            d = loaded.copy(book = loaded.book.copy(finished = false, positionMs = 0))
            start = 0
        }
        details = d
        observeScenes(d.book.id)
        lastChapterIndex = -1
        undoPosition = null
        pausedAt = 0
        error = null
        exoPlayer.setPlaybackSpeed(speedFor(d.book))
        app.sound.onBookLoaded(d.book.id, d.book.audioSettingsJson)
        repo.savePosition(bookId, start, played = true)
        val (index, offset) = locate(d.tracks, start)
        return MediaSession.MediaItemsWithStartPosition(mediaItems(d), index, offset)
    }

    private fun mediaItems(d: BookDetails): List<MediaItem> {
        val artwork = d.book.coverPath?.let { Uri.fromFile(File(it)) }
        return d.tracks.map { t ->
            MediaItem.Builder()
                .setMediaId("book:${d.book.id}:${t.index}")
                .setUri(Uri.parse(t.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(if (d.tracks.size > 1) t.title else d.book.title)
                        .setArtist(d.book.author)
                        .setAlbumTitle(d.book.title)
                        .setAlbumArtist(d.book.author)
                        .setArtworkUri(artwork)
                        .setTrackNumber(t.index + 1)
                        .setTotalTrackCount(d.tracks.size)
                        .setIsPlayable(true)
                        .setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                        .build(),
                )
                .build()
        }
    }

    // ------------------------------------------------------------------ transport

    fun play() {
        if (details == null) return
        beforePlay()
        exoPlayer.play()
    }

    fun pause() = exoPlayer.pause()

    fun togglePlayPause() = if (exoPlayer.playWhenReady) pause() else play()

    /** Called for every "play" coming from the app, the notification, headsets or the car. */
    internal fun beforePlay() {
        when (exoPlayer.playbackState) {
            Player.STATE_IDLE -> exoPlayer.prepare()
            Player.STATE_ENDED -> seekTo(0, rememberUndo = false)
            else -> Unit
        }
        if (pausedAt > 0) {
            applySmartRewind(pausedAt)
            pausedAt = 0
        }
    }

    private fun applySmartRewind(since: Long) {
        if (!settingsStore.current.smartRewind) return
        val paused = SystemClock.elapsedRealtime() - since
        val back = when {
            paused < 10_000 -> 0L
            paused < 60_000 -> 2_000L
            paused < 5 * 60_000 -> 5_000L
            paused < 60 * 60_000 -> 10_000L
            else -> 20_000L
        }
        if (back > 0) seekTo(max(0, currentPosition() - back), rememberUndo = false)
    }

    fun currentPosition(): Long {
        val d = details ?: return 0
        val track = d.tracks.getOrNull(exoPlayer.currentMediaItemIndex) ?: return 0
        return track.startMs + exoPlayer.currentPosition.coerceAtLeast(0)
    }

    fun seekTo(positionMs: Long, rememberUndo: Boolean = true) {
        val d = details ?: return
        val from = currentPosition()
        val target = positionMs.coerceIn(0, max(0, d.book.durationMs - 500))
        // A deliberate jump while paused makes the smart rewind on resume pointless.
        if (rememberUndo) pausedAt = 0
        if (rememberUndo && abs(target - from) > 60_000) {
            undoPosition = from
            undoSetAt = SystemClock.elapsedRealtime()
        }
        val (index, offset) = locate(d.tracks, target)
        exoPlayer.seekTo(index, offset)
        refresh()
    }

    fun seekBy(deltaMs: Long) = seekTo(currentPosition() + deltaMs, rememberUndo = false)

    fun rewind() = seekBy(-settingsStore.current.rewindSeconds * 1000L)

    fun forward() = seekBy(settingsStore.current.forwardSeconds * 1000L)

    fun undoJump() {
        val target = undoPosition ?: return
        val from = currentPosition()
        seekTo(target, rememberUndo = false)
        undoPosition = from
        undoSetAt = SystemClock.elapsedRealtime()
        refresh()
    }

    fun nextChapter() {
        val chapters = details?.chapters ?: return
        val i = chapterIndexAt(chapters, currentPosition())
        pausedAt = 0
        if (i + 1 < chapters.size) seekTo(chapters[i + 1].startMs, rememberUndo = false)
    }

    fun previousChapter() {
        val chapters = details?.chapters ?: return
        if (chapters.isEmpty()) return
        val pos = currentPosition()
        val i = chapterIndexAt(chapters, pos)
        val target = if (pos - chapters[i].startMs > 3_000 || i == 0) chapters[i].startMs else chapters[i - 1].startMs
        pausedAt = 0
        seekTo(target, rememberUndo = false)
    }

    fun jumpToChapter(index: Int) {
        val chapter = details?.chapters?.getOrNull(index) ?: return
        seekTo(chapter.startMs)
    }

    internal fun onNextButton() = if (settingsStore.current.headsetSkipsChapters) nextChapter() else forward()

    internal fun onPreviousButton() = if (settingsStore.current.headsetSkipsChapters) previousChapter() else rewind()

    fun setSpeed(speed: Float) {
        val s = (Math.round(speed * 100) / 100f).coerceIn(MIN_SPEED, MAX_SPEED)
        exoPlayer.setPlaybackSpeed(s)
        val d = details ?: return
        details = d.copy(book = d.book.copy(speed = s))
        app.appScope.launch { repo.setSpeed(d.book.id, s) }
        refresh()
    }

    fun setSkipSilence(enabled: Boolean) {
        settingsStore.update { it.copy(skipSilence = enabled) }
    }

    private fun speedFor(book: Book) = if (book.speed > 0f) book.speed else settingsStore.current.defaultSpeed

    // ------------------------------------------------------------------ bookmarks

    suspend fun addBookmark(title: String? = null, note: String = ""): Long? {
        val d = details ?: return null
        val pos = currentPosition()
        val chapterTitle = d.chapters.getOrNull(chapterIndexAt(d.chapters, pos))?.title
        return repo.addBookmark(d.book.id, pos, title?.takeIf { it.isNotBlank() } ?: chapterTitle ?: "Lesezeichen", note)
    }

    // ------------------------------------------------------------------ sleep timer

    fun startSleepTimer(minutes: Int) {
        val total = minutes * 60_000L
        _sleep.value = SleepState(SleepMode.TIMER, total, total)
        exoPlayer.volume = 1f
        updateShake()
        startTicker()
    }

    fun sleepAtEndOfChapter() {
        _sleep.value = SleepState(SleepMode.END_OF_CHAPTER, chapterRemaining(), 0)
        exoPlayer.volume = 1f
        updateShake()
        startTicker()
    }

    fun extendSleepTimer(minutes: Int) {
        val s = _sleep.value
        if (s.mode != SleepMode.TIMER) {
            startSleepTimer(minutes)
            return
        }
        _sleep.value = s.copy(remainingMs = s.remainingMs + minutes * 60_000L, totalMs = s.totalMs + minutes * 60_000L)
        exoPlayer.volume = 1f
    }

    fun cancelSleepTimer() {
        _sleep.value = SleepState()
        exoPlayer.volume = 1f
        updateShake()
    }

    private fun chapterRemaining(): Long {
        val d = details ?: return 0
        val pos = currentPosition()
        val chapter = d.chapters.getOrNull(chapterIndexAt(d.chapters, pos)) ?: return 0
        return ((chapter.endMs - pos) / exoPlayer.playbackParameters.speed).toLong().coerceAtLeast(0)
    }

    private fun sleepTick(elapsed: Long) {
        val s = _sleep.value
        val fade = settingsStore.current.sleepFadeOut
        when (s.mode) {
            SleepMode.OFF -> return
            SleepMode.TIMER -> {
                if (!exoPlayer.isPlaying) return
                val remaining = s.remainingMs - elapsed
                if (remaining <= 0) {
                    fallAsleep()
                } else {
                    _sleep.value = s.copy(remainingMs = remaining)
                    exoPlayer.volume = if (fade && remaining < FADE_MS) max(0.05f, remaining.toFloat() / FADE_MS) else 1f
                }
            }
            SleepMode.END_OF_CHAPTER -> {
                val remaining = chapterRemaining()
                _sleep.value = s.copy(remainingMs = remaining)
                if (exoPlayer.isPlaying) {
                    exoPlayer.volume = if (fade && remaining < FADE_MS / 2) max(0.05f, remaining.toFloat() / (FADE_MS / 2)) else 1f
                }
                if (remaining < 300 && exoPlayer.isPlaying) {
                    val d = details
                    val chapters = d?.chapters.orEmpty()
                    val next = chapters.getOrNull(chapterIndexAt(chapters, currentPosition()) + 1)
                    fallAsleep()
                    if (next != null) seekTo(next.startMs, rememberUndo = false)
                }
            }
        }
    }

    private fun fallAsleep() {
        exoPlayer.pause()
        exoPlayer.volume = 1f
        _sleep.value = SleepState()
        updateShake()
        val d = details ?: return
        if (settingsStore.current.sleepBookmark) {
            val pos = currentPosition()
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            app.appScope.launch { repo.addBookmark(d.book.id, pos, "Eingeschlafen um $time") }
        }
    }

    private fun onShake() {
        val s = _sleep.value
        if (s.mode == SleepMode.OFF) return
        startSleepTimer(settingsStore.current.sleepDefaultMinutes)
    }

    private fun updateShake() {
        shake.setActive(
            settingsStore.current.sleepShakeToExtend && _sleep.value.mode != SleepMode.OFF && exoPlayer.isPlaying,
        )
    }

    private fun maybeStartAutoSleepTimer() {
        val s = settingsStore.current
        if (!s.sleepAuto || _sleep.value.mode != SleepMode.OFF) return
        val hour = LocalTime.now().hour
        val inWindow = if (s.sleepAutoFromHour <= s.sleepAutoToHour) {
            hour >= s.sleepAutoFromHour && hour < s.sleepAutoToHour
        } else {
            hour >= s.sleepAutoFromHour || hour < s.sleepAutoToHour
        }
        if (inWindow) startSleepTimer(s.sleepDefaultMinutes)
    }

    // ------------------------------------------------------------------ periodic work

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        lastTick = SystemClock.elapsedRealtime()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - lastTick
                lastTick = now
                val playing = exoPlayer.isPlaying
                if (playing) pendingListenMs += elapsed
                sleepTick(elapsed)
                refresh()
                if (playing && now - lastSavedAt > SAVE_INTERVAL_MS) saveProgress()
                if (pendingListenMs > STATS_INTERVAL_MS) flushStats()
                if (!playing && _sleep.value.mode == SleepMode.OFF && now > previewUntil) break
            }
        }
    }

    private fun saveProgress() {
        val d = details ?: return
        val pos = currentPosition()
        lastSavedAt = SystemClock.elapsedRealtime()
        app.appScope.launch { repo.savePosition(d.book.id, pos, played = true) }
    }

    private fun flushStats() {
        val d = details ?: return
        val ms = pendingListenMs
        if (ms <= 0) return
        pendingListenMs = 0
        app.appScope.launch { repo.addListening(d.book.id, d.book.title, ms) }
    }

    private fun onBookEnded() {
        val d = details ?: return
        flushStats()
        cancelSleepTimer()
        app.appScope.launch { repo.setFinished(d.book.id, true) }
    }

    /** Corrects stored track lengths with the exact value from the decoder (VBR MP3s). */
    private fun checkTrackDuration() {
        val d = details ?: return
        val index = exoPlayer.currentMediaItemIndex
        val track = d.tracks.getOrNull(index) ?: return
        val real = exoPlayer.duration
        if (real == C.TIME_UNSET || real <= 0 || abs(real - track.durationMs) < 1500) return
        app.appScope.launch {
            repo.fixTrackDuration(d.book.id, index, track.durationMs, real)
            val fresh = repo.bookDetails(d.book.id) ?: return@launch
            if (details?.book?.id == fresh.book.id) {
                details = fresh.copy(book = fresh.book.copy(speed = details?.book?.speed ?: fresh.book.speed))
                refresh()
            }
        }
    }

    private fun observeScenes(bookId: Long) {
        scenesJob?.cancel()
        scenes = emptyList()
        scenesJob = scope.launch {
            repo.observeScenes(bookId).collect {
                scenes = it
                refresh()
            }
        }
    }

    /** Picks the background music for the current position. */
    private fun updateMusic(pos: Long, chapterIndex: Int, chapters: List<Chapter>): Scene? {
        val s = app.musicSettings.current
        val d = details
        val now = SystemClock.elapsedRealtime()
        val previewing = now < previewUntil
        val scene = SceneMerger.sceneAt(scenes, pos)
        if (previewing) {
            music.setPlaying(true)
            return scene
        }
        if (!s.enabled || d == null) {
            music.setPlaying(false)
            return scene
        }
        // The sleep timer fades the narration; the music follows.
        music.apply(s, exoPlayer.volume)
        val spec = when (s.mode) {
            MusicMode.FIXED -> if (s.fixedMood == null && s.fixedAtmosphere == null) null else {
                SoundSpec(s.fixedMood, s.fixedAtmosphere, 0.5f, d.book.id, setting = s.fixedSetting)
            }
            MusicMode.CHAPTERS -> chapters.getOrNull(chapterIndex)?.let { ch ->
                when {
                    pos - ch.startMs < CHAPTER_INTRO_MS -> SoundSpec(Mood.CALM, null, 0.4f, ch.startMs)
                    ch.endMs - pos < CHAPTER_OUTRO_MS && ch.durationMs > 2 * CHAPTER_INTRO_MS ->
                        SoundSpec(Mood.NEUTRAL, null, 0.3f, ch.startMs)
                    else -> null
                }
            }
            MusicMode.SCENES -> when {
                scene == null -> if (s.neutralFallback) SoundSpec(Mood.NEUTRAL, null, 0.3f, d.book.id) else null
                scene.mood == null -> null
                else -> SoundSpec(
                    mood = scene.mood,
                    atmosphere = if (s.placeSounds) scene.setting?.atmosphere else null,
                    intensity = scene.intensity,
                    seed = scene.startMs,
                    setting = scene.setting,
                )
            }
        }
        music.setSpec(spec)
        val playing = exoPlayer.isPlaying
        music.setPlaying(playing && spec != null)
        if (playing && s.chapterChime && lastChapterIndex >= 0 && chapterIndex != lastChapterIndex) music.chime()
        lastChapterIndex = chapterIndex
        if (playing && s.mode == MusicMode.SCENES && now - lastAheadCheck > AHEAD_CHECK_MS) {
            lastAheadCheck = now
            app.analysis.ensureAhead(d.book.id, pos)
        }
        return scene
    }

    /** Plays a few seconds of a mood or atmosphere, also while the book is paused. */
    fun previewMusic(spec: SoundSpec, seconds: Int = 12) {
        music.apply(app.musicSettings.current, 1f)
        music.setSpec(spec)
        previewUntil = SystemClock.elapsedRealtime() + seconds * 1000L
        music.setPlaying(true)
        startTicker()
    }

    /** Plays another library piece for the current scene. */
    fun skipMusicTrack() {
        music.currentTrackId?.let { app.musicLibrary.avoidNext(it) }
        music.refresh()
    }

    fun stopPreview() {
        previewUntil = 0
        refresh()
    }

    private fun refresh() {
        val d = details
        val pos = currentPosition()
        val chapters = d?.chapters.orEmpty()
        val scene = updateMusic(pos, chapterIndexAt(chapters, pos), chapters)
        if (undoPosition != null && SystemClock.elapsedRealtime() - undoSetAt > UNDO_VISIBLE_MS) undoPosition = null
        val next = NowPlaying(
            book = d?.book,
            tracks = d?.tracks.orEmpty(),
            chapters = chapters,
            chapterIndex = chapterIndexAt(chapters, pos),
            positionMs = pos,
            durationMs = d?.book?.durationMs ?: 0,
            isPlaying = exoPlayer.isPlaying,
            playWhenReady = exoPlayer.playWhenReady,
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
            speed = exoPlayer.playbackParameters.speed,
            skipSilence = exoPlayer.skipSilenceEnabled,
            error = error,
            undoPositionMs = undoPosition,
            scene = scene,
            musicPlaying = music.currentSpec != null && (exoPlayer.isPlaying || SystemClock.elapsedRealtime() < previewUntil),
            musicTrackId = music.currentTrackId,
        )
        _state.value = next
        WidgetUpdater.onState(context, next)
    }

    fun release() {
        saveProgress()
        flushStats()
        shake.setActive(false)
        music.release()
        exoPlayer.removeListener(listener)
        scope.cancel()
    }

    /** ForwardingPlayer given to the MediaSession. */
    private inner class BookPlayer(player: ExoPlayer) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                )
                .build()

        override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

        override fun play() {
            beforePlay()
            super.play()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) beforePlay()
            super.setPlayWhenReady(playWhenReady)
        }

        override fun seekToNext() = onNextButton()
        override fun seekToNextMediaItem() = onNextButton()
        override fun seekToPrevious() = onPreviousButton()
        override fun seekToPreviousMediaItem() = onPreviousButton()
        override fun seekBack() = rewind()
        override fun seekForward() = forward()
        override fun getSeekBackIncrement(): Long = settingsStore.current.rewindSeconds * 1000L
        override fun getSeekForwardIncrement(): Long = settingsStore.current.forwardSeconds * 1000L
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.5f
        private const val TICK_MS = 250L
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val STATS_INTERVAL_MS = 30_000L
        private const val FADE_MS = 20_000L
        private const val UNDO_VISIBLE_MS = 30_000L
        private const val CHAPTER_INTRO_MS = 25_000L
        private const val CHAPTER_OUTRO_MS = 15_000L
        private const val AHEAD_CHECK_MS = 20_000L

        fun bookIdOf(mediaId: String): Long? =
            mediaId.takeIf { it.startsWith("book:") }?.split(':')?.getOrNull(1)?.toLongOrNull()

        fun locate(tracks: List<Track>, positionMs: Long): Pair<Int, Long> {
            if (tracks.isEmpty()) return 0 to 0L
            val pos = positionMs.coerceAtLeast(0)
            var i = tracks.indexOfLast { it.startMs <= pos }
            if (i < 0) i = 0
            val t = tracks[i]
            var offset = pos - t.startMs
            if (t.durationMs > 0 && offset > t.durationMs) offset = t.durationMs
            return i to offset
        }

        fun chapterIndexAt(chapters: List<Chapter>, positionMs: Long): Int {
            var lo = 0
            var hi = chapters.size - 1
            var found = 0
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (chapters[mid].startMs <= positionMs) {
                    found = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return found
        }
    }
}
