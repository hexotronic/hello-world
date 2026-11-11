package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.hexotronic.lauscher.analysis.core.HfFile
import io.github.hexotronic.lauscher.analysis.core.HfModels
import io.github.hexotronic.lauscher.analysis.core.HfRepo
import io.github.hexotronic.lauscher.analysis.core.MiniJson
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class ModelKind { SPEECH, LANGUAGE }

/** A downloadable on-device model. Files are fetched once and then used offline. */
data class ModelInfo(
    val id: String,
    val kind: ModelKind,
    val label: String,
    val description: String,
    val url: String?,
    val fileName: String,
    val sizeMb: Int,
    /** The Hugging Face repository of a model the listener added himself, else null. */
    val repo: String? = null,
) {
    val isUserModel: Boolean get() = repo != null
}

object ModelCatalog {
    private const val WHISPER = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    val models = listOf(
        ModelInfo(
            "whisper-base", ModelKind.SPEECH, "Whisper Base",
            "Empfohlen: guter Kompromiss aus Tempo und Genauigkeit, viele Sprachen",
            WHISPER + "ggml-base-q5_1.bin", "ggml-base-q5_1.bin", 60,
        ),
        ModelInfo(
            "whisper-tiny", ModelKind.SPEECH, "Whisper Tiny",
            "Sehr schnell, für ältere Handys; erkennt weniger genau",
            WHISPER + "ggml-tiny-q5_1.bin", "ggml-tiny-q5_1.bin", 32,
        ),
        ModelInfo(
            "whisper-small", ModelKind.SPEECH, "Whisper Small",
            "Genauer, braucht aber deutlich mehr Rechenzeit",
            WHISPER + "ggml-small-q5_1.bin", "ggml-small-q5_1.bin", 190,
        ),
        // Language models, ordered by the model comparison in CI (German test story; see README).
        ModelInfo(
            "qwen3-1.7b", ModelKind.LANGUAGE, "Qwen3 1.7B",
            "Empfohlen: versteht im Test fast so gut wie Qwen2.5 3B und ist so schnell wie ein mittleres Modell (ab 4 GB RAM)",
            "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            "Qwen3-1.7B-Q4_K_M.gguf", 1110,
        ),
        ModelInfo(
            "qwen3-4b-2507", ModelKind.LANGUAGE, "Qwen3 4B Instruct 2507",
            "Versteht am besten – Szenen, Fragen und Befehle; braucht etwa doppelt so lange wie Qwen2.5 3B (ab 8 GB RAM)",
            "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_K_M.gguf",
            "Qwen3-4B-Instruct-2507-Q4_K_M.gguf", 2500,
        ),
        ModelInfo(
            "gemma3-4b", ModelKind.LANGUAGE, "Gemma 3 4B",
            "Ebenso gut, trifft die Musik-Merkmale am besten; etwas schneller als Qwen3 4B (ab 8 GB RAM, Gemma-Nutzungsbedingungen)",
            "https://huggingface.co/unsloth/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf",
            "gemma-3-4b-it-Q4_K_M.gguf", 2490,
        ),
        ModelInfo(
            "qwen2.5-3b", ModelKind.LANGUAGE, "Qwen2.5 3B Instruct",
            "Gute Szenen und Antworten, versteht Befehle aber schlechter als die neuen Modelle (ab 6 GB RAM)",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            "qwen2.5-3b-instruct-q4_k_m.gguf", 2100,
        ),
        ModelInfo(
            "qwen2.5-1.5b", ModelKind.LANGUAGE, "Qwen2.5 1.5B Instruct",
            "Älter: beantwortet Fragen ordentlich, verwechselt aber oft Szenen (ab 4 GB RAM)",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1120,
        ),
        ModelInfo(
            "qwen2.5-0.5b", ModelKind.LANGUAGE, "Qwen2.5 0.5B Instruct",
            "Sehr klein und schnell, aber nur für einfache Fälle",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf", 400,
        ),
    )

    fun byId(id: String?): ModelInfo? = models.firstOrNull { it.id == id }

