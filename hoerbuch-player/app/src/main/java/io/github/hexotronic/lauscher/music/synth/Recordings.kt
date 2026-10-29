package io.github.hexotronic.lauscher.music.synth

/**
 * A recorded piece of music, decoded ahead of time on another thread. [read] is called on the
 * audio thread and must never block.
 */
interface PcmStream {
    /** Library id of the piece (for the "now playing" credit). */
    val trackId: String

    /** True once the whole piece has been delivered. */
    val finished: Boolean

    /**
     * Writes up to [frames] stereo frames into [left]/[right] and returns how many were written.
     * Fewer frames mean the decoder is behind (or the piece has ended).
     */
    fun read(left: FloatArray, right: FloatArray, frames: Int): Int

    fun close()
}

/** Supplies recorded pieces for a mood, e.g. the downloaded music library. */
interface RecordingSource {
    /**
     * Opens a piece for [mood]: [trackId] if given, otherwise one picked for the mood and
     * [intensity] (0..1). Returns null when no piece is available, the synthesizer plays then.
     * Called on the audio thread, so it must only start work, not wait for it.
     */
    fun open(mood: Mood, intensity: Float, trackId: String?): PcmStream?
}

/**
 * Plays recorded pieces of one mood one after another, with a short pause in between.
 * Pieces already end musically, so they are not cut or looped.
 */
internal class RecordingGenerator(
    private val sampleRate: Int,
    private val source: RecordingSource,
    val mood: Mood,
    private val intensity: Float,
    first: PcmStream,
) : Generator {
    override val reverb = 0.05f
    private var stream: PcmStream? = first
    private var pauseLeft = 0
    private var started = false

    /** The piece that is currently audible, null during the pause between pieces. */
    val trackId: String? get() = stream?.trackId

    /** True once a piece has delivered audio; until then the decoder is still starting. */
    val hasStarted: Boolean get() = started

    override fun render(left: FloatArray, right: FloatArray, frames: Int) {
        var done = 0
        while (done < frames) {
            val s = stream
            if (s == null) {
                val skip = minOf(pauseLeft, frames - done)
                pauseLeft -= skip
                done += skip
                if (pauseLeft <= 0) {
                    stream = source.open(mood, intensity, null)
                    // Nothing else to play: stay silent but try again after the next pause.
                    if (stream == null) pauseLeft = (PAUSE_SECONDS * sampleRate).toInt()
                }
                continue
            }
            val n = readInto(s, left, right, done, frames - done)
            if (n > 0) started = true
            done += n
            if (s.finished) {
                s.close()
                stream = null
                pauseLeft = (PAUSE_SECONDS * sampleRate).toInt()
            } else if (n == 0) {
                break // decoder is behind: leave the rest of this block silent
            }
        }
    }

    private val tmpL = FloatArray(MusicMixer.MAX_BLOCK)
    private val tmpR = FloatArray(MusicMixer.MAX_BLOCK)

    private fun readInto(s: PcmStream, left: FloatArray, right: FloatArray, offset: Int, frames: Int): Int {
        val want = minOf(frames, tmpL.size)
        val n = s.read(tmpL, tmpR, want)
        for (i in 0 until n) {
            left[offset + i] += tmpL[i]
            right[offset + i] += tmpR[i]
        }
        return n
    }

    override fun close() {
        stream?.close()
        stream = null
    }

    companion object {
        const val PAUSE_SECONDS = 2.5f
    }
}
