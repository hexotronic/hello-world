package io.github.hexotronic.lauscher.music.library

import android.content.Context
import android.util.Log
import io.github.hexotronic.lauscher.music.MusicEngine
import io.github.hexotronic.lauscher.music.MusicSettingsStore
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.core.LibraryTrack
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.library.core.TrackPicker
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.PcmStream
import io.github.hexotronic.lauscher.music.synth.RecordingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.pow

/** Progress of downloading one or more pieces. */
data class LibraryDownload(
    val totalCount: Int,
    val doneCount: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val currentTitle: String?,
    val error: String? = null,
) {
    val running: Boolean get() = currentTitle != null
    val progress: Float get() = if (bytesTotal > 0) (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) else 0f
}

/**
 * The downloadable fantasy music: fetches pieces from the games' repositories, checks them
 * against the catalog's checksums and hands them to the music mixer.
 */
class MusicLibrary(
    private val context: Context,
    private val settings: MusicSettingsStore,
    private val scope: CoroutineScope,
) : RecordingSource {
    private val _installed = MutableStateFlow<Set<String>>(emptySet())
    val installed: StateFlow<Set<String>> = _installed.asStateFlow()
    private val _download = MutableStateFlow<LibraryDownload?>(null)
    val download: StateFlow<LibraryDownload?> = _download.asStateFlow()

    private val picker = TrackPicker()
    private val queue = ArrayDeque<LibraryTrack>()
    private var job: Job? = null

    @Volatile
    private var installedTracks: List<LibraryTrack> = emptyList()

    private val dir: File
        get() = (context.getExternalFilesDir("music") ?: File(context.filesDir, "music")).also { it.mkdirs() }

    init {
        scope.launch(Dispatchers.IO) { rescan() }
    }

    fun fileFor(id: String): File? {
        val track = MusicCatalog.byId(id) ?: return null
        return File(dir, fileName(track)).takeIf { it.isFile && it.length() == track.bytes }
    }

    fun installedBytes(): Long = installedTracks.sumOf { it.bytes }

    private fun rescan() {
        val present = MusicCatalog.tracks.filter { t -> File(dir, fileName(t)).let { it.isFile && it.length() == t.bytes } }
        installedTracks = present
        _installed.value = present.map { it.id }.toSet()
    }

    // Batch bookkeeping, guarded by [queue].
    private var workerRunning = false
    private var currentId: String? = null
    private var batchCount = 0
    private var batchBytes = 0L
    private var doneCount = 0
    private var doneBytes = 0L

    /** Queues pieces for download; pieces already present or queued are skipped. */
    fun download(ids: Collection<String>) {
        val wanted = ids.mapNotNull { MusicCatalog.byId(it) }.filter { it.id !in _installed.value }
        var start = false
        synchronized(queue) {
            val fresh = wanted.filter { t -> t.id != currentId && queue.none { it.id == t.id } }
            if (fresh.isEmpty()) return
            if (!workerRunning) {
                batchCount = 0
                batchBytes = 0
                doneCount = 0
                doneBytes = 0
            }
            queue.addAll(fresh)
            batchCount += fresh.size
            batchBytes += fresh.sumOf { it.bytes }
            start = !workerRunning
            workerRunning = true
            publishLocked(currentId?.let { MusicCatalog.byId(it)?.title } ?: fresh.first().title, 0)
        }
        if (start) job = scope.launch(Dispatchers.IO) { work() }
    }

    private fun publishLocked(title: String?, partBytes: Long) {
        _download.value = LibraryDownload(batchCount, doneCount, doneBytes + partBytes, batchBytes, title)
    }

    private suspend fun work() {
        while (true) {
            val track = synchronized(queue) {
                queue.removeFirstOrNull().also { t ->
                    currentId = t?.id
                    if (t == null) {
                        workerRunning = false
                        _download.value = null
                    } else {
                        publishLocked(t.title, 0)
                    }
                }
            } ?: return
            val error = runCatching { fetch(track) }.exceptionOrNull()
            val cancelled = !coroutineContext.isActive
            synchronized(queue) {
                currentId = null
                if (error != null) {
                    queue.clear()
                    workerRunning = false
                    if (!cancelled) Log.w(TAG, "Download von ${track.id} fehlgeschlagen: ${error.message}")
                    _download.value = if (cancelled) null else _download.value?.copy(currentTitle = null, error = "${track.title}: ${error.message}")
                    return
                }
                doneCount++
                doneBytes += track.bytes
                installedTracks = installedTracks + track
                _installed.value = _installed.value + track.id
            }
            Log.i(TAG, "Geladen: ${track.id}")
        }
    }

    private suspend fun fetch(track: LibraryTrack) {
        val target = File(dir, fileName(track))
        val part = File(dir, fileName(track) + ".part")
        val conn = URL(track.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "Lauscher-Android")
        try {
            val code = conn.responseCode
            if (code != 200) error("Server antwortet mit $code")
            val digest = MessageDigest.getInstance("SHA-256")
            var got = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        if (!coroutineContext.isActive) error("Abgebrochen")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        got += n
                        if (got - lastReport > 128 * 1024) {
                            lastReport = got
                            synchronized(queue) { if (currentId == track.id) publishLocked(track.title, got) }
                        }
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (got != track.bytes || sha != track.sha256) {
                part.delete()
                error("Datei ist beschädigt (Prüfsumme stimmt nicht)")
            }
            if (!part.renameTo(target)) error("Konnte Datei nicht speichern")
        } finally {
            conn.disconnect()
            part.delete()
        }
    }

    fun cancel() {
        synchronized(queue) { queue.clear() }
        job?.cancel()
    }

    fun clearError() {
        if (_download.value?.running == false) _download.value = null
    }

    fun delete(ids: Collection<String>) {
        val tracks = ids.mapNotNull { MusicCatalog.byId(it) }
        tracks.forEach { File(dir, fileName(it)).delete() }
        val gone = tracks.map { it.id }.toSet()
        installedTracks = installedTracks.filter { it.id !in gone }
        _installed.value = _installed.value - gone
    }

    // ------------------------------------------------------------------ RecordingSource

    override fun open(mood: Mood, intensity: Float, trackId: String?): PcmStream? {
        val track = if (trackId != null) {
            MusicCatalog.byId(trackId)
        } else {
            if (settings.current.source != MusicSource.LIBRARY) return null
            picker.pick(installedTracks, mood, intensity)
        } ?: return null
        val file = File(dir, fileName(track))
        if (!file.isFile) return null
        // Quieter scenes get the piece a little softer.
        val gain = 10f.pow(track.gainDb / 20f) * (0.8f + 0.2f * intensity.coerceIn(0f, 1f))
        Log.i(TAG, "Spielt: ${track.id} (${mood.name})")
        return DecodedFileStream(file, track.id, gain, MusicEngine.SAMPLE_RATE)
    }

    companion object {
        private const val TAG = "LauscherMusik"

        private fun fileName(track: LibraryTrack) = track.id.replace('/', '-') + ".ogg"
    }
}
