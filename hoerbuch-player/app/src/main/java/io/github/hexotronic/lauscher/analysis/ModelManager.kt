package io.github.hexotronic.lauscher.analysis

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

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
)

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
        ModelInfo(
            "qwen2.5-1.5b", ModelKind.LANGUAGE, "Qwen2.5 1.5B Instruct",
            "Empfohlen: versteht Deutsch gut und läuft auf den meisten Handys (ab 4 GB RAM)",
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            "qwen2.5-1.5b-instruct-q4_k_m.gguf", 1120,
        ),
        ModelInfo(
            "qwen2.5-0.5b", ModelKind.LANGUAGE, "Qwen2.5 0.5B Instruct",
            "Klein und schnell, die Szenenwahl ist aber grober",
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            "qwen2.5-0.5b-instruct-q4_k_m.gguf", 400,
        ),
        ModelInfo(
            "qwen2.5-3b", ModelKind.LANGUAGE, "Qwen2.5 3B Instruct",
            "Beste Ergebnisse, für starke Handys (ab 8 GB RAM)",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            "qwen2.5-3b-instruct-q4_k_m.gguf", 2100,
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

    /** External app storage keeps the large files out of the internal partition. */
    private val dirs: List<File>
        get() = listOfNotNull(context.getExternalFilesDir("models"), File(context.filesDir, "models"))
            .onEach { it.mkdirs() }

    private val targetDir: File get() = dirs.first()

    fun fileFor(id: String?): File? {
        id ?: return null
        val name = ModelCatalog.byId(id)?.fileName ?: customFileName(id) ?: return null
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
        val name = ModelCatalog.byId(id)?.fileName ?: customFileName(id) ?: return
        dirs.forEach { File(it, name).delete(); File(it, "$name.part").delete() }
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
