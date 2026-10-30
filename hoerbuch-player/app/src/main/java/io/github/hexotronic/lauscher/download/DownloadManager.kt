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
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.download.core.OutputFormat
import io.github.hexotronic.lauscher.download.core.PlannedBook
import io.github.hexotronic.lauscher.download.core.TransferEstimate
import io.github.hexotronic.lauscher.download.core.TransferMeter
import io.github.hexotronic.lauscher.scan.LibraryScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

enum class JobState { QUEUED, RUNNING, PAUSED, DONE, FAILED, CANCELLED }

const val PHASE_DOWNLOAD = "lädt herunter"
const val PHASE_CONVERT = "wandelt in MP3 um"
const val PHASE_SAVE = "speichert"

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
) {
    val active: Boolean get() = state == JobState.QUEUED || state == JobState.RUNNING
    val finished: Boolean get() = state == JobState.DONE || state == JobState.FAILED || state == JobState.CANCELLED
    val overall: Float get() = if (total <= 0) 0f else ((done + fraction) / total).coerceIn(0f, 1f)
}

/**
 * Downloads audiobooks and the sound of videos, converts to MP3 if wanted, saves the files
 * (see [DownloadStore]) and adds them to the library as books. One job runs at a time; a job
 * can be paused and continues where it stopped (finished files are kept, the current file is
 * resumed with a Range request).
 */
class DownloadManager(private val context: Context, private val scanner: LibraryScanner, private val scope: CoroutineScope) {
    /** What a job has to do, and how far it got (kept while paused). */
    private sealed class Work {
        var activeMs = 0L
        var bytes = 0L
        var bytesTotal = -1L
        val meter = TransferMeter()
        /** Storage id of the current book's folder, once its first file is written. */
        var folderDocId: String? = null

        class Audiobook(val id: String, val limit: Int?) : Work() {
            var book: ArchiveOrg.Book? = null
            var cover: File? = null
            var next = 0
            val saved = ArrayList<Pair<Uri, String>>()
        }

        class Media(val books: List<PlannedBook>, val format: OutputFormat, val quality: Mp3Quality, val local: Boolean) : Work() {
            var bookIndex = 0
            var itemIndex = 0
            var doneItems = 0
            var cover: File? = null
            var coverBytes: ByteArray? = null
            val saved = ArrayList<Pair<Uri, String>>()
        }
    }

