package io.github.hexotronic.lauscher.scan

import android.content.Context
import android.net.Uri
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.analysis.core.WindowMood
import io.github.hexotronic.lauscher.data.Chapter
import io.github.hexotronic.lauscher.data.LibraryRepository
import io.github.hexotronic.lauscher.data.Track
import io.github.hexotronic.lauscher.music.synth.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a small demo audiobook with a synthetic voice and typical noise problems
 * (hiss, mains hum, crackle), so the Klangstudio can be tried without own files.
 */
object DemoBook {
    const val KEY = "demo:lauscher"
    private const val RATE = 22_050
    private const val SECONDS = 40

    private val chapters = listOf(
        "Kapitel 1 – Bandrauschen",
        "Kapitel 2 – Netzbrummen",
        "Kapitel 3 – Knistern",
    )

    suspend fun create(context: Context, repo: LibraryRepository): Long = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "demo").apply { mkdirs() }
        val tracks = ArrayList<Track>()
        val marks = ArrayList<Chapter>()
        var start = 0L
        chapters.forEachIndexed { i, title ->
            val file = File(dir, "kapitel_${i + 1}.wav")
            if (!file.exists()) {
                val tmp = File(dir, "kapitel_${i + 1}.tmp")
                writeWav(tmp, synthesize(i))
                tmp.renameTo(file)
            }
            val duration = SECONDS * 1000L
            tracks.add(Track(0, i, Uri.fromFile(file).toString(), title, duration, start))
            marks.add(Chapter(0, i, title, start, start + duration))
            start += duration
        }
        val id = repo.saveScannedBook(
            sourceKey = KEY,
            rootUri = "demo",
            title = "Lauscher-Demo: Rauschtest",
            author = "Lauscher",
            narrator = "Robo-Sprecher",
            year = null,
            description = "Eine künstliche Stimme mit Bandrauschen, Netzbrummen und Knistern. " +
                "Öffne das Klangstudio und vergleiche mit der A/B-Taste.",
            coverPath = null,
            signature = "demo-v3",
            tracks = tracks,
            chapters = marks,
        )
        // A ready-made analysis, so background music and search work without downloading models.
        val story = listOf(
            Triple("Am Morgen saß Mira am See und trank Tee. Die Sonne wärmte das Wasser, Vögel sangen im Schilf.", Mood.IDYLLIC, "Morgen am See"),
            Triple("Am Abend hörte sie Schritte im Dunkeln. Ein Schatten folgte ihr bis zum alten Bootshaus.", Mood.TENSE, "Schritte im Dunkeln"),
            Triple("Doch es war nur ihr Bruder. Lachend feierten sie zusammen das Sommerfest bis tief in die Nacht.", Mood.HAPPY, "Das Sommerfest"),
        )
        story.forEachIndexed { i, (text, mood, title) ->
            val window = TranscriptWindow(i * SECONDS * 1000L, (i + 1) * SECONDS * 1000L, text)
            repo.saveTranscript(id, window)
            repo.saveWindowMoods(id, listOf(WindowMood(window.startMs, window.endMs, mood, 2, title, SceneMerger.SOURCE_LLM)))
        }
        repo.saveStory(id, "Mira verbringt einen Sommertag am See, fürchtet sich abends vor einem Verfolger und feiert dann mit ihrem Bruder.")
        id
    }

    private fun synthesize(kind: Int): ShortArray {
        val n = RATE * SECONDS
        val out = ShortArray(n)
        val rnd = Random(kind + 1)
        val table = FloatArray(4096) { sin(2 * PI * it / 4096).toFloat() }
        var phase = 0.0
        var crackle = 0f
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            // Phrases of syllables with pauses, a gently moving pitch and three formants.
            val inPhrase = (t % 3.1) < 2.3
            val syllablePos = t % 0.27
            var voice = 0f
            if (inPhrase && syllablePos < 0.19) {
                val env = sin(PI * syllablePos / 0.19).toFloat()
                val f0 = 125 + 30 * sin(2 * PI * 0.45 * t) + 12 * sin(2 * PI * 3.1 * t)
                phase += f0 / RATE
                for (h in 1..14) {
                    val f = f0 * h
                    val formant = 1.0 / (1 + ((f - 650) / 260).let { it * it }) +
                        0.7 / (1 + ((f - 1700) / 380).let { it * it }) +
                        0.35 / (1 + ((f - 2700) / 480).let { it * it })
                    val idx = ((phase * h % 1.0) * 4096).toInt() and 4095
                    voice += (formant * table[idx] / h).toFloat()
                }
                voice *= 0.32f * env
            }
            val hiss = (rnd.nextFloat() * 2 - 1)
            val noise = when (kind) {
                0 -> 0.045f * hiss
                1 -> 0.012f * hiss + 0.05f * (
                    table[((50 * t % 1.0) * 4096).toInt() and 4095] +
                        0.5f * table[((150 * t % 1.0) * 4096).toInt() and 4095] +
                        0.25f * table[((250 * t % 1.0) * 4096).toInt() and 4095]
                    )
                else -> {
                    if (rnd.nextFloat() < 0.0009f) crackle = (rnd.nextFloat() * 2 - 1) * 0.6f
                    crackle *= 0.55f
                    0.015f * hiss + crackle
                }
            }
            out[i] = ((voice + noise).coerceIn(-1f, 1f) * 32000).toInt().toShort()
        }
        return out
    }

    private fun writeWav(file: File, samples: ShortArray) {
        val dataBytes = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + dataBytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(RATE)
            putInt(RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataBytes)
        }
        val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        body.asShortBuffer().put(samples)
        BufferedOutputStream(FileOutputStream(file)).use {
            it.write(header.array())
            it.write(body.array())
        }
    }
}
