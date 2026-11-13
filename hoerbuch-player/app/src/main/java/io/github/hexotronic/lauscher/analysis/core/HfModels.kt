package io.github.hexotronic.lauscher.analysis.core

import java.net.URLEncoder

/** A model repository found on Hugging Face. */
data class HfRepo(val id: String, val downloads: Long, val likes: Int, val tags: List<String>, val gated: Boolean = false) {
    val author: String get() = id.substringBefore('/')
    val name: String get() = id.substringAfter('/')
}

/** One model file of a repository, with its size in bytes. */
data class HfFile(val path: String, val size: Long) {
    val fileName: String get() = path.substringAfterLast('/')
    val sizeMb: Int get() = (size / 1_000_000).toInt()
}

/** Whether a model fits into the phone's memory. */
enum class RamFit(val label: String) { GOOD("passt"), TIGHT("knapp"), TOO_BIG("zu groß") }

/**
 * Searching Hugging Face for models the app can run: GGUF language models for llama.cpp and ggml
 * speech models for whisper.cpp. Only parses and builds addresses; the app does the requests.
 */
object HfModels {
    const val HOST = "https://huggingface.co"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Most downloaded repositories with GGUF files (language) or whisper models (speech) for [query]. */
    fun searchUrl(query: String, speech: Boolean, limit: Int = 25): String {
        val q = query.trim().ifEmpty { if (speech) "whisper" else "instruct" }
        val filter = if (speech) "" else "&filter=gguf"
        return "$HOST/api/models?search=${enc(q)}$filter&sort=downloads&direction=-1&limit=$limit"
    }

    /** All files of a repository with their sizes (also in sub folders). */
    fun filesUrl(repo: String): String = "$HOST/api/models/${repo.split('/').joinToString("/") { enc(it) }}/tree/main?recursive=true"

    fun downloadUrl(repo: String, path: String): String =
        "$HOST/${repo.split('/').joinToString("/") { enc(it) }}/resolve/main/${path.split('/').joinToString("/") { enc(it) }}"

    fun pageUrl(repo: String) = "$HOST/$repo"

