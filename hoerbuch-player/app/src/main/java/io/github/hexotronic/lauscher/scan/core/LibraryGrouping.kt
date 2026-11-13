package io.github.hexotronic.lauscher.scan.core

import java.security.MessageDigest

/** Compares "Kapitel 2" < "Kapitel 10", ignoring case. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

/** A file or folder as seen by the scanner. [id] is opaque (a SAF document id or a path). */
data class FsEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String? = null,
    val size: Long = 0,
    val lastModified: Long = 0,
    val children: List<FsEntry> = emptyList(),
)

/** A detected audiobook: an ordered list of audio files plus candidate cover images. */
data class BookCandidate(
    val key: String,
    val title: String,
    val parentFolderName: String?,
    val files: List<FsEntry>,
    val images: List<FsEntry>,
) {
    /** Changes whenever files are added, removed, renamed or modified. */
    val signature: String by lazy {
        val md = MessageDigest.getInstance("SHA-1")
        for (f in files) md.update("${f.id}|${f.size}|${f.lastModified};".toByteArray())
        md.digest().joinToString("") { "%02x".format(it) }
    }
}

object LibraryGrouping {
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "mka", "amr", "awb", "3gp", "mp2", "weba", "webm",
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    private val STANDALONE_EXTENSIONS = setOf("m4b")
    private val DISC_FOLDER = Regex(
        "^(cd|disc|disk|teil|part|tape|kassette|seite|side|vol|volume|band|folge)[\\s._-]*\\d+.*",
        RegexOption.IGNORE_CASE,
    )

    fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

    fun isAudio(entry: FsEntry): Boolean {
        if (entry.isDirectory) return false
        if (entry.name.startsWith(".")) return false
        val ext = extension(entry.name)
        if (ext in AUDIO_EXTENSIONS) return true
        return entry.mimeType?.startsWith("audio/") == true && ext != "m3u" && ext != "pls"
    }

    fun isImage(entry: FsEntry) = !entry.isDirectory && extension(entry.name) in IMAGE_EXTENSIONS

    fun group(root: FsEntry): List<BookCandidate> {
        val out = ArrayList<BookCandidate>()
        visit(root, parentName = null, out)
        return out
    }

    private fun visit(dir: FsEntry, parentName: String?, out: MutableList<BookCandidate>) {
        val audio = dir.children.filter(::isAudio).sortedWith(compareBy(NaturalOrder) { it.name })
        val images = dir.children.filter(::isImage)
        val subDirs = dir.children.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedWith(compareBy(NaturalOrder) { it.name })
        val discDirs = subDirs.filter { DISC_FOLDER.matches(it.name) && it.children.any(::isAudio) }
        val otherDirs = subDirs - discDirs.toSet()

        val standalone = audio.filter { extension(it.name) in STANDALONE_EXTENSIONS }
        if (standalone.size > 1 && standalone.size == audio.size && discDirs.isEmpty()) {
            // A folder full of .m4b files: every file is its own book.
            for (f in standalone) {
                out.add(BookCandidate("file:${f.id}", f.name.substringBeforeLast('.'), dir.name, listOf(f), images))
            }
        } else if (audio.isNotEmpty() || discDirs.isNotEmpty()) {
            val files = ArrayList(audio)
            val allImages = ArrayList(images)
            for (disc in discDirs) {
                files.addAll(disc.children.filter(::isAudio).sortedWith(compareBy(NaturalOrder) { it.name }))
                allImages.addAll(disc.children.filter(::isImage))
            }
            out.add(BookCandidate("dir:${dir.id}", dir.name, parentName, files, allImages))
        }
        for (sub in otherDirs) visit(sub, dir.name, out)
    }

    /**
     * Picks title and author from tags and folder names. Supports "Autor - Titel" folder names
     * and the common "Autor/Titel/…" layout.
     */
    fun guessTitleAndAuthor(
        candidate: BookCandidate,
        tagAlbum: String?,
        tagArtist: String?,
        tagAlbumArtist: String?,
    ): Pair<String, String?> {
        val folderTitle = candidate.title
        val dash = folderTitle.split(" - ", " – ", limit = 2)
        val authorFromFolder = if (dash.size == 2) dash[0].trim() else null
        val titleFromFolder = if (dash.size == 2) dash[1].trim() else folderTitle.trim()
        val title = tagAlbum?.takeIf { it.isNotBlank() } ?: titleFromFolder
        val author = tagAlbumArtist?.takeIf { it.isNotBlank() }
            ?: tagArtist?.takeIf { it.isNotBlank() }
            ?: authorFromFolder
            ?: candidate.parentFolderName?.takeIf { it.isNotBlank() }
        return title to author
    }

    /** Ranks image files: cover/folder/front first, larger files next. */
    fun bestCoverImage(images: List<FsEntry>): FsEntry? {
        val preferred = listOf("cover", "folder", "front", "albumart", "book")
        return images.sortedWith(
            compareBy<FsEntry> { img ->
                val base = img.name.substringBeforeLast('.').lowercase()
                preferred.indexOfFirst { base.startsWith(it) }.let { if (it < 0) preferred.size else it }
            }.thenByDescending { it.size },
        ).firstOrNull()
    }
}