    /** Id used for a GGUF/ggml file the user picked himself. */
    fun customId(kind: ModelKind) = if (kind == ModelKind.SPEECH) "custom-speech" else "custom-language"
}

data class DownloadState(val modelId: String, val bytes: Long, val total: Long, val error: String? = null) {
    val progress: Float get() = if (total > 0) bytes.toFloat() / total else 0f
}

/** Downloads, imports and locates model files (app-specific storage, no permissions needed). */
class ModelManager(private val context: Context) {
    private val _download = MutableStateFlow<DownloadState?>(null)
    val download: StateFlow<DownloadState?> = _download.asStateFlow()
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()
    private var job: Job? = null
    private val prefs = context.getSharedPreferences("user_models", Context.MODE_PRIVATE)
    private val _userModels = MutableStateFlow(loadUserModels())

    /** Models the listener found on Hugging Face and added (downloaded or not yet). */
    val userModels: StateFlow<List<ModelInfo>> = _userModels.asStateFlow()

    /** A model from the catalog or one the listener added. */
    fun info(id: String?): ModelInfo? = ModelCatalog.byId(id) ?: _userModels.value.firstOrNull { it.id == id }

    fun all(kind: ModelKind): List<ModelInfo> = ModelCatalog.models.filter { it.kind == kind } + _userModels.value.filter { it.kind == kind }