    fun parseSearch(json: String): List<HfRepo> {
        val list = runCatching { MiniJson.parse(json) }.getOrNull() as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] ?: m["modelId"]) as? String ?: return@mapNotNull null
            if (m["private"] == true) return@mapNotNull null
            HfRepo(
                id,
                (m["downloads"] as? Number)?.toLong() ?: 0L,
                (m["likes"] as? Number)?.toInt() ?: 0,
                (m["tags"] as? List<*>).orEmpty().filterIsInstance<String>(),
                gated = m["gated"].let { it == true || (it is String && it != "false") },
            )
        }
    }

    /**
     * The usable model files of a repository: whole GGUF files for language models (no parts of split
     * files, no vision add-ons), ggml whisper files for speech; the recommended quantisation first, then by size.
     */
    fun parseFiles(json: String, speech: Boolean): List<HfFile> {
        val list = runCatching { MiniJson.parse(json) }.getOrNull() as? List<*> ?: return emptyList()
        val files = list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            if (m["type"] != "file") return@mapNotNull null
            val path = m["path"] as? String ?: return@mapNotNull null
            val size = ((m["lfs"] as? Map<*, *>)?.get("size") as? Number ?: m["size"] as? Number)?.toLong() ?: 0L
            HfFile(path, size)
        }.filter { usable(it.fileName, speech) && it.size > 1_000_000 }
        return files.sortedWith(compareBy<HfFile> { rank(it.fileName) }.thenBy { it.size })
    }

    fun usable(name: String, speech: Boolean): Boolean {
        val n = name.lowercase()
        return if (speech) {
            n.endsWith(".bin") && n.startsWith("ggml") && "encoder" !in n && "coreml" !in n
        } else {
            n.endsWith(".gguf") && "mmproj" !in n && !Regex("-\\d{5}-of-\\d{5}\\.gguf$").containsMatchIn(n) && "imatrix" !in n
        }
    }

    /** "Q4_K_M", "IQ4_XS", "Q8_0", "F16", "q5_1" … from a file name, upper case; null if none is named. */
    fun quantOf(name: String): String? =
        Regex("(?i)(?<![a-z0-9])(i?q[1-8](_[0-9a-z]+){0,2}|f16|bf16|f32)(?![a-z0-9])").findAll(name.substringBeforeLast('.'))
            .lastOrNull()?.value?.uppercase()

    /** Smaller is better: the quantisations that are a good trade of size and quality on phones come first. */
    private fun rank(name: String): Int {
        val q = quantOf(name) ?: return 50
        return PREFERRED.indexOf(q).takeIf { it >= 0 } ?: (20 + if (q.startsWith("F") || q.startsWith("BF")) 20 else 0)
    }

    private val PREFERRED = listOf("Q4_K_M", "Q4_K_S", "IQ4_XS", "Q4_0", "IQ4_NL", "Q5_K_M", "Q5_0", "Q5_1", "Q5_K_S", "Q3_K_L", "Q6_K", "Q3_K_M", "Q8_0")

    /** The file the app suggests: the best quantisation that fits the memory, else the smallest. */
    fun recommended(files: List<HfFile>, ramMb: Int): HfFile? =
        files.firstOrNull { fit(it.size, ramMb) == RamFit.GOOD } ?: files.firstOrNull { fit(it.size, ramMb) == RamFit.TIGHT } ?: files.minByOrNull { it.size }

    /**
     * About how much memory running a model needs: the file (mapped into memory) plus the context
     * (4096 tokens) and work buffers.
     */
    fun neededMb(fileBytes: Long): Int = (fileBytes / 1_000_000 * 1.15 + 350).toInt()

    /** Android and the other apps keep about 40 % of the memory for themselves. */
    fun fit(fileBytes: Long, ramMb: Int): RamFit {
        if (ramMb <= 0) return RamFit.GOOD
        val need = neededMb(fileBytes)
        return when {
            need <= ramMb * 0.35 -> RamFit.GOOD
            need <= ramMb * 0.6 -> RamFit.TIGHT
            else -> RamFit.TOO_BIG
        }
    }

    /** "1.5B", "3B", "0.5B", "270M" from a name, in billions; null if it does not say. */
    fun billionsOf(name: String): Float? {
        val m = Regex("(?i)(?<![a-z0-9.])(\\d+(?:\\.\\d+)?)\\s*([bm])(?![a-z])").find(name) ?: return null
        val n = m.groupValues[1].toFloatOrNull() ?: return null
        return if (m.groupValues[2].equals("m", ignoreCase = true)) n / 1000f else n
    }

    /** Language-model repositories that are not chat models (base models, embeddings, rerankers) answer badly. */
    fun looksLikeChatModel(repo: HfRepo): Boolean {
        val n = repo.id.lowercase()
        if (listOf("embed", "rerank", "reward", "vision-encoder", "-base-", "tts", "whisper").any { it in n }) return false
        return listOf("instruct", "-it", "chat", "assistant").any { it in n } || "conversational" in repo.tags
    }

    /** A stable id for a file of a repository the listener added. */
    fun idFor(repo: String, path: String) = "hf:$repo/$path"

    /** The local file name for it: readable and without collisions between repositories. */
    fun localName(repo: String, path: String): String =
        ("hf-" + repo.replace('/', '_') + "-" + path.substringAfterLast('/')).replace(Regex("[^A-Za-z0-9._-]"), "_").take(160)

    /** "12,3 Tsd." / "1,2 Mio." downloads. */
    fun count(n: Long): String = when {
        n >= 1_000_000 -> "%.1f".format(java.util.Locale.ROOT, n / 1e6).replace('.', ',') + " Mio."
        n >= 1_000 -> "%.1f".format(java.util.Locale.ROOT, n / 1e3).replace('.', ',') + " Tsd."
        else -> n.toString()
    }
}
