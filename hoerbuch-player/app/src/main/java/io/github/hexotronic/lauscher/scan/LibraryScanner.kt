package io.github.hexotronic.lauscher.scan

import io.github.hexotronic.lauscher.download.core.StoragePaths
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import io.github.hexotronic.lauscher.data.Chapter
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.Track
import io.github.hexotronic.lauscher.scan.core.BookCandidate
import io.github.hexotronic.lauscher.scan.core.FsEntry
import io.github.hexotronic.lauscher.scan.core.Id3ChapterParser
import io.github.hexotronic.lauscher.scan.core.LibraryGrouping
import io.github.hexotronic.lauscher.scan.core.Mp4ChapterParser
import io.github.hexotronic.lauscher.scan.core.RandomAccessSource
import io.github.hexotronic.lauscher.scan.core.RawChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val folder: String, val done: Int, val total: Int, val current: String) : ScanState
    data class Finished(val books: Int, val added: Int, val errors: Int) : ScanState
}

/**
 * Walks the folders the user granted through the Storage Access Framework, groups audio
 * files into audiobooks and extracts durations, tags, chapters and covers.
 * Books whose files did not change are skipped, so re-scans are fast.
 */
class LibraryScanner(private val context: Context, private val repo: LibraryRepository) {
    private val resolver = context.contentResolver
    private val coverDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val mutex = Mutex()
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    suspend fun addFolder(treeUri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = displayNameOfTree(treeUri)
        repo.addFolder(treeUri.toString(), name)
        scanAll(force = false)
    }

    suspend fun removeFolder(uri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        repo.removeFolder(uri)
    }

    /** Imports single files (e.g. one .m4b) as books. */
    suspend fun addFiles(uris: List<Uri>): Long? = mutex.withLock {
        withContext(Dispatchers.IO) {
            var lastId: Long? = null
            for (uri in uris) {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Hörbuch"
                val entry = FsEntry(id = uri.toString(), name = name, isDirectory = false)
                val candidate = BookCandidate("doc:$uri", name.substringBeforeLast('.'), null, listOf(entry), emptyList())
                lastId = runCatching { importBook(candidate, uri.toString()) { it -> Uri.parse(it.id) } }
                    .onFailure { Log.w(TAG, "Import failed for $uri", it) }
                    .getOrNull()
            }
            lastId
        }
    }

