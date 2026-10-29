package io.github.hexotronic.lauscher.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import io.github.hexotronic.lauscher.audio.AudioEnhancerHub
import io.github.hexotronic.lauscher.music.synth.MusicMixer
import io.github.hexotronic.lauscher.music.synth.RecordingSource
import io.github.hexotronic.lauscher.music.synth.SoundSpec

/**
 * Plays the background music (synthesizer or downloaded pieces) on its own AudioTrack, next
 * to the audiobook. Starts and stops with the narration (with a short fade) and ducks under speech.
 */
class MusicEngine(recordings: RecordingSource? = null) {
    private val mixer = MusicMixer(SAMPLE_RATE).apply { this.recordings = recordings }
    private var thread: Thread? = null

    @Volatile
    private var wantPlaying = false

    @Volatile
    private var running = false

    @Volatile
    var currentSpec: SoundSpec? = null
        private set

    fun apply(settings: MusicSettings, volumeScale: Float) {
        mixer.volume = settings.volume * volumeScale.coerceIn(0f, 1f)
        mixer.duckDepthDb = settings.duckDepthDb
        mixer.crossfadeSeconds = settings.crossfadeSeconds
    }

    fun setSpec(spec: SoundSpec?) {
        if (spec == currentSpec) return
        currentSpec = spec
        mixer.setTarget(spec)
    }

    fun chime() = mixer.playChime()

    /** Library id of the downloaded piece that is playing, if any. */
    val currentTrackId: String? get() = mixer.currentTrackId

    /** Chooses the music for the current scene again (after the music source changed). */
    fun refresh() = mixer.refresh()

    @Synchronized
    fun setPlaying(playing: Boolean) {
        wantPlaying = playing
        if (playing && !running) start()
    }

    private fun start() {
        running = true
        thread = Thread({ loop() }, "LauscherMusic").apply { start() }
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE * 4 / 5))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }
        val floats = FloatArray(BLOCK * 2)
        val shorts = ShortArray(BLOCK * 2)
        var gain = 0f
        val step = 1f / (FADE_SECONDS * SAMPLE_RATE / BLOCK)
        try {
            track.play()
            while (true) {
                val target = if (wantPlaying) 1f else 0f
                gain = if (gain < target) minOf(target, gain + step) else maxOf(target, gain - step)
                if (!wantPlaying && gain <= 0f) break
                mixer.speechLevelDb = AudioEnhancerHub.speechLevelDb
                mixer.render(floats, BLOCK)
                for (i in 0 until BLOCK * 2) {
                    shorts[i] = (floats[i] * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(shorts, 0, shorts.size)
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(this) {
                running = false
                // Playback may have been requested again while we were fading out.
                if (wantPlaying) start()
            }
        }
    }

    fun release() {
        wantPlaying = false
        mixer.closeAll()
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val BLOCK = 1024
        private const val FADE_SECONDS = 0.6f
    }
}
