package com.hexotronic.fluxplayer.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val folderName: String,
    val folderId: String,
    val dateAdded: Long
)

data class VideoFolder(val id: String, val name: String, val videos: List<VideoItem>) {
    val totalSize: Long get() = videos.sumOf { it.size }
}

object MediaRepository {

    fun loadVideos(context: Context): List<VideoItem> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.DATE_ADDED
        )
        val out = ArrayList<VideoItem>()
        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val iBucket = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val iBucketId = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += VideoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(iName) ?: "Video $id",
                        durationMs = c.getLong(iDur),
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        folderName = c.getString(iBucket) ?: "Unbekannt",
                        folderId = c.getString(iBucketId) ?: "?",
                        dateAdded = c.getLong(iDate)
                    )
                }
            }
        } catch (_: SecurityException) {
            // keine Berechtigung
        }
        return out
    }

    fun folders(videos: List<VideoItem>): List<VideoFolder> =
        videos.groupBy { it.folderId }
            .map { (id, list) -> VideoFolder(id, list.first().folderName, list) }
            .sortedBy { it.name.lowercase() }

    fun sort(videos: List<VideoItem>, order: SortOrder, descending: Boolean): List<VideoItem> {
        val sorted = when (order) {
            SortOrder.NAME -> videos.sortedWith(compareBy(NaturalOrder) { it.name })
            SortOrder.DATE -> videos.sortedBy { it.dateAdded }
            SortOrder.SIZE -> videos.sortedBy { it.size }
            SortOrder.DURATION -> videos.sortedBy { it.durationMs }
        }
        return if (descending) sorted.reversed() else sorted
    }

    /** Dateiname einer content://- oder file://-URI. */
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            } catch (_: Exception) {
            }
        }
        val last = uri.lastPathSegment ?: return uri.toString()
        return Uri.decode(last.substringAfterLast('/')).ifBlank { uri.toString() }
    }
}

/** Natürliche Sortierung („Folge 2“ vor „Folge 10“). */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("(\\d+|\\D+)")
    override fun compare(a: String, b: String): Int {
        val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(ca.size, cb.size)) {
            val x = ca[i]
            val y = cb[i]
            val nx = x.toBigIntegerOrNull()
            val ny = y.toBigIntegerOrNull()
            val r = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
            if (r != 0) return r
        }
        return ca.size - cb.size
    }
}

/** Kleiner Speicher-Cache für Video-Vorschaubilder. */
object ThumbCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun peek(key: String): Bitmap? = cache.get(key)

    @Suppress("DEPRECATION")
    fun load(context: Context, item: VideoItem): Bitmap? {
        val key = item.uri.toString()
        cache.get(key)?.let { return it }
        val bmp = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(item.uri, Size(384, 216), null)
            } else {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null
                )
            }
        } catch (_: Exception) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }
}
