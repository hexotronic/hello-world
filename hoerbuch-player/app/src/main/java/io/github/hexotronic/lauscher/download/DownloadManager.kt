package io.github.hexotronic.lauscher.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import io.github.hexotronic.lauscher.download.core.ArchiveOrg
import io.github.hexotronic.lauscher.download.core.FileNames
import io.github.hexotronic.lauscher.download.core.HttpFetch
import io.github.hexotronic.lauscher.download.core.Id3
import io.github.hexotronic.lauscher.download.core.JobStore
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.download.core.OutputFormat
import io.github.hexotronic.lauscher.download.core.PlannedBook
import io.github.hexotronic.lauscher.download.core.SavedBook
import io.github.hexotronic.lauscher.download.core.SavedJob
import io.github.hexotronic.lauscher.download.core.TransferEstimate
import io.github.hexotronic.lauscher.download.core.TransferMeter
import io.github.hexotronic.lauscher.scan.LibraryScanner
import io.github.hexotronic.lauscher.ui.formatClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.TreeMap
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap

enum class JobState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

const val PHASE_DOWNLOAD = "lädt herunter"
const val PHASE_CONVERT = "wandelt in MP3 um"
const val PHASE_SAVE = "speichert"
const val PHASE_WAIT = "wartet"

/** One entry of the download list: a book from LibriVox, or a batch of videos or files. */
data class DownloadJob(
    val id: Long,
    val title: String,
    val detail: String,
    val total: Int,
    val done: Int = 0,
    val current: String? = null,
    val fraction: Float = 0f,
    val state: JobState = JobState.QUEUED,
    val message: String? = null,
    val failed: Int = 0,
    val bookIds: List<Long> = emptyList(),
    /** Bytes received over the network for this job so far. */
    val bytes: Long = 0,
    /** Size of everything to download, -1 when not known in advance (videos). */
    val bytesTotal: Long = -1,
    val bytesPerSecond: Long = 0,
    /** Expected remaining time while running, null while unknown. */
    val remainingMs: Long? = null,
    /** "WLAN", "mobile Daten", … while running. */
    val network: String? = null,
    /** What happens with the current item: [PHASE_DOWNLOAD], [PHASE_CONVERT], [PHASE_SAVE]. */
    val phase: String? = null,
    /** Items worked on at the same time; with more than one, [current] has a line for each. */
    val working: Int = 0,
) {
    val active: Boolean get() = state == JobState.QUEUED || state == JobState.RUNNING
    val finished: Boolean get() = state == JobState.DONE || state == JobState.FAILED || state == JobState.CANCELLED
    val overall: Float get() = if (total <= 0) 0f else ((done + fraction) / total).coerceIn(0f, 1f)
}

/**
 * Downloads audiobooks and the sound of videos, converts to MP3 if wanted, saves the files
 * (see [DownloadStore]) and adds them to the library as books. One job runs at a time; a job
 * can be paused and continues where it stopped (finished files are kept, the current file is
 * resumed with a Range request). Jobs are kept on disk ([JobStore]): after the app was closed
 * they are back, and interrupted ones continue when the app is opened again.
 */
class DownloadManager(private val context: Context, private val scanner: LibraryScanner, private val scope: CoroutineScope) {
    /** What a job has to do, and how far it got (kept while paused). */
    private sealed class Work {
        var activeMs = 0L
        var bytes = 0L
        var bytesTotal = -1L
        val meter = TransferMeter()
        var lastPublish = 0L
        /** Storage id of the book's folder, once its first file is written (LibriVox). */
        var folderDocId: String? = null

        class Audiobook(val id: String, val limit: Int?) : Work() {
            var book: ArchiveOrg.Book? = null
            var cover: File? = null
            var next = 0
            val saved = ArrayList<Pair<Uri, String>>()
        }

        class Media(val books: List<PlannedBook>, val format: OutputFormat, val quality: Mp3Quality, val local: Boolean) : Work() {
            /** How far each book got; changed while holding this object's lock. */
            val state = books.map { BookState() }
            /** Items being worked on now (book to item). */
            val active = ConcurrentHashMap<Pair<Int, Int>, ItemStatus>()
            val doneItems: Int get() = state.sumOf { it.done.size }
        }
    }

