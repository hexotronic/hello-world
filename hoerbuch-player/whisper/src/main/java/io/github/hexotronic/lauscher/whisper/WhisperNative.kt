package io.github.hexotronic.lauscher.whisper

import java.io.Closeable

/** Offline speech recognition with a whisper.cpp model (ggml *.bin file). Not thread-safe. */
class WhisperNative private constructor(private var handle: Long) : Closeable {

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    /**
     * Transcribes 16 kHz mono samples in the range -1..1. [prompt] is text the speech is expected
     * to resemble (e.g. the voice commands), which steers the recogniser towards those words.
     * [maxTokens] > 0 caps the length of the text (for short commands), [beam] > 1 searches more
     * carefully with that many candidates (slower).
     */
    fun transcribe(
        samples: FloatArray,
        language: String = "de",
        threads: Int = defaultThreads(),
        prompt: String? = null,
        maxTokens: Int = 0,
        beam: Int = 0,
    ): List<Segment> {
        val raw = String(nativeTranscribe(handle, samples, language, threads, prompt, maxTokens, beam), Charsets.UTF_8)
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val text = parts[2].trim()
            if (text.isEmpty()) null else Segment(parts[0].toLongOrNull() ?: 0, parts[1].toLongOrNull() ?: 0, text)
        }.toList()
    }

    override fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** "schnell" or "standard", see [NativeCpu]. */
        val variant: String = NativeCpu.load("lauscher_whisper")

        fun load(path: String): WhisperNative? {
            val h = nativeInit(path)
            return if (h == 0L) null else WhisperNative(h)
        }

        fun defaultThreads(): Int = NativeCpu.performanceCores

        @JvmStatic private external fun nativeInit(path: String): Long
        @JvmStatic private external fun nativeTranscribe(
            handle: Long,
            samples: FloatArray,
            language: String,
            threads: Int,
            prompt: String?,
            maxTokens: Int,
            beam: Int,
        ): ByteArray
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}
