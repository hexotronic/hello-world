package io.github.hexotronic.lauscher.download.core

/**
 * Expected remaining time of a video or file job. The items go through two stages like on an
 * assembly line: the network fetches one item after the other, and the encoders convert each
 * item once it is there (while the network already fetches the next). Each stage works at the
 * speed measured on this device ([Rates]), so the estimate fits from the first second instead of
 * being guessed from how much of the job is done.
 */
object WorkEstimate {
    /** What is left of one item, in the order the items are worked on. */
    data class Item(
        /** Bytes still to download (0: nothing to download). */
        val bytesLeft: Long,
        /** Sound still to convert to MP3, in ms (0: not converted). */
        val audioLeftMs: Long,
        /** Fixed time before the download, e.g. looking up the video's stream. */
        val overheadMs: Long = 0,
    )

    /** Remaining ms for [items] at [bytesPerSecond] over the network and [audioPerSecond] (ms of sound converted per ms). */
    fun remainingMs(items: List<Item>, bytesPerSecond: Double, audioPerSecond: Double): Long {
        var fetched = 0.0
        var converted = 0.0
        for (item in items) {
            fetched += item.overheadMs + item.bytesLeft * 1000.0 / bytesPerSecond
            converted = maxOf(converted, fetched) + item.audioLeftMs / audioPerSecond
        }
        return maxOf(fetched, converted).toLong()
    }
}

/**
 * Speeds measured on this device, kept between app starts (smoothed, so one odd value does not
 * spoil them). [load] and [save] connect them to the app's settings.
 */
class Rates(private val load: (String) -> Float?, private val save: (String, Float) -> Unit) {
    /**
     * Ms of sound converted to MP3 per ms when recordings are encoded in pieces on several cores,
     * with LAME quality [lameQuality] (until measured: 50 for the normal setting, the CI emulator
     * does about 65 with three encoders; the fast setting is about 1.5× as fast).
     */
    fun convert(lameQuality: Int = 5): Double = value(convertKey(CONVERT, lameQuality), 50f * speedUp(lameQuality))

    /** The same when a recording is encoded in one go (rates other than 32/44.1/48 kHz). */
    fun convertSingle(lameQuality: Int = 5): Double = value(convertKey(CONVERT_SINGLE, lameQuality), 20f * speedUp(lameQuality))

    /** Network speed in bytes per second. */
    val network: Double get() = value(NETWORK, 1_000_000f)

    /** Bytes downloaded per ms of sound (about 20 for the usual 160 kbit/s audio streams). */
    val bytesPerAudioMs: Double get() = value(BYTES_PER_AUDIO, 20f)

    /** A conversion of [audioMs] of sound took [wallMs]; short ones say too little to count. */
    fun converted(audioMs: Long, wallMs: Long, inPieces: Boolean, lameQuality: Int = 5) {
        if (audioMs < 30_000 || wallMs <= 0) return
        learn(convertKey(if (inPieces) CONVERT else CONVERT_SINGLE, lameQuality), audioMs.toDouble() / wallMs)
    }

    /** Speeds are kept per LAME setting; the normal one keeps the original key. */
    private fun convertKey(base: String, lameQuality: Int) = if (lameQuality == 5) base else "$base-q$lameQuality"

    private fun speedUp(lameQuality: Int) = if (lameQuality >= 7) 1.5f else 1f

    /** [bytes] arrived in [wallMs]; [audioMs] is the length of the sound if known. */
    fun downloaded(bytes: Long, wallMs: Long, audioMs: Long) {
        if (bytes < 500_000 || wallMs <= 0) return
        learn(NETWORK, bytes * 1000.0 / wallMs)
        if (audioMs > 30_000) learn(BYTES_PER_AUDIO, bytes.toDouble() / audioMs)
    }

    private fun value(key: String, default: Float): Double = (load(key)?.takeIf { it > 0f } ?: default).toDouble()

    private fun learn(key: String, measured: Double) {
        val old = load(key)?.takeIf { it > 0f }?.toDouble()
        save(key, (if (old == null) measured else old + (measured - old) * WEIGHT).toFloat())
    }

    private companion object {
        const val CONVERT = "convert"
        const val CONVERT_SINGLE = "convertSingle"
        const val NETWORK = "network"
        const val BYTES_PER_AUDIO = "bytesPerAudioMs"

        /** How much a new measurement counts against what was learned before. */
        const val WEIGHT = 0.5
    }
}
