package io.github.hexotronic.lauscher.download.core

import io.github.hexotronic.lauscher.analysis.core.MiniJson

/**
 * A download job as kept on disk, so it survives the app being closed: what it is about, how far
 * it got (finished items, saved files) and its numbers. Partial files stay next to it.
 */
data class SavedJob(
    val id: Long,
    val title: String,
    val detail: String,
    val total: Int,
    val done: Int,
    val failed: Int,
    /** Name of the job state ("RUNNING", "PAUSED", "DONE", …). */
    val state: String,
    val message: String?,
    val bookIds: List<Long>,
    val bytes: Long,
    val bytesTotal: Long,
    val activeMs: Long,
    /** "audiobook" (LibriVox) or "media" (videos, links, files on the phone). */
    val kind: String,
    val archiveId: String? = null,
    val limit: Int? = null,
    val next: Int = 0,
    val books: List<PlannedBook> = emptyList(),
    val format: String? = null,
    val quality: String? = null,
    val local: Boolean = false,
    val bookIndex: Int = 0,
    val itemIndex: Int = 0,
    val doneItems: Int = 0,
    /** Files of the current book already saved: (uri, name). */
    val saved: List<Pair<String, String>> = emptyList(),
    val cover: String? = null,
    val folderDocId: String? = null,
)

object JobStore {
    fun encode(jobs: List<SavedJob>): String = buildString {
        append("{\"version\":1,\"jobs\":[")
        jobs.forEachIndexed { i, j ->
            if (i > 0) append(',')
            append('{')
            field("id", j.id)
            field("title", j.title)
            field("detail", j.detail)
            field("total", j.total)
            field("done", j.done)
            field("failed", j.failed)
            field("state", j.state)
            field("message", j.message)
            append("\"bookIds\":[").append(j.bookIds.joinToString(",")).append("],")
            field("bytes", j.bytes)
            field("bytesTotal", j.bytesTotal)
            field("activeMs", j.activeMs)
            field("kind", j.kind)
            field("archiveId", j.archiveId)
            field("limit", j.limit)
            field("next", j.next)
            append("\"books\":[")
            j.books.forEachIndexed { b, book ->
                if (b > 0) append(',')
                append('{')
                field("title", book.title)
                field("author", book.author)
                append("\"items\":[")
                book.items.forEachIndexed { k, item ->
                    if (k > 0) append(',')
                    append('{')
                    field("url", item.url)
                    field("title", item.title)
                    field("uploader", item.uploader)
                    field("duration", item.durationSeconds)
                    field("thumbnail", item.thumbnail, last = true)
                    append('}')
                }
                append("]}")
            }
            append("],")
            field("format", j.format)
            field("quality", j.quality)
            field("local", j.local)
            field("bookIndex", j.bookIndex)
            field("itemIndex", j.itemIndex)
            field("doneItems", j.doneItems)
            append("\"saved\":[")
            j.saved.forEachIndexed { k, (uri, name) ->
                if (k > 0) append(',')
                append('[').append(quote(uri)).append(',').append(quote(name)).append(']')
            }
            append("],")
            field("cover", j.cover)
            field("folderDocId", j.folderDocId, last = true)
            append('}')
        }
        append("]}")
    }

    /** The jobs in [text]; an unreadable file gives an empty list rather than an error. */
    fun decode(text: String): List<SavedJob> {
        val root = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *> ?: return emptyList()
        return (root["jobs"] as? List<*>).orEmpty().mapNotNull { e -> runCatching { job(e as Map<*, *>) }.getOrNull() }
    }

    private fun job(m: Map<*, *>): SavedJob = SavedJob(
        id = long(m["id"]) ?: error("no id"),
        title = m["title"] as? String ?: "",
        detail = m["detail"] as? String ?: "",
        total = long(m["total"])?.toInt() ?: 0,
        done = long(m["done"])?.toInt() ?: 0,
        failed = long(m["failed"])?.toInt() ?: 0,
        state = m["state"] as? String ?: "PAUSED",
        message = m["message"] as? String,
        bookIds = (m["bookIds"] as? List<*>).orEmpty().mapNotNull { long(it) },
        bytes = long(m["bytes"]) ?: 0,
        bytesTotal = long(m["bytesTotal"]) ?: -1,
        activeMs = long(m["activeMs"]) ?: 0,
        kind = m["kind"] as? String ?: error("no kind"),
        archiveId = m["archiveId"] as? String,
        limit = long(m["limit"])?.toInt(),
        next = long(m["next"])?.toInt() ?: 0,
        books = (m["books"] as? List<*>).orEmpty().mapNotNull { b ->
            val bm = b as? Map<*, *> ?: return@mapNotNull null
            PlannedBook(
                title = bm["title"] as? String ?: "",
                author = bm["author"] as? String,
                items = (bm["items"] as? List<*>).orEmpty().mapNotNull { i ->
                    val im = i as? Map<*, *> ?: return@mapNotNull null
                    MediaItem(
                        url = im["url"] as? String ?: return@mapNotNull null,
                        title = im["title"] as? String ?: "",
                        uploader = im["uploader"] as? String,
                        durationSeconds = long(im["duration"]) ?: -1,
                        thumbnail = im["thumbnail"] as? String,
                    )
                },
            )
        },
        format = m["format"] as? String,
        quality = m["quality"] as? String,
        local = m["local"] as? Boolean ?: false,
        bookIndex = long(m["bookIndex"])?.toInt() ?: 0,
        itemIndex = long(m["itemIndex"])?.toInt() ?: 0,
        doneItems = long(m["doneItems"])?.toInt() ?: 0,
        saved = (m["saved"] as? List<*>).orEmpty().mapNotNull { p ->
            val pair = p as? List<*> ?: return@mapNotNull null
            val uri = pair.getOrNull(0) as? String ?: return@mapNotNull null
            uri to (pair.getOrNull(1) as? String ?: "")
        },
        cover = m["cover"] as? String,
        folderDocId = m["folderDocId"] as? String,
    )

    private fun long(v: Any?): Long? = (v as? Number)?.toLong()

    private fun StringBuilder.field(name: String, value: Any?, last: Boolean = false) {
        append('"').append(name).append("\":")
        when (value) {
            null -> append("null")
            is String -> append(quote(value))
            is Boolean, is Number -> append(value.toString())
            else -> append(quote(value.toString()))
        }
        if (!last) append(',')
    }

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
