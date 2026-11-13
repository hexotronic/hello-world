package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.hexotronic.lauscher.analysis.core.BookText
import io.github.hexotronic.lauscher.analysis.core.BookTextParser
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Opens a book text the listener picked or shared: EPUB, HTML or plain text. */
object BookTextReader {
    /** Returns the parsed text and a short description of where it came from. */
    fun read(context: Context, uri: Uri): Pair<BookText, String> {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val mime = resolver.getType(uri).orEmpty()
        val lower = name.lowercase()
        return when {
            mime == "application/epub+zip" || lower.endsWith(".epub") -> {
                val book = resolver.openInputStream(uri)?.use { BookTextParser.fromEpub(it) } ?: error("Datei nicht lesbar")
                book to "EPUB: $name"
            }
            mime.contains("html") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml") -> {
                BookTextParser.fromPlain(BookTextParser.htmlToText(readString(context, uri))) to "HTML: $name"
            }
            else -> BookTextParser.fromPlain(readString(context, uri)) to "Text: $name"
        }
    }

    /** UTF-8, or Windows-1252 for older text files that are not valid UTF-8. */
    private fun readString(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Datei nicht lesbar")
        if (bytes.size > MAX_BYTES) error("Die Datei ist zu groß (über ${MAX_BYTES / 1_000_000} MB)")
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }.removePrefix("﻿")
    }

    private const val MAX_BYTES = 30_000_000
}