    /** One book of a video job: finished items, their files (in item order), cover and folder. */
    private class BookState {
        val done = TreeSet<Int>()
        val saved = TreeMap<Int, Pair<Uri, String>>()
        var cover: File? = null
        var coverBytes: ByteArray? = null
        var folderDocId: String? = null
        var imported = false
        var importing = false
    }

    private val store = DownloadStore(context)
    /** Partial files and covers of unfinished jobs; not in the cache, which Android may empty. */
    private val tempDir = File(context.filesDir, "downloads")
    private val jobsFile = File(context.filesDir, "download-jobs.json")
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()
    private val work = ConcurrentHashMap<Long, Work>()
    private val pausing: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    /** Continued while their pause was still taking effect: they go on as soon as it has. */
    private val resumeAfterPause: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private var nextId = 1L
    private var worker: Job? = null
    private var running: Pair<Long, Job>? = null
    private var networkLabel: String? = null
    private var networkCheckedAt = 0L
    /** Jobs that were running when the app was closed; they continue when it is opened again. */
    private val interrupted = HashSet<Long>()
    private var persistJob: Job? = null
    /** Downloads running at the same time (each over several connections). */
    private val downloads = Semaphore(DOWNLOADS_AT_ONCE)

    init {
        File(context.cacheDir, "downloads").deleteRecursively()
        tempDir.mkdirs()
        restore()
    }

    /** Where the files end up, for the screen. */
    val location: String get() = store.location

    /** True when the listener picked a folder instead of the default one. */
    val customFolder: Boolean get() = store.treeUri != null

    /** Saves new downloads into [tree] (from the system's folder picker), or the default folder for null. */
    fun setFolder(tree: Uri?) {
        if (tree == null) store.useDefault() else store.useFolder(tree)
        Log.i(TAG, "Speicherort: ${store.location}")
    }

    fun downloadAudiobook(item: ArchiveOrg.Item, limit: Int? = null): Long =
        enqueue(item.title, "LibriVox" + (item.author?.let { " · $it" } ?: ""), 0, Work.Audiobook(item.id, limit))

    fun downloadMedia(books: List<PlannedBook>, format: OutputFormat, quality: Mp3Quality): Long =
        enqueue(batchTitle(books), describe(books, format, quality), books.sumOf { it.items.size }, Work.Media(books, format, quality, local = false))

    /** Converts files picked on the phone; their [MediaItem.url]s are content URIs. */
    fun convertFiles(books: List<PlannedBook>, format: OutputFormat, quality: Mp3Quality): Long =
        enqueue(batchTitle(books), describe(books, format, quality), books.sumOf { it.items.size }, Work.Media(books, format, quality, local = true))

    fun cancel(id: Long) {
        interrupted -= id
        resumeAfterPause -= id
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        when {
            running?.first == id -> running?.second?.cancel()
            job.state == JobState.QUEUED || job.state == JobState.PAUSED -> {
                forget(id)
                setJob(id) { it.copy(state = JobState.CANCELLED, current = null, bytesPerSecond = 0, remainingMs = null) }
            }
        }
    }

    /** Stops a job for now; finished files and the started one are kept. */
    fun pause(id: Long) {
        interrupted -= id
        resumeAfterPause -= id
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        when {
            running?.first == id -> {
                pausing += id
                running?.second?.cancel()
            }
            job.state == JobState.QUEUED -> setJob(id) { it.copy(state = JobState.PAUSED) }
        }
    }

