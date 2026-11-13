package io.github.hexotronic.lauscher.download

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.MediaStore
import io.github.hexotronic.lauscher.download.core.StoragePaths
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Where downloaded books are saved, one subfolder per book: by default "Audiobooks/Lauscher/"
 * on the phone's shared storage (Android 10+, otherwise the app's own folder), or a folder the
 * listener picked – on the SD card, or their audiobook folder.
 */
class DownloadStore(private val context: Context) {
    /**
     * A file being written; [finish] makes it visible, [discard] removes it. [folderDocId] is the
     * storage id of the book's folder ("primary:Audiobooks/Lauscher/…"), when known.
     */
    class Target(
        val uri: Uri,
        val name: String,
        val folderDocId: String?,
        private val open: () -> OutputStream,
        private val done: () -> Unit,
        private val drop: () -> Unit,
    ) {
        fun openStream(): OutputStream = open()
        fun finish() = done()
        fun discard() = drop()
    }

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    /** The folder the listener picked, or null for the default. */
    val treeUri: Uri?
        get() = prefs.getString(KEY_TREE, null)?.let(Uri::parse)

    /** Saves into [tree] from now on (a folder from the system's folder picker). */
    fun useFolder(tree: Uri) {
        resolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString(KEY_TREE, tree.toString()).apply()
    }

    fun useDefault() {
        prefs.edit().remove(KEY_TREE).apply()
    }

    val location: String
        get() = treeUri?.let { tree ->
            StoragePaths.describe(DocumentsContract.getTreeDocumentId(tree))
                ?: displayName(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
                ?: "gewählter Ordner"
        } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Telefonspeicher › ${BASE.replace("/", " › ")}"
        } else {
            "Android/data/${context.packageName}/files/$BASE"
        }

    fun create(folder: String, name: String, mime: String): Target {
        treeUri?.let { return createInTree(it, folder, name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$BASE/$folder/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("Datei konnte nicht angelegt werden")
            return Target(
                uri,
                name,
                folderDocId = "primary:$BASE/$folder",
                open = { resolver.openOutputStream(uri, "w") ?: throw IOException("Datei nicht beschreibbar") },
                done = { resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) },
                drop = { runCatching { resolver.delete(uri, null, null) } },
            )
        }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "$BASE/$folder").apply { mkdirs() }
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')).also { n++ }
        return Target(Uri.fromFile(file), file.name, null, open = { FileOutputStream(file) }, done = {}, drop = { file.delete() })
    }

    private fun createInTree(tree: Uri, folder: String, name: String): Target {
        val unreachable = "Der Speicherordner ist nicht erreichbar – bitte unter „Herunterladen“ neu wählen"
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (e: IllegalArgumentException) {
            throw IOException(unreachable, e)
        }
        val dirId = try {
            childId(tree, rootId, folder, Document.MIME_TYPE_DIR)
                ?: DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, rootId), Document.MIME_TYPE_DIR, folder)
                    ?.let { DocumentsContract.getDocumentId(it) }
        } catch (e: Exception) {
            throw IOException(unreachable, e)
        } ?: throw IOException(unreachable)
        // "application/octet-stream" keeps the file name exactly as given (no extension is added).
        val uri = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, dirId), "application/octet-stream", name)
            ?: throw IOException("Datei konnte nicht angelegt werden")
        return Target(
            uri,
            displayName(uri) ?: name,
            folderDocId = dirId,
            open = { resolver.openOutputStream(uri, "w") ?: throw IOException("Datei nicht beschreibbar") },
            done = {},
            drop = { runCatching { DocumentsContract.deleteDocument(resolver, uri) } },
        )
    }

    /** The id of the child [name] (of type [mime]) inside [parentId], if it exists. */
    private fun childId(tree: Uri, parentId: String, name: String, mime: String): String? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == mime) return c.getString(0)
            }
        }
        return null
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        val BASE = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Environment.DIRECTORY_AUDIOBOOKS else "Audiobooks") + "/Lauscher"
        private const val KEY_TREE = "tree"

        fun mimeOf(extension: String): String = when (extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "m4b", "mp4", "aac" -> "audio/mp4"
            "webm" -> "audio/webm"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "audio/*"
        }
    }
}