    private val store = DownloadStore(context)
    private val tempDir = File(context.cacheDir, "downloads").apply {
        // Partial files of an earlier app session cannot be continued (their jobs are gone).
        listFiles()?.forEach { it.delete() }
        mkdirs()
    }
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()
    private val work = HashMap<Long, Work>()
    private val pausing = HashSet<Long>()
    private var nextId = 1L
    private var worker: Job? = null
    private var running: Pair<Long, Job>? = null
    private var networkLabel: String? = null
    private var networkCheckedAt = 0L

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
        if (job.state != JobState.PAUSED) return
        setJob(id) { it.copy(state = JobState.QUEUED, message = null) }
        Log.i(TAG, "Auftrag $id fortgesetzt")
        startWorker()
    }

    fun pauseAll() = _jobs.value.filter { it.active }.forEach { pause(it.id) }

    fun resumeAll() = _jobs.value.filter { it.state == JobState.PAUSED }.forEach { resume(it.id) }

    fun clearFinished() = _jobs.update { list -> list.filterNot { it.finished } }

    private fun enqueue(title: String, detail: String, total: Int, w: Work): Long {
        val id = nextId++
        work[id] = w
        _jobs.update { it + DownloadJob(id, title, detail, total) }
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
                        setJob(job.id) { it.copy(state = JobState.PAUSED, bytesPerSecond = 0, remainingMs = null) }
                        Log.i(TAG, "Auftrag ${job.id} pausiert (${TransferEstimate.size(w.bytes)} geladen)")
                    } else {
                        forget(job.id)
                        setJob(job.id) { it.copy(state = JobState.CANCELLED, current = null, bytesPerSecond = 0, remainingMs = null) }
                    }
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Auftrag ${job.id} fehlgeschlagen", e)
                    forget(job.id)
                    setJob(job.id) { it.copy(state = JobState.FAILED, current = null, bytesPerSecond = 0, remainingMs = null, message = DownloadErrors.describe(e)) }
                } finally {
                    w.activeMs += SystemClock.elapsedRealtime() - segmentStart
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
            w.cover = Net.bytes(b.coverUrl)?.let { saveCover(jobId, it) }
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
                w.saved += target.uri to target.name
                temp.delete()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                temp.delete()
                Log.w(TAG, "Kapitel ${f.name} fehlgeschlagen", e)
                setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
            }
            w.next = i + 1
        }
        importBook(jobId, "download:librivox:${book.id}", book.title, book.author, w.saved, w.cover, w.folderDocId)
    }

    // ---------------------------------------------------------------- videos, links, local files

    private suspend fun runMedia(jobId: Long, w: Work.Media, segmentStart: Long) {
        while (w.bookIndex < w.books.size) {
            val book = w.books[w.bookIndex]
            val folder = folderName(book.title, book.author)
            while (w.itemIndex < book.items.size) {
                val i = w.itemIndex
                val item = book.items[i]
                setJob(jobId) { it.copy(done = w.doneItems, current = item.title, fraction = 0f) }
                try {
                    val source = if (w.local) {
                        localSource(item)
                    } else {
                        MediaSources.audioOf(item.url, compatible = w.format == OutputFormat.ORIGINAL)
                    }
                    if (w.cover == null && !w.local) {
                        w.coverBytes = (source.thumbnail ?: item.thumbnail)?.let { Net.bytes(it) }
                        w.cover = w.coverBytes?.let { saveCover(jobId, it) }
                    }
                    w.saved += fetchAndSave(jobId, w, book, item, source, i, folder, if (i == 0) w.coverBytes else null, segmentStart)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "„${item.title}“ fehlgeschlagen", e)
                    setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
                }
                w.itemIndex = i + 1
                w.doneItems++
            }
            val key = "download:" + sha1(book.items.joinToString("\n") { it.url })
            importBook(jobId, key, book.title, book.author, w.saved, w.cover, w.folderDocId)
            w.folderDocId = null
            w.bookIndex++
            w.itemIndex = 0
            w.saved.clear()
            w.cover = null
            w.coverBytes = null
        }
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
        book: PlannedBook,
        item: MediaItem,
        source: AudioSource,
        index: Int,
        folder: String,
        cover: ByteArray?,
        segmentStart: Long,
    ): Pair<Uri, String> {
        val title = if (w.local) item.title.substringBeforeLast('.') else item.title
        val count = book.items.size
        // Same name on every attempt, so a paused download continues.
        val temp = File(tempDir, "$jobId-${w.bookIndex}-$index.${source.extension.ifEmpty { "bin" }}")
        val extracted = File(tempDir, "$jobId-${w.bookIndex}-$index-audio")
        val input: Uri = if (w.local) {
            Uri.parse(source.url)
        } else {
            val share = if (w.format == OutputFormat.MP3 && !(source.extension == "mp3" && !source.isVideo)) 0.6f else 1f
            val onDisk = HttpFetch.bytesOnDisk(temp)
            setJob(jobId) { it.copy(phase = PHASE_DOWNLOAD) }
            val progress = Progress(jobId, w, segmentStart, onDisk) { done, total -> if (total > 0) share * done / total else 0f }
            if (onDisk > 0) Log.i(TAG, "Fortsetzen ab ${TransferEstimate.size(onDisk)}: ${item.title}")
            val fetchStart = SystemClock.elapsedRealtime()
            Net.download(source.url, temp, progress::report)
            logSpeed(item.title, temp.length() - onDisk, fetchStart)
            Uri.fromFile(temp)
        }
        val isMp3 = source.extension == "mp3" && !source.isVideo
        if (isMp3 || w.format == OutputFormat.ORIGINAL) {
            setJob(jobId) { it.copy(phase = PHASE_SAVE, bytesPerSecond = 0) }
            val (from, extension) = when {
                isMp3 -> input to "mp3"
                else -> AudioConverter.extractAudio(context, input, extracted)?.let { Uri.fromFile(extracted) to it }
                    ?: (null to null)
            }
            if (from != null && extension != null) {
                val target = store.create(folder, FileNames.track(index, count, title, extension), DownloadStore.mimeOf(extension))
                w.folderDocId = target.folderDocId ?: w.folderDocId
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
        w.folderDocId = target.folderDocId ?: w.folderDocId
        try {
            val start = if (w.local) 0f else 0.6f
            setJob(jobId) { it.copy(phase = PHASE_CONVERT, bytesPerSecond = 0) }
            val progress = Progress(jobId, w, segmentStart, 0) { _, _ -> 0f }
            target.openStream().use { out ->
                out.write(Id3.tag(title, book.author, book.title, index + 1, count, cover))
                AudioConverter.toMp3(context, input, out, w.quality) { f -> progress.fraction(start + (1 - start) * f) }
            }
            target.finish()
        } catch (e: Throwable) {
            target.discard()
            // A pause keeps the downloaded file; converting starts again on resume.
            if (e !is CancellationException) temp.delete()
            extracted.delete()
            throw e
        }
        temp.delete()
        extracted.delete()
        Log.i(TAG, "Umgewandelt: ${target.name}")
        return target.uri to target.name
    }

    // ---------------------------------------------------------------- progress, speed, network

    /**
     * Turns the byte counts of one file into the job's numbers: data received, speed, share of
     * the current item ([share] of (bytes, total)) and the expected remaining time.
     */
    private inner class Progress(
        private val jobId: Long,
        private val w: Work,
        private val segmentStart: Long,
        startBytes: Long,
        private val share: (Long, Long) -> Float,
    ) {
        private var last = startBytes
        private var lastReport = 0L

        fun report(done: Long, total: Long) {
            w.bytes += (done - last).coerceAtLeast(0)
            last = done
            publish(share(done, total))
        }

        fun fraction(f: Float) = publish(f)

        private fun publish(f: Float) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReport < 250) return
            lastReport = now
            w.meter.add(now, w.bytes)
            val active = w.activeMs + (now - segmentStart)
            val net = network(now)
            setJob(jobId) { j ->
                val overall = if (j.total <= 0) 0f else ((j.done + f) / j.total).coerceIn(0f, 1f)
                j.copy(
                    fraction = f,
                    bytes = w.bytes,
                    bytesTotal = w.bytesTotal,
                    // While converting or saving nothing comes over the network.
                    bytesPerSecond = if (j.phase == PHASE_DOWNLOAD) w.meter.bytesPerSecond.toLong() else 0,
                    remainingMs = TransferEstimate.remainingMs(w.bytes, w.bytesTotal, w.meter.bytesPerSecond, overall, active),
                    network = net,
                )
            }
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

    private fun setJob(id: Long, change: (DownloadJob) -> DownloadJob) =
        _jobs.update { list -> list.map { if (it.id == id) change(it) else it } }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    private companion object {
        const val TAG = "LauscherDownload"
    }
}