    fun resume(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.state == JobState.RUNNING && id in pausing) {
            // Paused a moment ago, but the pause has not taken effect yet (e.g. a slow server).
            resumeAfterPause += id
            return
        }
        if (job.state != JobState.PAUSED) return
        setJob(id) { it.copy(state = JobState.QUEUED, message = null) }
        Log.i(TAG, "Auftrag $id fortgesetzt")
        startWorker()
    }

    /** Continues the jobs the closing of the app interrupted (called when the app is opened). */
    fun resumeInterrupted() {
        val ids = interrupted.toList()
        interrupted.clear()
        ids.forEach { resume(it) }
    }

    fun pauseAll() = _jobs.value.filter { it.active }.forEach { pause(it.id) }

    fun resumeAll() = _jobs.value.filter { it.state == JobState.PAUSED }.forEach { resume(it.id) }

    fun clearFinished() {
        _jobs.update { list -> list.filterNot { it.finished } }
        persistSoon()
    }

    private fun enqueue(title: String, detail: String, total: Int, w: Work): Long {
        val id = nextId++
        work[id] = w
        _jobs.update { it + DownloadJob(id, title, detail, total) }
        persist()
        Log.i(TAG, "Auftrag $id: $title ($detail)")
        startWorker()
        return id
    }

    private fun startWorker() {
        DownloadService.start(context)
        if (worker?.isActive != true) worker = scope.launch(Dispatchers.IO) { runQueue() }
    }

    private suspend fun runQueue() {
        while (true) {
            val job = _jobs.value.firstOrNull { it.state == JobState.QUEUED } ?: break
            val w = work[job.id]
            if (w == null) {
                setJob(job.id) { it.copy(state = JobState.FAILED) }
                continue
            }
            setJob(job.id) { it.copy(state = JobState.RUNNING) }
            val segmentStart = SystemClock.elapsedRealtime()
            w.meter.reset()
            val task = scope.launch(Dispatchers.IO) {
                try {
                    when (w) {
                        is Work.Audiobook -> runAudiobook(job.id, w, segmentStart)
                        is Work.Media -> runMedia(job.id, w, segmentStart)
                    }
                    setJob(job.id) { j ->
                        val ok = j.bookIds.isNotEmpty()
                        j.copy(
                            state = if (ok) JobState.DONE else JobState.FAILED,
                            done = j.total,
                            current = null,
                            working = 0,
                            fraction = 0f,
                            bytesPerSecond = 0,
                            remainingMs = null,
                            message = when {
                                !ok -> j.message ?: "Nichts heruntergeladen"
                                j.failed > 0 -> "${j.failed} von ${j.total} fehlgeschlagen" + (j.message?.let { ": $it" } ?: "")
                                else -> null
                            },
                        )
                    }
                    forget(job.id)
                    Log.i(TAG, "Auftrag ${job.id} fertig: ${_jobs.value.firstOrNull { it.id == job.id }?.let { "${it.state}, Bücher ${it.bookIds}, Fehler ${it.failed}" }}")
                } catch (e: CancellationException) {
                    if (pausing.remove(job.id)) {
                        setJob(job.id) {
                            it.copy(state = JobState.PAUSED, bytesPerSecond = 0, remainingMs = null, working = 0, current = it.current.takeIf { _ -> it.working <= 1 })
                        }
                        Log.i(TAG, "Auftrag ${job.id} pausiert (${TransferEstimate.size(w.bytes)} geladen)")
                        if (resumeAfterPause.remove(job.id)) resume(job.id)
                    } else {
                        forget(job.id)
                        setJob(job.id) { it.copy(state = JobState.CANCELLED, current = null, working = 0, bytesPerSecond = 0, remainingMs = null) }
                    }
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Auftrag ${job.id} fehlgeschlagen", e)
                    forget(job.id)
                    setJob(job.id) { it.copy(state = JobState.FAILED, current = null, working = 0, bytesPerSecond = 0, remainingMs = null, message = DownloadErrors.describe(e)) }
                } finally {
                    w.activeMs += SystemClock.elapsedRealtime() - segmentStart
                    persist()
                }
            }
            running = job.id to task
            task.join()
            running = null
        }
    }

    /** Drops what a finished or cancelled job kept for continuing. */
    private fun forget(id: Long) {
        work.remove(id)
        pausing.remove(id)
        tempDir.listFiles()?.filter { it.name.startsWith("$id-") }?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------- LibriVox

    private suspend fun runAudiobook(jobId: Long, w: Work.Audiobook, segmentStart: Long) {
        val book = w.book ?: run {
            setJob(jobId) { it.copy(current = "Inhaltsverzeichnis …") }
            val b = ArchiveOrg.parseMetadata(w.id, Net.text(ArchiveOrg.metadataUrl(w.id))) ?: error("Dieses Hörbuch hat keine MP3-Dateien")
            w.book = b
            if (w.cover == null) w.cover = Net.bytes(b.coverUrl)?.let { saveCover(jobId, it) }
            b
        }
        val files = w.limit?.let { book.files.take(it) } ?: book.files
        w.bytesTotal = files.sumOf { it.bytes ?: 0L }.takeIf { size -> files.all { it.bytes != null } && size > 0 } ?: -1
        setJob(jobId) {
            it.copy(
                title = book.title,
                total = files.size,
                detail = "LibriVox · ${files.size} Kapitel" + (book.author?.let { a -> " · $a" } ?: ""),
                bytesTotal = w.bytesTotal,
            )
        }
        val folder = folderName(book.title, book.author)
        while (w.next < files.size) {
            val i = w.next
            val f = files[i]
            val chapter = f.title ?: f.name.substringBeforeLast('.')
            setJob(jobId) { it.copy(done = i, current = chapter, fraction = 0f) }
            val temp = File(tempDir, "$jobId-a$i.mp3")
            try {
                val onDisk = HttpFetch.bytesOnDisk(temp)
                setJob(jobId) { it.copy(phase = PHASE_DOWNLOAD) }
                val progress = Progress(jobId, w, segmentStart, onDisk) { done, total ->
                    val all = if (total > 0) total else f.bytes ?: 0L
                    if (all > 0) done.toFloat() / all else 0f
                }
                if (onDisk > 0) Log.i(TAG, "Fortsetzen ab ${TransferEstimate.size(onDisk)}: $chapter")
                val fetchStart = SystemClock.elapsedRealtime()
                Net.download(f.url, temp, progress::report)
                logSpeed(chapter, temp.length() - onDisk, fetchStart)
                setJob(jobId) { it.copy(phase = PHASE_SAVE, bytesPerSecond = 0) }
                val name = FileNames.track(i, files.size, chapter, "mp3")
                val target = store.create(folder, name, "audio/mpeg")
                w.folderDocId = target.folderDocId ?: w.folderDocId
                try {
                    target.openStream().use { out -> temp.inputStream().use { it.copyTo(out, 64 * 1024) } }
                    target.finish()
                } catch (e: Throwable) {
                    target.discard()
                    throw e
                }
                synchronized(w) { w.saved += target.uri to target.name }
                temp.delete()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                temp.delete()
                Log.w(TAG, "Kapitel ${f.name} fehlgeschlagen", e)
                setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
            }
            w.next = i + 1
            // Saved at once, so a closed app does not fetch this chapter again.
            persist()
        }
        importBook(jobId, "download:librivox:${book.id}", book.title, book.author, w.saved, w.cover, w.folderDocId)
    }

    // ---------------------------------------------------------------- videos, links, local files

    /**
     * Works on several items at once ([ITEMS_AT_ONCE], files on the phone [LOCAL_AT_ONCE]), also
     * across books: while one item is converted the next one downloads. At most
     * [DOWNLOADS_AT_ONCE] downloads run at the same time, and all conversions share the encoders
     * of [AudioConverter]. A book goes into the library as soon as all its items are finished.
     */
    private suspend fun runMedia(jobId: Long, w: Work.Media, segmentStart: Long) = coroutineScope {
        val todo = ArrayDeque<Pair<Int, Int>>()
        synchronized(w) {
            w.books.forEachIndexed { b, book ->
                val state = w.state[b]
                if (!state.imported) book.items.indices.filter { it !in state.done }.forEach { todo += b to it }
            }
        }
        // Books that were finished before an interruption but are not in the library yet.
        w.books.indices.forEach { importIfComplete(jobId, w, it) }
        repeat(minOf(if (w.local) LOCAL_AT_ONCE else ITEMS_AT_ONCE, todo.size)) {
            launch {
                while (true) {
                    val (b, i) = synchronized(todo) { todo.removeFirstOrNull() } ?: break
                    runItem(jobId, w, b, i, segmentStart)
                    importIfComplete(jobId, w, b)
                }
            }
        }
    }

    private suspend fun runItem(jobId: Long, w: Work.Media, b: Int, i: Int, segmentStart: Long) {
        val book = w.books[b]
        val item = book.items[i]
        val state = w.state[b]
        val status = ItemStatus(item.title)
        w.active[b to i] = status
        publish(jobId, w, segmentStart, force = true)
        try {
            val source = if (w.local) {
                localSource(item)
            } else {
                MediaSources.audioOf(item.url, compatible = w.format == OutputFormat.ORIGINAL)
            }
            if (!w.local && synchronized(w) { state.cover == null }) {
                val bytes = (source.thumbnail ?: item.thumbnail)?.let { Net.bytes(it) }
                val file = bytes?.let { saveCover(jobId, it) }
                synchronized(w) {
                    if (state.cover == null) {
                        state.cover = file
                        state.coverBytes = bytes
                    } else {
                        file?.delete()
                    }
                }
            }
            val cover = if (i == 0) synchronized(w) { state.coverBytes ?: state.cover?.takeIf { it.exists() }?.readBytes() } else null
            val file = fetchAndSave(jobId, w, b, book, item, source, i, cover, segmentStart, status)
            synchronized(w) { state.saved[i] = file }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "„${item.title}“ fehlgeschlagen", e)
            setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
        } finally {
            w.active.remove(b to i)
        }
        synchronized(w) { state.done += i }
        publish(jobId, w, segmentStart, force = true)
        // Saved at once, so a closed app does not do this item again.
        persist()
    }

    /** Adds book [b] to the library once all its items are finished (and only once). */
    private suspend fun importIfComplete(jobId: Long, w: Work.Media, b: Int) {
        val book = w.books[b]
        val state = w.state[b]
        val files = synchronized(w) {
            if (state.imported || state.importing || state.done.size < book.items.size) return
            state.importing = true
            state.saved.values.toList()
        }
        val key = "download:" + sha1(book.items.joinToString("\n") { it.url })
        importBook(jobId, key, book.title, book.author, files, state.cover, state.folderDocId)
        synchronized(w) {
            state.imported = true
            state.importing = false
            state.cover = null
            state.coverBytes = null
        }
        persist()
    }

    private fun localSource(item: MediaItem): AudioSource {
        val uri = Uri.parse(item.url)
        val type = context.contentResolver.getType(uri).orEmpty()
        val extension = LinkInput.extensionOf(item.title).ifEmpty { if (type == "audio/mpeg") "mp3" else "mp4" }
        return AudioSource(item.url, extension, !type.startsWith("audio/"), item.title, null, null)
    }

    /** Downloads (unless local) and converts one item; returns the saved file. */
    private suspend fun fetchAndSave(
        jobId: Long,
        w: Work.Media,
        b: Int,
        book: PlannedBook,
        item: MediaItem,
        source: AudioSource,
        index: Int,
        cover: ByteArray?,
        segmentStart: Long,
        status: ItemStatus,
    ): Pair<Uri, String> {
        val state = w.state[b]
        val folder = folderName(book.title, book.author)
        val title = if (w.local) item.title.substringBeforeLast('.') else item.title
        val count = book.items.size
        fun phase(p: String) {
            status.phase = p
            publish(jobId, w, segmentStart, force = true)
        }
        // Same name on every attempt, so a paused download continues.
        val temp = File(tempDir, "$jobId-$b-$index.${source.extension.ifEmpty { "bin" }}")
        val extracted = File(tempDir, "$jobId-$b-$index-audio")
        val input: Uri = if (w.local) {
            Uri.parse(source.url)
        } else {
            val share = if (w.format == OutputFormat.MP3 && !(source.extension == "mp3" && !source.isVideo)) 0.6f else 1f
            phase(PHASE_WAIT)
            downloads.withPermit {
                val onDisk = HttpFetch.bytesOnDisk(temp)
                phase(PHASE_DOWNLOAD)
                val progress = Progress(jobId, w, segmentStart, onDisk, status) { done, total -> if (total > 0) share * done / total else 0f }
                if (onDisk > 0) Log.i(TAG, "Fortsetzen ab ${TransferEstimate.size(onDisk)}: ${item.title}")
                val fetchStart = SystemClock.elapsedRealtime()
                Net.download(source.url, temp, progress::report)
                logSpeed(item.title, temp.length() - onDisk, fetchStart)
            }
            Uri.fromFile(temp)
        }
        val isMp3 = source.extension == "mp3" && !source.isVideo
        if (isMp3 || w.format == OutputFormat.ORIGINAL) {
            phase(PHASE_SAVE)
            val (from, extension) = when {
                isMp3 -> input to "mp3"
                else -> AudioConverter.extractAudio(context, input, extracted)?.let { Uri.fromFile(extracted) to it }
                    ?: (null to null)
            }
            if (from != null && extension != null) {
                val target = store.create(folder, FileNames.track(index, count, title, extension), DownloadStore.mimeOf(extension))
                synchronized(w) { state.folderDocId = target.folderDocId ?: state.folderDocId }
                try {
                    target.openStream().use { out -> context.contentResolver.openInputStream(from)!!.use { it.copyTo(out, 64 * 1024) } }
                    target.finish()
                } catch (e: Throwable) {
                    target.discard()
                    throw e
                }
                temp.delete()
                extracted.delete()
                return target.uri to target.name
            }
            // Codecs that cannot be copied as they are (e.g. AC-3 in a video) become MP3.
        }
        val target = store.create(folder, FileNames.track(index, count, title, "mp3"), "audio/mpeg")
        synchronized(w) { state.folderDocId = target.folderDocId ?: state.folderDocId }
        val started = SystemClock.elapsedRealtime()
        val result = try {
            val start = if (w.local) 0f else 0.6f
            phase(PHASE_CONVERT)
            val progress = Progress(jobId, w, segmentStart, 0, status) { _, _ -> 0f }
            val r = target.openStream().use { out ->
                out.write(Id3.tag(title, book.author, book.title, index + 1, count, cover))
                AudioConverter.toMp3(context, input, out, w.quality) { f -> progress.fraction(start + (1 - start) * f) }
            }
            target.finish()
            r
        } catch (e: Throwable) {
            target.discard()
            // A pause keeps the downloaded file; converting starts again on resume.
            if (e !is CancellationException) temp.delete()
            extracted.delete()
            throw e
        }
        temp.delete()
        extracted.delete()
        val seconds = (SystemClock.elapsedRealtime() - started) / 1000.0
        Log.i(
            TAG,
            "Umgewandelt: ${target.name} – ${formatClock(result.durationMs)} in ${"%.1f".format(Locale.ROOT, seconds)} s" +
                (if (result.pieces > 1) " (${result.pieces} Teile, ${AudioConverter.threads} gleichzeitig, ${result.roughJoints} unsaubere Nähte)" else ""),
        )
        return target.uri to target.name
    }

    // ---------------------------------------------------------------- progress, speed, network

    /** One item of a video job while it is worked on. */
    private class ItemStatus(val title: String) {
        @Volatile var phase: String = PHASE_WAIT
        /** Share of the item that is done (download and conversion together). */
        @Volatile var fraction = 0f
    }

    /**
     * Turns the byte counts of one file into the job's numbers: data received, speed, share of
     * the item ([share] of (bytes, total)) and the expected remaining time.
     */
    private inner class Progress(
        private val jobId: Long,
        private val w: Work,
        private val segmentStart: Long,
        startBytes: Long,
        private val status: ItemStatus? = null,
        private val share: (Long, Long) -> Float,
    ) {
        private var last = startBytes

        fun report(done: Long, total: Long) {
            synchronized(w) { w.bytes += (done - last).coerceAtLeast(0) }
            last = done
            fraction(share(done, total))
        }

        fun fraction(f: Float) {
            status?.fraction = f
            publish(jobId, w, segmentStart, f)
        }
    }

    /**
     * Shows the job's numbers (at most four times a second unless [force]). Video jobs sum up
     * the items being worked on; [single] is the share of the current file otherwise.
     */
    private fun publish(jobId: Long, w: Work, segmentStart: Long, single: Float = 0f, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val (bytes, speed) = synchronized(w) {
            if (!force && now - w.lastPublish < 250) {
                null
            } else {
                w.lastPublish = now
                w.meter.add(now, w.bytes)
                w.bytes to w.meter.bytesPerSecond
            }
        } ?: return
        val active = w.activeMs + (now - segmentStart)
        val net = network(now)
        val items = (w as? Work.Media)?.let { m ->
            m.active.entries.sortedBy { it.key.first * 100_000L + it.key.second }.map { it.value }
        }
        val done = (w as? Work.Media)?.let { synchronized(it) { it.doneItems } }
        setJob(jobId) { j ->
            val fraction = items?.sumOf { it.fraction.toDouble() }?.toFloat() ?: single
            val phase = when {
                items == null -> j.phase
                items.any { it.phase == PHASE_DOWNLOAD } -> PHASE_DOWNLOAD
                else -> items.firstOrNull()?.phase
            }
            val current = when {
                items == null -> j.current
                items.size <= 1 -> items.firstOrNull()?.title
                else -> items.joinToString("\n") { "${it.title} – ${it.phase} ${(it.fraction * 100).toInt()} %" }
            }
            val finished = done ?: j.done
            val overall = if (j.total <= 0) 0f else ((finished + fraction) / j.total).coerceIn(0f, 1f)
            j.copy(
                done = finished,
                fraction = fraction,
                current = current,
                phase = phase,
                working = items?.size ?: j.working,
                bytes = bytes,
                bytesTotal = w.bytesTotal,
                // While converting or saving nothing comes over the network.
                bytesPerSecond = if (phase == PHASE_DOWNLOAD) speed.toLong() else 0,
                remainingMs = TransferEstimate.remainingMs(bytes, w.bytesTotal, speed, overall, active),
                network = net,
            )
        }
    }

    /** Kind of the current connection, checked every few seconds. */
    private fun network(now: Long): String? {
        if (now - networkCheckedAt < 3_000 && networkCheckedAt > 0) return networkLabel
        networkCheckedAt = now
        networkLabel = runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile Daten"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "LAN"
                else -> null
            }
        }.getOrNull()
        return networkLabel
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun importBook(
        jobId: Long,
        key: String,
        title: String,
        author: String?,
        files: List<Pair<Uri, String>>,
        cover: File?,
        folderDocId: String?,
    ) {
        if (files.isEmpty()) return
        setJob(jobId) { it.copy(current = "In die Bibliothek …", phase = null, bytesPerSecond = 0) }
        // Saved inside a library folder: the key a scan of that folder would give, so the
        // book is not added a second time.
        val library = folderDocId?.let { scanner.libraryFolderOf(it) }
        val bookKey = if (library != null) "dir:$folderDocId" else key
        val id = scanner.importFiles(bookKey, title, author, files.toList(), cover, library)
        cover?.delete()
        Log.i(TAG, "Importiert [$bookKey]: $title (${files.size} Dateien) → Buch ${id ?: "–"}")
        if (id != null) setJob(jobId) { it.copy(bookIds = it.bookIds + id) }
    }

    private fun logSpeed(name: String, bytes: Long, since: Long) {
        val ms = (SystemClock.elapsedRealtime() - since).coerceAtLeast(1)
        Log.i(TAG, "Geladen: $name – ${TransferEstimate.size(bytes)} in ${ms / 1000.0} s (${TransferEstimate.size(bytes * 1000 / ms)}/s)")
    }

    private fun saveCover(jobId: Long, bytes: ByteArray): File =
        File(tempDir, "$jobId-cover-${System.nanoTime()}.jpg").apply { writeBytes(bytes) }

    private fun folderName(title: String, author: String?) = FileNames.safe((author?.let { "$it - " } ?: "") + title)

    private fun batchTitle(books: List<PlannedBook>) =
        if (books.size == 1) books[0].title else "${books.size} Hörbücher"

    private fun describe(books: List<PlannedBook>, format: OutputFormat, quality: Mp3Quality): String {
        val items = books.sumOf { it.items.size }
        return "$items ${if (items == 1) "Datei" else "Dateien"} · " + if (format == OutputFormat.MP3) "MP3 ${quality.kbps} kbit/s" else "Original"
    }

    private fun setJob(id: Long, change: (DownloadJob) -> DownloadJob) {
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }
        persistSoon()
    }

    // ---------------------------------------------------------------- kept on disk

    /** Brings back the jobs of the last app session; running ones become "interrupted". */
    private fun restore() {
        val saved = runCatching { JobStore.decode(jobsFile.readText()) }.getOrDefault(emptyList())
        val jobs = saved.map { s ->
            val wasActive = s.state == JobState.RUNNING.name || s.state == JobState.QUEUED.name
            var state = if (wasActive) JobState.PAUSED else runCatching { JobState.valueOf(s.state) }.getOrDefault(JobState.PAUSED)
            if (state == JobState.PAUSED) {
                val w = workOf(s)
                if (w == null) {
                    state = JobState.FAILED
                } else {
                    work[s.id] = w
                }
            }
            if (wasActive && state == JobState.PAUSED) interrupted += s.id
            DownloadJob(
                id = s.id,
                title = s.title,
                detail = s.detail,
                total = s.total,
                done = s.done,
                state = state,
                message = if (wasActive) "Unterbrochen – geht weiter, sobald die App offen ist" else s.message,
                failed = s.failed,
                bookIds = s.bookIds,
                bytes = s.bytes,
                bytesTotal = s.bytesTotal,
            )
        }
        _jobs.value = jobs
        nextId = (saved.maxOfOrNull { it.id } ?: 0L) + 1
        // Partial files of jobs that are gone.
        tempDir.listFiles()?.filter { f -> work.keys.none { f.name.startsWith("$it-") } }?.forEach { it.deleteRecursively() }
        if (saved.isNotEmpty()) Log.i(TAG, "Wiederhergestellt: ${saved.size} Aufträge, ${interrupted.size} unterbrochen")
    }

    private fun workOf(s: SavedJob): Work? {
        val w = when (s.kind) {
            KIND_AUDIOBOOK -> Work.Audiobook(s.archiveId ?: return null, s.limit).apply { next = s.next }
            KIND_MEDIA -> Work.Media(
                s.books,
                runCatching { OutputFormat.valueOf(s.format!!) }.getOrNull() ?: return null,
                runCatching { Mp3Quality.valueOf(s.quality!!) }.getOrNull() ?: return null,
                s.local,
            ).apply {
                s.bookStates.forEachIndexed { b, saved ->
                    val st = state.getOrNull(b) ?: return@forEachIndexed
                    st.done += saved.done.filter { it in books[b].items.indices }
                    saved.saved.forEach { (i, uri, name) -> st.saved[i] = Uri.parse(uri) to name }
                    st.cover = saved.cover?.let(::File)?.takeIf { it.exists() }
                    st.folderDocId = saved.folderDocId
                    st.imported = saved.imported
                }
            }
            else -> return null
        }
        w.activeMs = s.activeMs
        w.bytes = s.bytes
        w.bytesTotal = s.bytesTotal
        w.folderDocId = s.folderDocId
        if (w is Work.Audiobook) {
            w.saved += s.saved.map { (uri, name) -> Uri.parse(uri) to name }
            w.cover = s.cover?.let(::File)?.takeIf { it.exists() }
        }
        return w
    }

    private fun savedOf(j: DownloadJob, w: Work?): SavedJob {
        val base = SavedJob(
            id = j.id,
            title = j.title,
            detail = j.detail,
            total = j.total,
            done = j.done,
            failed = j.failed,
            state = j.state.name,
            message = j.message,
            bookIds = j.bookIds,
            bytes = j.bytes,
            bytesTotal = j.bytesTotal,
            activeMs = w?.activeMs ?: 0,
            kind = KIND_FINISHED,
        )
        return when (w) {
            null -> base
            is Work.Audiobook -> synchronized(w) {
                base.copy(kind = KIND_AUDIOBOOK, archiveId = w.id, limit = w.limit, next = w.next, saved = w.saved.map { it.first.toString() to it.second },
                    cover = w.cover?.path, folderDocId = w.folderDocId)
            }
            is Work.Media -> synchronized(w) {
                base.copy(
                    kind = KIND_MEDIA, books = w.books, format = w.format.name, quality = w.quality.name, local = w.local,
                    bookStates = w.state.map { st ->
                        SavedBook(
                            done = st.done.toList(),
                            saved = st.saved.map { (i, file) -> Triple(i, file.first.toString(), file.second) },
                            cover = st.cover?.path,
                            folderDocId = st.folderDocId,
                            imported = st.imported,
                        )
                    },
                )
            }
        }
    }

    /** Writes all jobs to disk now (atomically: a half-written file is never read back). */
    @Synchronized
    private fun persist() {
        val jobs = _jobs.value
        val finished = jobs.filter { it.finished }.takeLast(MAX_FINISHED_KEPT).map { it.id }.toSet()
        val list = jobs.filter { !it.finished || it.id in finished }.map { savedOf(it, work[it.id]) }
        runCatching {
            val tmp = File(jobsFile.path + ".tmp")
            tmp.writeText(JobStore.encode(list))
            tmp.renameTo(jobsFile)
        }.onFailure { Log.w(TAG, "Aufträge nicht gespeichert", it) }
    }

    /** Saves a moment later, so frequent progress updates cost one write. */
    private fun persistSoon() {
        if (persistJob?.isActive == true) return
        persistJob = scope.launch(Dispatchers.IO) {
            delay(1_500)
            persist()
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        const val TAG = "LauscherDownload"
        const val KIND_AUDIOBOOK = "audiobook"
        const val KIND_MEDIA = "media"
        const val KIND_FINISHED = "finished"

        /** Finished jobs shown again after a restart (until "Erledigte entfernen"). */
        const val MAX_FINISHED_KEPT = 30

        /** Items of a video job worked on at once (one converts while the next downloads). */
        const val ITEMS_AT_ONCE = 3

        /** Files from the phone converted at once (each one on several cores already). */
        const val LOCAL_AT_ONCE = 2

        const val DOWNLOADS_AT_ONCE = 2
    }
}
