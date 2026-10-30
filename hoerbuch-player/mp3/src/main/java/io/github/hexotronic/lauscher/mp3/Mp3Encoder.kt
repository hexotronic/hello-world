package io.github.hexotronic.lauscher.mp3

import java.io.Closeable

/**
 * Constant-bitrate MP3 encoder (LAME). Feed interleaved 16-bit PCM with [encode], then call
 * [flush] once for the last frames. Not thread-safe.
 */
class Mp3Encoder private constructor(private var handle: Long, private val channels: Int) : Closeable {

    /** Encodes [frames] frames of interleaved samples and returns the MP3 bytes produced so far. */
    fun encode(pcm: ShortArray, frames: Int = pcm.size / channels): ByteArray = nativeEncode(handle, pcm, frames)

    fun flush(): ByteArray = nativeFlush(handle)

    override fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    companion object {
        init {
            System.loadLibrary("lauscher_mp3")
        }

        /**
         * [sampleRate] and [channels] (1 or 2) describe the input. With [mono] the output is
         * downmixed; [outRate] 0 lets LAME pick a rate that suits [kbps].
         */
        fun create(sampleRate: Int, channels: Int, kbps: Int, mono: Boolean, outRate: Int = 0): Mp3Encoder? {
            val h = nativeInit(sampleRate, channels, outRate, kbps, mono)
            return if (h == 0L) null else Mp3Encoder(h, channels)
        }

        @JvmStatic private external fun nativeInit(sampleRate: Int, channels: Int, outRate: Int, kbps: Int, mono: Boolean): Long
        @JvmStatic private external fun nativeEncode(handle: Long, pcm: ShortArray, frames: Int): ByteArray
        @JvmStatic private external fun nativeFlush(handle: Long): ByteArray
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}
