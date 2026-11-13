package io.github.hexotronic.lauscher.download.core

/**
 * Document ids of Android's storage provider look like "primary:Audiobooks/Lauscher" (phone
 * storage) or "1A2B-3C4D:Music" (SD card). These helpers compare and describe them.
 */
object StoragePaths {
    /** True if [docId] is the folder [rootId] or lies inside it. */
    fun contains(rootId: String, docId: String): Boolean = when {
        rootId == docId -> true
        // A whole volume ("primary:") contains everything on it.
        rootId.endsWith(":") -> docId.startsWith(rootId)
        else -> docId.startsWith("$rootId/")
    }

    /** "Telefonspeicher › Audiobooks › Lauscher", "SD-Karte › Music"; null for other providers' ids. */
    fun describe(docId: String): String? {
        val volume = docId.substringBefore(':', "")
        if (volume.isEmpty()) return null
        val name = when {
            volume == "primary" -> "Telefonspeicher"
            Regex("[0-9A-F]{4}-[0-9A-F]{4}", RegexOption.IGNORE_CASE).matches(volume) -> "SD-Karte"
            else -> return null
        }
        val path = docId.substringAfter(':').split('/').filter { it.isNotBlank() }
        return (listOf(name) + path).joinToString(" › ")
    }

    /** The id of [child] inside the folder [parentId]. */
    fun child(parentId: String, child: String): String = if (parentId.endsWith(":")) "$parentId$child" else "$parentId/$child"
}
