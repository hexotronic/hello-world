package io.github.hexotronic.lauscher.download

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.hexotronic.lauscher.download.core.ArchiveOrg
import io.github.hexotronic.lauscher.download.core.FileNames
import io.github.hexotronic.lauscher.download.core.Id3
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.download.core.OutputFormat
import io.github.hexotronic.lauscher.download.core.PlannedBook
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

enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELLED }

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
) {
    val active: Boolean get() = state == JobState.QUEUED || state == JobState.RUNNING
    val overall: Float get() = if (total <= 0) 0f else ((done + fraction) / total).coerceIn(0f, 1f)
}

/**
 * Downloads audiobooks and the sound of videos, converts to MP3 if wanted, saves the files
 * (see [DownloadStore]) and adds them to the library as books. One job runs at a time.
 */
class DownloadManager(private val context: Context, private val scanner: LibraryScanner, private val scope: CoroutineScope) {
    private sealed interface Work {
        class Audiobook(val id: String, val limit: Int?) : Work
        class Media(val books: List<PlannedBook>, val format: OutputFormat, val quality: Mp3Quality, val local: Boolean) : Work
    }

    private val store = DownloadStore(context)
    private val tempDir = File(context.cacheDir, "downloads")
    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()
    private val work = HashMap<Long, Work>()
    private var nextId = 1L
    private var worker: Job? = null
    private var running: Pair<Long, Job>? = null

    /** Where the files end up, for the screen. */
    val location: String get() = store.location

    fun downloadAudiobook(item: ArchiveOrg.Item, limit: Int? = null): Long =
        enqueue(item.title, "LibriVox" + (item.author?.let { " · $it" } ?: ""), 0, Work.Audiobook(item.id, limit))

    fun downloadMedia(books: List<PlannedBook>, format: OutputFormat, quality: Mp3Quality): Long =
        enqueue(batchTitle(books), describe(books, format, quality), books.sumOf { it.items.size }, Work.Media(books, format, quality, local = false))

    /** Converts files picked on the phone; their [MediaItem.url]s are content URIs. */
    fun convertFiles(books: List<PlannedBook>, format: OutputFormat, quality: Mp3Quality): Long =
        enqueue(batchTitle(books), describe(books, format, quality), books.sumOf { it.items.size }, Work.Media(books, format, quality, local = true))

    fun cancel(id: Long) {
        val job = _jobs.value.firstOrNull { it.id == id } ?: return
        if (job.state == JobState.QUEUED) {
            work.remove(id)
            setJob(id) { it.copy(state = JobState.CANCELLED) }
        } else if (running?.first == id) {
            running?.second?.cancel()
        }
    }

    fun clearFinished() = _jobs.update { list -> list.filter { it.active } }

    private fun enqueue(title: String, detail: String, total: Int, w: Work): Long {
        val id = nextId++
        work[id] = w
        _jobs.update { it + DownloadJob(id, title, detail, total) }
        Log.i(TAG, "Auftrag $id: $title ($detail)")
        DownloadService.start(context)
        if (worker?.isActive != true) worker = scope.launch(Dispatchers.IO) { runQueue() }
        return id
    }

