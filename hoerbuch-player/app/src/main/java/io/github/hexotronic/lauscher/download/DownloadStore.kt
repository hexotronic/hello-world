package io.github.hexotronic.lauscher.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Where downloaded books are saved: "Audiobooks/Lauscher/<Buch>/" on the phone's shared storage
 * (visible to file managers and other players, Android 10+), otherwise the app's own folder.
 */
class DownloadStore(private val context: Context) {
    /** A file being written; [finish] makes it visible, [discard] removes it. */
    class Target(val uri: Uri, val name: String, private val open: () -> OutputStream, private val done: () -> Unit, private val drop: () -> Unit) {
        fun openStream(): OutputStream = open()
        fun finish() = done()
        fun discard() = drop()
    }

    val location: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Telefonspeicher › $BASE"
        } else {
            "Android/data/${context.packageName}/files/$BASE"
        }

    fun create(folder: String, name: String, mime: String): Target {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
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
                open = { resolver.openOutputStream(uri, "w") ?: throw IOException("Datei nicht beschreibbar") },
                done = { resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) },
                drop = { runCatching { resolver.delete(uri, null, null) } },
            )
        }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "$BASE/$folder").apply { mkdirs() }
        var file = File(dir, name)
        var n = 1
        while (file.exists()) file = File(dir, name.substringBeforeLast('.') + " ($n)." + name.substringAfterLast('.')).also { n++ }
        return Target(Uri.fromFile(file), file.name, open = { FileOutputStream(file) }, done = {}, drop = { file.delete() })
    }

    companion object {
        val BASE = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Environment.DIRECTORY_AUDIOBOOKS else "Audiobooks") + "/Lauscher"

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
