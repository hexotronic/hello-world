package io.github.hexotronic.lauscher.llm

import java.io.Closeable

/**
 * A local large language model (any GGUF file) running on the phone's CPU via llama.cpp.
 * Not thread-safe: use one instance from one thread at a time.
 */
class LlamaNative private constructor(private var handle: Long) : Closeable {

    val contextSize: Int get() = nativeContextSize(handle)

    /** Formats a system and user message with the model's chat template. */
    fun chatPrompt(system: String, user: String): String =
        String(nativeChatPrompt(handle, system.toByteArray(), user.toByteArray()), Charsets.UTF_8)

    fun countTokens(text: String): Int = nativeCountTokens(handle, text.toByteArray())

    /**
     * Generates text. With a GBNF [grammar] the output always matches it.
     * Returns null on errors (e.g. prompt too long).
     */
    fun generate(prompt: String, grammar: String? = null, maxTokens: Int = 512, temperature: Float = 0.2f, seed: Int = 42): String? {
        val bytes = nativeGenerate(handle, prompt.toByteArray(), grammar, maxTokens, temperature, seed)
        return if (bytes.isEmpty()) null else String(bytes, Charsets.UTF_8)
    }

    /** Stops a running [generate] from another thread. */
    fun cancel() = nativeCancel(handle)

    override fun close() {
        if (handle != 0L) {
            nativeFree(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("lauscher_llm")
        }

        /** Loads a GGUF model; returns null if the file is not a usable model. */
        fun load(path: String, contextSize: Int = 4096, threads: Int = defaultThreads()): LlamaNative? {
            val h = nativeLoad(path, contextSize, threads)
            return if (h == 0L) null else LlamaNative(h)
        }

        fun defaultThreads(): Int = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)

        @JvmStatic private external fun nativeLoad(path: String, nCtx: Int, nThreads: Int): Long
        @JvmStatic private external fun nativeChatPrompt(handle: Long, system: ByteArray, user: ByteArray): ByteArray
        @JvmStatic private external fun nativeCountTokens(handle: Long, text: ByteArray): Int
        @JvmStatic private external fun nativeContextSize(handle: Long): Int
        @JvmStatic private external fun nativeGenerate(
            handle: Long,
            prompt: ByteArray,
            grammar: String?,
            maxTokens: Int,
            temperature: Float,
            seed: Int,
        ): ByteArray
        @JvmStatic private external fun nativeCancel(handle: Long)
        @JvmStatic private external fun nativeFree(handle: Long)
    }
}
