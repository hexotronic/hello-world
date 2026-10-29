package io.github.hexotronic.lauscher.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.MainActivity
import io.github.hexotronic.lauscher.R
import io.github.hexotronic.lauscher.audio.EnhancerAudioProcessor
import io.github.hexotronic.lauscher.data.Book
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.future

/** In-process access from the UI and widget to the running player. */
object PlaybackHub {
    private val _core = MutableStateFlow<AudiobookPlayerCore?>(null)
    val core: StateFlow<AudiobookPlayerCore?> = _core.asStateFlow()

    internal fun attach(core: AudiobookPlayerCore) {
        _core.value = core
    }

    internal fun detach(core: AudiobookPlayerCore) {
        if (_core.value === core) _core.value = null
    }
}

/**
 * Foreground media service: owns ExoPlayer and the MediaLibrarySession, which provides the
 * notification, lock screen controls, Bluetooth/headset buttons, Wear OS and Android Auto.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = MainScope()
    private lateinit var core: AudiobookPlayerCore
    private var session: MediaLibrarySession? = null
    private val app get() = application as LauscherApp

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = buildPlayer()
        core = AudiobookPlayerCore(this, exoPlayer)

        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val librarySession = MediaLibrarySession.Builder(this, core.player, LibraryCallback())
            .setSessionActivity(openPlayer)
            .build()
        librarySession.setCustomLayout(
            ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.action_rewind))
                    .setIconResId(R.drawable.ic_rewind)
                    .setSessionCommand(REWIND)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.action_forward))
                    .setIconResId(R.drawable.ic_forward)
                    .setSessionCommand(FORWARD)
                    .build(),
            ),
        )
        session = librarySession
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
        PlaybackHub.attach(core)
    }

    private fun buildPlayer(): ExoPlayer {
        val enhancer = EnhancerAudioProcessor()
        val renderers = RenderersFactory { handler, _, audioListener, _, _ ->
            val sink = DefaultAudioSink.Builder(this)
                .setAudioProcessors(arrayOf<AudioProcessor>(enhancer))
                .build()
            arrayOf<Renderer>(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener, sink))
        }
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        return ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlaybackHub.detach(core)
        core.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val commands = base.availableSessionCommands.buildUpon().add(REWIND).add(FORWARD).build()
            return MediaSession.ConnectionResult.accept(commands, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                REWIND.customAction -> core.rewind()
                FORWARD.customAction -> core.forward()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folderItem(ROOT, "Lauscher"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = when (parentId) {
                ROOT -> listOf(folderItem(RECENT, "Weiterhören"), folderItem(ALL, "Alle Hörbücher"))
                RECENT -> app.repository.recentBooks(12).filter { it.lastPlayedAt > 0 && !it.finished }.map(::bookItem)
                ALL -> app.repository.recentBooks(Int.MAX_VALUE).sortedBy { it.title.lowercase() }.map(::bookItem)
                else -> emptyList()
            }
            LibraryResult.ofItemList(items, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val book = AudiobookPlayerCore.bookIdOf(mediaId)?.let { app.repository.book(it) }
            if (book != null) LibraryResult.ofItem(bookItem(book), null) else LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val first = mediaItems.firstOrNull()
            val query = first?.requestMetadata?.searchQuery
            val bookId = first?.mediaId?.let(AudiobookPlayerCore::bookIdOf)
                ?: query?.let { findBook(it)?.id }
                ?: app.repository.lastPlayedBook()?.id
                ?: error("Kein Hörbuch gefunden")
            core.prepareBook(bookId) ?: error("Hörbuch $bookId nicht verfügbar")
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            val bookId = mediaItems.firstOrNull()?.mediaId?.let(AudiobookPlayerCore::bookIdOf)
                ?: error("Unbekanntes Medium")
            core.prepareBook(bookId)?.mediaItems?.toMutableList() ?: error("Hörbuch $bookId nicht verfügbar")
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val book = app.repository.lastPlayedBook() ?: error("Noch kein Hörbuch gehört")
            core.prepareBook(book.id) ?: error("Hörbuch nicht verfügbar")
        }
    }

    private suspend fun findBook(query: String): Book? {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return null
        val books = app.repository.recentBooks(Int.MAX_VALUE)
        return books.firstOrNull { it.title.lowercase() == q }
            ?: books.firstOrNull { it.title.lowercase().contains(q) }
            ?: books.firstOrNull { it.author?.lowercase()?.contains(q) == true }
    }

    private fun folderItem(id: String, title: String) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_AUDIO_BOOKS)
                .build(),
        )
        .build()

    private fun bookItem(book: Book) = MediaItem.Builder()
        .setMediaId("book:${book.id}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(book.title)
                .setArtist(book.author)
                .setSubtitle(book.author)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
                .build(),
        )
        .build()

    companion object {
        private const val ROOT = "root"
        private const val RECENT = "recent"
        private const val ALL = "all"
        val REWIND = SessionCommand("lauscher.REWIND", Bundle.EMPTY)
        val FORWARD = SessionCommand("lauscher.FORWARD", Bundle.EMPTY)
    }
}
