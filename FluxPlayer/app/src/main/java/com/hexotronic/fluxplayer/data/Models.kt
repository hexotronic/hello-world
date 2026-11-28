package com.hexotronic.fluxplayer.data

/** Ein abspielbarer Eintrag (lokal oder Netzwerk). Reines Kotlin, damit unit-testbar. */
data class MediaEntry(
    val uri: String,
    val title: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val forceFfmpeg: Boolean = false
)

/** Übergabe großer Playlists an den Player ohne Intent-Größenlimit (gleicher Prozess). */
object PlaylistHandoff {
    @Volatile private var entries: List<MediaEntry>? = null
    @Volatile private var startIndex: Int = 0

    fun put(list: List<MediaEntry>, index: Int) {
        entries = list
        startIndex = index
    }

    fun take(): Pair<List<MediaEntry>, Int>? {
        val e = entries ?: return null
        entries = null
        return e to startIndex
    }
}