    private suspend fun runQueue() {
        tempDir.listFiles()?.forEach { it.delete() }
        tempDir.mkdirs()
        while (true) {
            val job = _jobs.value.firstOrNull { it.state == JobState.QUEUED } ?: break
            val w = work.remove(job.id)
            if (w == null) {
                setJob(job.id) { it.copy(state = JobState.FAILED) }
                continue
            }
            setJob(job.id) { it.copy(state = JobState.RUNNING) }
            val task = scope.launch(Dispatchers.IO) {
                try {
                    when (w) {
                        is Work.Audiobook -> runAudiobook(job.id, w)
                        is Work.Media -> runMedia(job.id, w)
                    }
                    setJob(job.id) { j ->
                        val ok = j.bookIds.isNotEmpty()
                        j.copy(
                            state = if (ok) JobState.DONE else JobState.FAILED,
                            done = j.total,
                            current = null,
                            fraction = 0f,
                            message = when {
                                !ok -> j.message ?: "Nichts heruntergeladen"
                                j.failed > 0 -> "${j.failed} von ${j.total} fehlgeschlagen" + (j.message?.let { ": $it" } ?: "")
                                else -> null
                            },
                        )
                    }
                    Log.i(TAG, "Auftrag ${job.id} fertig: ${_jobs.value.firstOrNull { it.id == job.id }?.let { "${it.state}, Bücher ${it.bookIds}, Fehler ${it.failed}" }}")
                } catch (e: CancellationException) {
                    setJob(job.id) { it.copy(state = JobState.CANCELLED, current = null) }
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "Auftrag ${job.id} fehlgeschlagen", e)
                    setJob(job.id) { it.copy(state = JobState.FAILED, current = null, message = DownloadErrors.describe(e)) }
                }
            }
            running = job.id to task
            task.join()
            running = null
        }
        tempDir.listFiles()?.forEach { it.delete() }
    }

    // ---------------------------------------------------------------- LibriVox

    private suspend fun runAudiobook(jobId: Long, w: Work.Audiobook) {
        setJob(jobId) { it.copy(current = "Inhaltsverzeichnis …") }
        val book = ArchiveOrg.parseMetadata(w.id, Net.text(ArchiveOrg.metadataUrl(w.id)))
            ?: error("Dieses Hörbuch hat keine MP3-Dateien")
        val files = w.limit?.let { book.files.take(it) } ?: book.files
        setJob(jobId) {
            it.copy(
                title = book.title,
                total = files.size,
                detail = "LibriVox · ${files.size} Kapitel" + (book.author?.let { a -> " · $a" } ?: ""),
            )
        }
        val folder = folderName(book.title, book.author)
        val cover = Net.bytes(book.coverUrl)?.let { saveCover(jobId, it) }
        val saved = ArrayList<Pair<Uri, String>>()
        files.forEachIndexed { i, f ->
            val chapter = f.title ?: f.name.substringBeforeLast('.')
            setJob(jobId) { it.copy(done = i, current = chapter, fraction = 0f) }
            val name = FileNames.track(i, files.size, chapter, "mp3")
            val target = store.create(folder, name, "audio/mpeg")
            try {
                val progress = Throttle()
                target.openStream().use { out ->
                    Net.copyStream(f.url, out) { done, total ->
                        val all = if (total > 0) total else f.bytes ?: 0L
                        if (all > 0 && progress.due()) setJob(jobId) { it.copy(fraction = done.toFloat() / all) }
                    }
                }
                target.finish()
                saved += target.uri to target.name
            } catch (e: Throwable) {
                target.discard()
                if (e is CancellationException) throw e
                Log.w(TAG, "Kapitel ${f.name} fehlgeschlagen", e)
                setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
            }
        }
        importBook(jobId, "download:librivox:${book.id}", book.title, book.author, saved, cover)
    }

    // ---------------------------------------------------------------- videos, links, local files

    private suspend fun runMedia(jobId: Long, w: Work.Media) {
        var index = 0
        for (book in w.books) {
            val folder = folderName(book.title, book.author)
            val saved = ArrayList<Pair<Uri, String>>()
            var cover: File? = null
            var coverBytes: ByteArray? = null
            book.items.forEachIndexed { i, item ->
                setJob(jobId) { it.copy(done = index, current = item.title, fraction = 0f) }
                try {
                    val source = if (w.local) {
                        localSource(item)
                    } else {
                        MediaSources.audioOf(item.url)
                    }
                    if (cover == null && !w.local) {
                        coverBytes = (source.thumbnail ?: item.thumbnail)?.let { Net.bytes(it) }
                        cover = coverBytes?.let { saveCover(jobId, it) }
                    }
                    saved += fetchAndSave(jobId, w, book, item, source, i, folder, if (i == 0) coverBytes else null)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "„${item.title}“ fehlgeschlagen", e)
                    setJob(jobId) { it.copy(failed = it.failed + 1, message = DownloadErrors.describe(e)) }
                }
                index++
            }
            val key = "download:" + sha1(book.items.joinToString("\n") { it.url })
            importBook(jobId, key, book.title, book.author, saved, cover)
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
    ): Pair<Uri, String> {
        val title = if (w.local) item.title.substringBeforeLast('.') else item.title
        val count = book.items.size
        val temp = File(tempDir, "$jobId-$index.${source.extension.ifEmpty { "bin" }}")
        val extracted = File(tempDir, "$jobId-$index-audio")
        try {
            val input: Uri = if (w.local) {
                Uri.parse(source.url)
            } else {
                val progress = Throttle()
                val share = if (w.format == OutputFormat.MP3 && !(source.extension == "mp3" && !source.isVideo)) 0.6f else 1f
                temp.outputStream().use { out ->
                    Net.copyStream(source.url, out) { done, total ->
                        if (total > 0 && progress.due()) setJob(jobId) { it.copy(fraction = share * done / total) }
                    }
                }
                Uri.fromFile(temp)
            }
            val isMp3 = source.extension == "mp3" && !source.isVideo
            if (isMp3 || w.format == OutputFormat.ORIGINAL) {
                val (from, extension) = when {
                    isMp3 -> input to "mp3"
                    else -> AudioConverter.extractAudio(context, input, extracted)?.let { Uri.fromFile(extracted) to it }
                        ?: (null to null)
                }
                if (from != null && extension != null) {
                    val target = store.create(folder, FileNames.track(index, count, title, extension), DownloadStore.mimeOf(extension))
                    try {
                        target.openStream().use { out -> context.contentResolver.openInputStream(from)!!.use { it.copyTo(out, 64 * 1024) } }
                        target.finish()
                    } catch (e: Throwable) {
                        target.discard()
                        throw e
                    }
                    return target.uri to target.name
                }
                // Codecs that cannot be copied as they are (e.g. AC-3 in a video) become MP3.
            }
            val target = store.create(folder, FileNames.track(index, count, title, "mp3"), "audio/mpeg")
            try {
                val progress = Throttle()
                val start = if (w.local) 0f else 0.6f
                target.openStream().use { out ->
                    out.write(Id3.tag(title, book.author, book.title, index + 1, count, cover))
                    AudioConverter.toMp3(context, input, out, w.quality) { f ->
                        if (progress.due()) setJob(jobId) { it.copy(fraction = start + (1 - start) * f) }
                    }
                }
                target.finish()
            } catch (e: Throwable) {
                target.discard()
                throw e
            }
            Log.i(TAG, "Umgewandelt: ${target.name}")
            return target.uri to target.name
        } finally {
            temp.delete()
            extracted.delete()
        }
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun importBook(jobId: Long, key: String, title: String, author: String?, files: List<Pair<Uri, String>>, cover: File?) {
        if (files.isEmpty()) return
        setJob(jobId) { it.copy(current = "In die Bibliothek …") }
        val id = scanner.importFiles(key, title, author, files, cover)
        cover?.delete()
        Log.i(TAG, "Importiert [$key]: $title (${files.size} Dateien) → Buch ${id ?: "–"}")
        if (id != null) setJob(jobId) { it.copy(bookIds = it.bookIds + id) }
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

    /** Limits progress updates to a few per second. */
    private class Throttle {
        private var last = 0L
        fun due(): Boolean {
            val now = System.nanoTime()
            if (now - last < 250_000_000L) return false
            last = now
            return true
        }
    }

    private companion object {
        const val TAG = "LauscherDownload"
    }
}