    private fun loadUserModels(): List<ModelInfo> = runCatching {
        (MiniJson.parse(prefs.getString("list", "[]") ?: "[]") as List<*>).mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            ModelInfo(
                m["id"] as String, ModelKind.valueOf(m["kind"] as String), m["label"] as String, m["description"] as? String ?: "",
                m["url"] as? String, m["fileName"] as String, (m["sizeMb"] as? Number)?.toInt() ?: 0, m["repo"] as String,
            )
        }
    }.getOrDefault(emptyList())

    private fun saveUserModels(list: List<ModelInfo>) {
        _userModels.value = list
        prefs.edit().putString(
            "list",
            MiniJson.write(
                list.map {
                    mapOf(
                        "id" to it.id, "kind" to it.kind.name, "label" to it.label, "description" to it.description, "url" to it.url,
                        "fileName" to it.fileName, "sizeMb" to it.sizeMb, "repo" to it.repo,
                    )
                },
            ),
        ).apply()
        _changes.value++
    }

    /** Adds a file of a Hugging Face repository to the model list (download it with [downloadModel]). */
    fun addUserModel(kind: ModelKind, repo: HfRepo, file: HfFile): ModelInfo {
        val id = HfModels.idFor(repo.id, file.path)
        val quant = HfModels.quantOf(file.fileName)
        val info = ModelInfo(
            id, kind, repo.name.removeSuffix("-GGUF").removeSuffix("-gguf") + (quant?.let { " · $it" } ?: ""),
            "Von Hugging Face: ${repo.id}", HfModels.downloadUrl(repo.id, file.path), HfModels.localName(repo.id, file.path), file.sizeMb,
            repo = repo.id,
        )
        saveUserModels(_userModels.value.filter { it.id != id } + info)
        return info
    }

    /** All memory of the phone in MB (for the "fits" hint of the search). */
    val totalRamMb: Int by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem.let { (it / 1_000_000).toInt() }
    }

    /** Searches Hugging Face (needs the internet). */
    suspend fun searchHub(query: String, kind: ModelKind): List<HfRepo> = withContext(Dispatchers.IO) {
        HfModels.parseSearch(getText(HfModels.searchUrl(query, kind == ModelKind.SPEECH)))
    }

    /** The usable files of a repository, the recommended one first. */
    suspend fun hubFiles(repo: String, kind: ModelKind): List<HfFile> = withContext(Dispatchers.IO) {
        HfModels.parseFiles(getText(HfModels.filesUrl(repo)), kind == ModelKind.SPEECH)
    }

    private fun getText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "Lauscher-Android")
        try {
            val code = conn.responseCode
            if (code != 200) error(if (code == 429) "Hugging Face bremst gerade, bitte gleich noch einmal" else "Hugging Face antwortet mit $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** External app storage keeps the large files out of the internal partition. */
    private val dirs: List<File>
        get() = listOfNotNull(context.getExternalFilesDir("models"), File(context.filesDir, "models"))
            .onEach { it.mkdirs() }

    private val targetDir: File get() = dirs.first()

    fun fileFor(id: String?): File? {
        id ?: return null
        val name = info(id)?.fileName ?: customFileName(id) ?: return null
        return dirs.map { File(it, name) }.firstOrNull { it.isFile && it.length() > 1_000_000 }
    }

    fun isInstalled(id: String?): Boolean = fileFor(id) != null

    fun installedSizeMb(id: String): Int = ((fileFor(id)?.length() ?: 0L) / 1_000_000).toInt()

    private fun customFileName(id: String) = when (id) {
        ModelCatalog.customId(ModelKind.SPEECH) -> "custom-speech.bin"
        ModelCatalog.customId(ModelKind.LANGUAGE) -> "custom-language.gguf"
        else -> null
    }

    fun delete(id: String) {
        val name = info(id)?.fileName ?: customFileName(id) ?: return
        dirs.forEach { File(it, name).delete(); File(it, "$name.part").delete() }
        if (_userModels.value.any { it.id == id }) saveUserModels(_userModels.value.filter { it.id != id })
        _changes.value++
    }

    /** Downloads with resume support; returns true when the file is complete. */
    suspend fun downloadModel(model: ModelInfo): Boolean = withContext(Dispatchers.IO) {
        val url = model.url ?: return@withContext false
        val target = File(targetDir, model.fileName)
        val part = File(targetDir, model.fileName + ".part")
        var redirects = 0
        var current = URL(url)
        try {
            while (true) {
                val have = if (part.exists()) part.length() else 0L
                val conn = current.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Lauscher-Android")
                if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: error("Weiterleitung ohne Ziel")
                    current = URL(current, location)
                    conn.disconnect()
                    if (++redirects > 8) error("Zu viele Weiterleitungen")
                    continue
                }
                if (code == 416) {
                    // Already complete.
                    conn.disconnect()
                    break
                }
                if (code != 200 && code != 206) error("Server antwortet mit $code")
                val append = code == 206
                val total = (if (append) have else 0L) + conn.contentLengthLong.coerceAtLeast(0)
                var done = if (append) have else 0L
                conn.inputStream.use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(256 * 1024)
                        var lastReport = 0L
                        while (coroutineContext.isActive) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - lastReport > 1_000_000) {
                                lastReport = done
                                _download.value = DownloadState(model.id, done, total)
                            }
                        }
                    }
                }
                conn.disconnect()
                if (!coroutineContext.isActive) return@withContext false
                break
            }
            if (!part.renameTo(target)) error("Datei konnte nicht gespeichert werden")
            _download.value = null
            _changes.value++
            true
        } catch (e: Exception) {
            _download.value = DownloadState(model.id, part.length(), 0, e.message ?: e.javaClass.simpleName)
            false
        }
    }

    fun clearError() {
        if (_download.value?.error != null) _download.value = null
    }

    /** Copies a model file the user picked (e.g. any GGUF from Hugging Face) into app storage. */
    suspend fun importModel(kind: ModelKind, uri: Uri): String? = withContext(Dispatchers.IO) {
        val id = ModelCatalog.customId(kind)
        val target = File(targetDir, customFileName(id)!!)
        val resolver = context.contentResolver
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done % (8L * 1024 * 1024) < buf.size) _download.value = DownloadState(id, done, size)
                    }
                }
            } ?: return@withContext null
            _download.value = null
            _changes.value++
            id
        } catch (e: Exception) {
            target.delete()
            _download.value = DownloadState(id, 0, 0, e.message)
            null
        }
    }

    fun setJob(j: Job?) {
        job?.cancel()
        job = j
    }

    fun cancelDownload() {
        job?.cancel()
        job = null
        _download.value = null
    }
}