    /**
     * Imports downloaded or converted [files] (in this order) as one book named exactly [title]
     * by [author], whatever the files' own tags say; [cover] is an optional image file. [rootUri]
     * is the library folder the files lie in, if any.
     */
    suspend fun importFiles(
        key: String,
        title: String,
        author: String?,
        files: List<Pair<Uri, String>>,
        cover: File?,
        rootUri: String? = null,
    ): Long? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val entries = files.map { (uri, name) -> FsEntry(id = uri.toString(), name = name, isDirectory = false) }
            val images = listOfNotNull(
                cover?.takeIf { it.length() > 0 }?.let { FsEntry(id = Uri.fromFile(it).toString(), name = "cover.jpg", isDirectory = false, size = it.length()) },
            )
            val candidate = BookCandidate(key, title, null, entries, images)
            runCatching { importBook(candidate, rootUri ?: ROOT_DOWNLOADS) { Uri.parse(it.id) } }
                .onFailure { Log.w(TAG, "Import failed for $title", it) }
                .getOrNull()
                ?.also { id -> repo.updateInfo(id, title, author, null) }
        }
    }

    /**
     * The library folder (its URI) that contains the storage folder [docId], if any – books saved
     * there get the key a later scan of that folder would give them, so they are not added twice.
     */
    suspend fun libraryFolderOf(docId: String): String? = repo.folderList().firstOrNull { folder ->
        runCatching { StoragePaths.contains(DocumentsContract.getTreeDocumentId(Uri.parse(folder.uri)), docId) }.getOrDefault(false)
    }?.uri

    suspend fun scanAll(force: Boolean) = mutex.withLock {
        withContext(Dispatchers.IO) {
            var books = 0
            var added = 0
            var errors = 0
            for (folder in repo.folderList()) {
                val treeUri = Uri.parse(folder.uri)
                _state.value = ScanState.Running(folder.name, 0, 0, "Ordner werden gelesen …")
                val root = runCatching { readTree(treeUri) }.getOrElse {
                    Log.w(TAG, "Cannot read ${folder.uri}", it)
                    errors++
                    null
                } ?: continue
                val candidates = LibraryGrouping.group(root)
                val present = HashSet<String>()
                candidates.forEachIndexed { i, candidate ->
                    _state.value = ScanState.Running(folder.name, i, candidates.size, candidate.title)
                    present.add(candidate.key)
                    val existing = repo.bookBySourceKey(candidate.key)
                    if (!force && existing != null && existing.signature == candidate.signature && !existing.missing) {
                        books++
                        return@forEachIndexed
                    }
                    try {
                        importBook(candidate, folder.uri) { entry -> DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.id) }
                        books++
                        if (existing == null) added++
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to import ${candidate.title}", e)
                        errors++
                    }
                }
                repo.markMissing(folder.uri, present)
            }
            _state.value = ScanState.Finished(books, added, errors)
        }
    }

    fun clearFinishedState() {
        if (_state.value is ScanState.Finished) _state.value = ScanState.Idle
    }

    // ---------------------------------------------------------------- tree reading

    private fun readTree(treeUri: Uri): FsEntry {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return FsEntry(rootId, displayNameOfTree(treeUri), isDirectory = true, children = children(treeUri, rootId, 0))
    }

    private fun children(treeUri: Uri, documentId: String, depth: Int): List<FsEntry> {
        if (depth > MAX_DEPTH) return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<FsEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (c.isNull(3)) 0L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4)
                out.add(FsEntry(id, name, isDir, mime, size, modified))
            }
        }
        return out.map { if (it.isDirectory) it.copy(children = children(treeUri, it.id, depth + 1)) else it }
    }

    private fun displayNameOfTree(treeUri: Uri): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        return queryDisplayName(docUri) ?: treeUri.lastPathSegment?.substringAfterLast(':') ?: "Ordner"
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- import

    private suspend fun importBook(candidate: BookCandidate, rootUri: String, uriOf: (FsEntry) -> Uri): Long {
        val tracks = ArrayList<Track>()
        val chapters = ArrayList<Chapter>()
        var album: String? = null
        var artist: String? = null
        var albumArtist: String? = null
        var narrator: String? = null
        var year: String? = null
        var description: String? = null
        var embeddedPicture: ByteArray? = null
        var start = 0L

        candidate.files.forEachIndexed { index, entry ->
            val uri = uriOf(entry)
            val retriever = MediaMetadataRetriever()
            var duration = 0L
            var trackTitle: String? = null
            try {
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                trackTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (index == 0) {
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4)
                    description = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                }
                if (embeddedPicture == null && index < 3) embeddedPicture = retriever.embeddedPicture
            } catch (e: Exception) {
                Log.w(TAG, "No metadata for ${entry.name}", e)
            } finally {
                runCatching { retriever.release() }
            }

            val fileName = entry.name.substringBeforeLast('.')
            val title = trackTitle?.takeIf { it.isNotBlank() && candidate.files.size > 1 } ?: fileName
            tracks.add(Track(0, index, uri.toString(), title, duration, start))

            val embedded = readEmbeddedChapters(uri, entry.name)
                .filter { it.startMs < duration || duration == 0L }
            if (embedded.size > 1 || (embedded.size == 1 && candidate.files.size == 1)) {
                embedded.forEachIndexed { i, ch ->
                    val end = embedded.getOrNull(i + 1)?.startMs ?: duration
                    val name = ch.title.ifBlank { "Kapitel ${chapters.size + 1}" }
                    chapters.add(Chapter(0, chapters.size, name, start + ch.startMs, start + end))
                }
            } else {
                chapters.add(Chapter(0, chapters.size, title, start, start + duration))
            }
            start += duration
        }

        val (title, author) = LibraryGrouping.guessTitleAndAuthor(
            candidate,
            album.takeIf { candidate.files.size > 1 || !album.isNullOrBlank() },
            artist,
            albumArtist,
        )
        val coverPath = saveCover(candidate, uriOf, embeddedPicture)
        return repo.saveScannedBook(
            sourceKey = candidate.key,
            rootUri = rootUri,
            title = title,
            author = author,
            narrator = narrator?.takeIf { it != author },
            year = year,
            description = description,
            coverPath = coverPath,
            signature = candidate.signature,
            tracks = tracks,
            chapters = chapters,
        )
    }

    private fun readEmbeddedChapters(uri: Uri, name: String): List<RawChapter> {
        val ext = LibraryGrouping.extension(name)
        val parser: ((RandomAccessSource) -> List<RawChapter>)? = when (ext) {
            "m4b", "m4a", "mp4", "aac", "3gp" -> Mp4ChapterParser::parse
            "mp3" -> Id3ChapterParser::parse
            else -> null
        }
        if (parser == null) return emptyList()
        return runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd -> parser(FileSource(pfd)) }.orEmpty()
        }.getOrElse {
            Log.w(TAG, "Chapter parsing failed for $name", it)
            emptyList()
        }
    }

    private fun saveCover(candidate: BookCandidate, uriOf: (FsEntry) -> Uri, embedded: ByteArray?): String? {
        val target = File(coverDir, sha1(candidate.key) + ".jpg")
        val image = LibraryGrouping.bestCoverImage(candidate.images)
        val bitmap: Bitmap? = image?.let { img ->
            runCatching { resolver.openInputStream(uriOf(img))?.use { it.readBytes() } }.getOrNull()?.let(::decodeScaled)
        } ?: embedded?.let(::decodeScaled)
        if (bitmap == null) return if (target.exists()) target.absolutePath else null
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return target.absolutePath
    }

    /** Copies a user-picked image as custom cover. */
    suspend fun setCustomCover(bookId: Long, sourceKey: String, image: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching { resolver.openInputStream(image)?.use { it.readBytes() } }.getOrNull() ?: return@withContext false
        val bitmap = decodeScaled(bytes) ?: return@withContext false
        val target = File(coverDir, sha1(sourceKey) + "_custom_" + System.currentTimeMillis() + ".jpg")
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        repo.setCover(bookId, target.absolutePath, custom = true)
        true
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_COVER_PX && bounds.outHeight / (sample * 2) >= MAX_COVER_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= MAX_COVER_PX) return decoded
        val scale = MAX_COVER_PX.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Random access over a file descriptor; the descriptor is owned and closed by the caller. */
    private class FileSource(pfd: ParcelFileDescriptor) : RandomAccessSource {
        private val channel = FileInputStream(pfd.fileDescriptor).channel
        override val length: Long = channel.size()
        override fun read(position: Long, buffer: ByteArray, offset: Int, count: Int): Int =
            channel.read(ByteBuffer.wrap(buffer, offset, count), position)
    }

    private companion object {
        const val TAG = "LibraryScanner"
        const val MAX_DEPTH = 10
        const val MAX_COVER_PX = 1000

        /** Root of books that Lauscher downloaded or converted itself. */
        const val ROOT_DOWNLOADS = "lauscher:downloads"
    }
}
