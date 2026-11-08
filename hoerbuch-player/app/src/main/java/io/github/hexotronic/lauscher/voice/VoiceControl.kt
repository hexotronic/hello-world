package io.github.hexotronic.lauscher.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.analysis.AudioSegmentDecoder
import io.github.hexotronic.lauscher.analysis.ModelCatalog
import io.github.hexotronic.lauscher.analysis.ModelKind
import io.github.hexotronic.lauscher.playback.PlaybackHub
import io.github.hexotronic.lauscher.voice.core.CommandMatcher
import io.github.hexotronic.lauscher.voice.core.Recognized
import io.github.hexotronic.lauscher.voice.core.SpeechDetector
import io.github.hexotronic.lauscher.voice.core.VoiceAction
import io.github.hexotronic.lauscher.whisper.WhisperNative
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/** What the voice control is doing, for the settings screen. */
sealed interface VoiceStatus {
    data object Off : VoiceStatus
    data class Waiting(val reason: String) : VoiceStatus
    data class Listening(val microphone: String) : VoiceStatus
}

/**
 * Hands-free control while the phone is in a pocket: listens on the microphone (in
 * [VoiceControlService]), cuts out short utterances ([SpeechDetector]), lets the offline speech
 * recogniser (whisper, the model of the scene analysis) write them down with the commands as a
 * hint, finds the command ([CommandMatcher]) and carries it out on the player. Nothing leaves
 * the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceControl(private val app: LauscherApp) {
    val store = VoiceSettingsStore(app)
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Off)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()
    private val _lastHeard = MutableStateFlow<String?>(null)

    /** The last thing heard and what came of it ("„Lauscher, Pause“ → Pause"). */
    val lastHeard: StateFlow<String?> = _lastHeard.asStateFlow()

    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    /** Set by the activity: Android lets the microphone start only while the app is on screen. */
    @Volatile
    var appVisible = false

    @Volatile
    private var idleSince = SystemClock.elapsedRealtime()

    /** True while [run] is listening in the service (it picks up changed settings by itself). */
    @Volatile
    private var running = false

    init {
        // Pressing play inside the app also starts listening.
        app.appScope.launch {
            PlaybackHub.core.flatMapLatest { it?.state ?: flowOf(null) }
                .map { it?.isPlaying == true }
                .distinctUntilChanged()
                .collect { playing -> if (playing && appVisible) ensureRunning() }
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** A speech recognition model is installed (the one of the scene analysis, or any other). */
    fun hasModel(): Boolean = modelFile() != null

    /** Starts listening if voice control is on and a book is loaded; call while the app is visible. */
    fun ensureRunning() {
        if (!store.current.enabled || running) return
        if (!hasPermission()) {
            _status.value = VoiceStatus.Waiting("Die Erlaubnis für das Mikrofon fehlt")
            return
        }
        if (!hasModel()) {
            _status.value = VoiceStatus.Waiting("Kein Spracherkennungsmodell – unter „KI-Modelle“ eins laden (Whisper Base)")
            return
        }
        if (PlaybackHub.core.value?.state?.value?.book == null) {
            _status.value = VoiceStatus.Waiting("Startet, sobald ein Hörbuch läuft")
            return
        }
        idleSince = SystemClock.elapsedRealtime()
        VoiceControlService.start(app)
    }

    fun setEnabled(on: Boolean) {
        store.update { it.copy(enabled = on) }
        if (on) {
            ensureRunning()
        } else {
            VoiceControlService.stop(app)
            _status.value = VoiceStatus.Off
        }
    }

    // ------------------------------------------------------------------ listening (in the service)

    /** Listens until voice control is switched off or nothing played for a long time. */
    suspend fun run() = withContext(Dispatchers.IO) {
        if (running) return@withContext
        running = true
        try {
            listenWhileWanted()
        } finally {
            running = false
        }
    }

    private suspend fun listenWhileWanted() = withContext(Dispatchers.IO) {
        val whisper = loadWhisper()
        if (whisper == null) {
            _status.value = VoiceStatus.Waiting("Kein Spracherkennungsmodell – unter „KI-Modelle“ eins laden (Whisper Base)")
            return@withContext
        }
        val recognizer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            while (isActive) {
                val s = store.current
                if (!s.enabled || !hasPermission()) break
                if (idleTooLong(s)) {
                    Log.i(TAG, "Sprachsteuerung pausiert: seit ${s.idleMinutes} Minuten nichts gehört")
                    break
                }
                when {
                    PlaybackHub.core.value?.state?.value?.book == null -> waiting("Wartet, bis ein Hörbuch geladen ist")
                    s.onlyWithHeadphones && !headphonesConnected() -> waiting("Wartet auf Kopfhörer")
                    else -> {
                        listen(whisper, recognizer, s)
                        continue
                    }
                }
                delay(2_000)
            }
        } finally {
            recognizer.close()
            whisper.close()
            _status.value = VoiceStatus.Off
            main.post { PlaybackHub.core.value?.duckForVoice(false) }
        }
    }

    private fun waiting(reason: String) {
        if ((_status.value as? VoiceStatus.Waiting)?.reason != reason) Log.i(TAG, "Sprachsteuerung: $reason")
        _status.value = VoiceStatus.Waiting(reason)
    }

    private fun idleTooLong(s: VoiceSettings): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (PlaybackHub.core.value?.state?.value?.isPlaying == true) idleSince = now
        return now - idleSince > s.idleMinutes * 60_000L
    }

    /** One stretch of listening with settings [s]; returns when something changes (settings, headphones, book). */
    private suspend fun listen(whisper: WhisperNative, recognizer: CoroutineDispatcher, s: VoiceSettings) = coroutineScope {
        val mic = openMicrophone(s)
        if (mic == null) {
            waiting("Das Mikrofon ist gerade nicht verfügbar")
            delay(5_000)
            return@coroutineScope
        }
        // One utterance is recognised at a time; while busy, newer ones are dropped.
        val utterances = Channel<FloatArray>(1, BufferOverflow.DROP_LATEST)
        val worker = launch(recognizer) { for (u in utterances) handle(whisper, u, s) }
        val detector = SpeechDetector(
            RATE,
            s.sensitivity,
            onStart = { if (s.duckWhileSpeaking) duck(true) },
            onEnd = { accepted -> if (!accepted) duck(false) },
        )
        try {
            mic.recorder.startRecording()
            _status.value = VoiceStatus.Listening(mic.description)
            Log.i(TAG, "Sprachsteuerung hört zu: ${mic.description}")
            val shorts = ShortArray(RATE / 10)
            val floats = FloatArray(shorts.size)
            var checkedAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val n = mic.recorder.read(shorts, 0, shorts.size)
                if (n < 0) {
                    Log.w(TAG, "Mikrofon liefert nichts mehr ($n)")
                    break
                }
                for (i in 0 until n) floats[i] = shorts[i] / 32768f
                for (u in detector.feed(floats, n)) {
                    if (utterances.trySend(u).isFailure) duck(false)
                }
                val now = SystemClock.elapsedRealtime()
                if (now - checkedAt > 1_000) {
                    checkedAt = now
                    val book = PlaybackHub.core.value?.state?.value?.book
                    if (store.current != s || book == null || idleTooLong(s) || (s.onlyWithHeadphones && !headphonesConnected())) break
                }
            }
        } finally {
            utterances.close()
            worker.cancel()
            mic.close()
            duck(false)
        }
    }

    private suspend fun handle(whisper: WhisperNative, samples: FloatArray, s: VoiceSettings) {
        val started = SystemClock.elapsedRealtime()
        val hint = s.commands.recognizerHint()
        fun recognize(beam: Int) = runCatching {
            whisper.transcribe(samples, s.language, threads = 2, prompt = hint, maxTokens = MAX_TOKENS, beam = beam)
        }.getOrDefault(emptyList()).joinToString(" ") { it.text }.let { CommandMatcher.collapseRepeats(it) }
        val quick = recognize(beam = 0)
        var text = quick
        var recognized = CommandMatcher.match(quick, s.commands)
        // The wake word was there, the command was not understood: listen again, more carefully.
        if (recognized == null && CommandMatcher.worthASecondTry(quick, s.commands)) {
            val careful = recognize(beam = BEAM)
            CommandMatcher.match(careful, s.commands)?.let { recognized = it; text = careful }
        }
        val result = recognized
        Log.i(
            TAG,
            "Gehört (${samples.size * 1000L / RATE} ms Ton, erkannt in ${SystemClock.elapsedRealtime() - started} ms): " +
                "„$text“" + (if (text != quick) " (erst „$quick“)" else "") +
                " → ${result?.let { "${it.action}" + (it.seconds?.let { n -> " $n" } ?: "") } ?: "kein Befehl"}",
        )
        withContext(Dispatchers.Main) {
            PlaybackHub.core.value?.duckForVoice(false)
            if (result != null) perform(result, s)
        }
        _lastHeard.value = when {
            result != null -> "„$text“ → ${result.action.label}"
            text.isNotEmpty() -> "„$text“ – kein Befehl"
            else -> _lastHeard.value
        }
    }

    /** Carries out [r] on the player (main thread). */
    private fun perform(r: Recognized, s: VoiceSettings) {
        val core = PlaybackHub.core.value ?: return
        val state = core.state.value
        val said = when (r.action) {
            VoiceAction.PLAY -> core.play().let { "Weiter" }
            VoiceAction.PAUSE -> core.pause().let { "Pause" }
            VoiceAction.REWIND -> r.seconds?.let {
                core.seekBy(-it * 1000L)
                "$it Sekunden zurück"
            } ?: core.rewind().let { "Zurück" }
            VoiceAction.FORWARD -> r.seconds?.let {
                core.seekBy(it * 1000L)
                "$it Sekunden vor"
            } ?: core.forward().let { "Vor" }
            VoiceAction.NEXT_CHAPTER -> core.nextChapter().let { "Nächstes Kapitel" }
            VoiceAction.PREVIOUS_CHAPTER -> core.previousChapter().let { "Voriges Kapitel" }
            VoiceAction.LOUDER -> repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0) }.let { "Lauter" }
            VoiceAction.QUIETER -> repeat(2) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0) }.let { "Leiser" }
            VoiceAction.FASTER -> core.setSpeed(state.speed + 0.1f).let { "Schneller" }
            VoiceAction.SLOWER -> core.setSpeed(state.speed - 0.1f).let { "Langsamer" }
            VoiceAction.SLEEP_TIMER -> {
                // "Schlaftimer 20 Minuten" or just "Schlaftimer 20": minutes; without a number the usual length.
                val minutes = r.seconds?.let { if (CommandMatcher.hasUnit(r.heard)) it / 60 else it }?.coerceIn(1, 240)
                    ?: app.settings.current.sleepDefaultMinutes
                core.startSleepTimer(minutes)
                "Schlaf-Timer $minutes Minuten"
            }
            VoiceAction.SLEEP_OFF -> core.cancelSleepTimer().let { "Schlaf-Timer aus" }
            VoiceAction.BOOKMARK -> {
                app.appScope.launch { core.addBookmark("Sprachbefehl") }
                "Lesezeichen gesetzt"
            }
            VoiceAction.WHERE -> core.describePosition() ?: "Kein Hörbuch"
            VoiceAction.UNDO -> core.undoJump().let { "Rückgängig" }
        }
        Log.i(TAG, "Ausgeführt: ${r.action} – $said (spielt: ${core.state.value.playWhenReady}, Position ${core.currentPosition() / 1000} s)")
        when {
            // "Wo bin ich?" is answered aloud whatever the feedback setting.
            r.action == VoiceAction.WHERE -> speak(said)
            s.feedback == VoiceFeedback.SPEECH -> speak(said)
            s.feedback == VoiceFeedback.TONE -> tone()
            else -> Unit
        }
    }

    private fun duck(on: Boolean) = main.post { PlaybackHub.core.value?.duckForVoice(on) }

    private fun tone() {
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            main.postDelayed({ generator.release() }, 500)
        }
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice")
            return
        }
        pendingSpeech = text
        if (engine == null) {
            tts = TextToSpeech(app) { result ->
                ttsReady = result == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.GERMAN
                    pendingSpeech?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "voice") }
                }
                pendingSpeech = null
            }
        }
    }

    // ------------------------------------------------------------------ microphone

    private class Microphone(val recorder: AudioRecord, val effects: List<AudioEffect>, val description: String, val onClose: () -> Unit) {
        fun close() {
            runCatching { recorder.stop() }
            recorder.release()
            effects.forEach { it.release() }
            onClose()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openMicrophone(s: VoiceSettings): Microphone? {
        if (!hasPermission()) return null
        val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val wired = inputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
        val builtin = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        var onClose: () -> Unit = {}
        val device = when (s.input) {
            VoiceInput.AUTO -> wired ?: builtin
            VoiceInput.PHONE -> builtin
            VoiceInput.HEADSET -> wired ?: builtin
            VoiceInput.BLUETOOTH -> startBluetooth()?.also { onClose = ::stopBluetooth } ?: builtin
        }
        // With echo cancellation the book is filtered out of the signal when it plays over the loudspeaker.
        val source = if (s.echoCancel) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = runCatching {
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, RATE * 2))
        }.getOrNull()
        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            onClose()
            return null
        }
        device?.let { recorder.setPreferredDevice(it) }
        val effects = ArrayList<AudioEffect>()
        if (s.echoCancel && AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.let {
                it.enabled = true
                effects += it
            }
        }
        val name = when (device?.type) {
            null -> "Standard-Mikrofon"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Handy-Mikrofon"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth-Headset (${device.productName})"
            else -> "Headset (${device.productName})"
        } + if (s.echoCancel) ", mit Echo-Unterdrückung" else ""
        return Microphone(recorder, effects, name, onClose)
    }

    /** Switches the Bluetooth headset to its microphone (hands-free mode); null if there is none. */
    private fun startBluetooth(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sco = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } ?: return null
            if (!audio.setCommunicationDevice(sco)) return null
        } else {
            @Suppress("DEPRECATION")
            audio.startBluetoothSco()
            @Suppress("DEPRECATION")
            audio.isBluetoothScoOn = true
        }
        return audio.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    }

    private fun stopBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audio.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audio.isBluetoothScoOn = false
        }
    }

    fun headphonesConnected(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONES }

    // ------------------------------------------------------------------ model

    private fun modelFile(): File? {
        val models = app.models
        return models.fileFor(app.musicSettings.current.whisperModel)
            ?: listOf("whisper-base", "whisper-tiny", "whisper-small").firstNotNullOfOrNull { models.fileFor(it) }
            ?: models.fileFor(ModelCatalog.customId(ModelKind.SPEECH))
    }

    private fun loadWhisper(): WhisperNative? = modelFile()?.let { WhisperNative.load(it.absolutePath) }

    /**
     * Test aid (adb extra "voice_test_file"): sends the sound in [path] through the same chain as
     * the microphone (speech detector, recogniser, commands, player) and logs every step.
     */
    fun testFile(path: String) {
        app.appScope.launch(Dispatchers.IO) {
            val whisper = loadWhisper()
            if (whisper == null) {
                Log.w(TAG, "Sprachbefehl-Test: kein Spracherkennungsmodell")
                return@launch
            }
            try {
                val samples = AudioSegmentDecoder.decode(app, Uri.fromFile(File(path)), 0, 10 * 60_000L)
                val detector = SpeechDetector(RATE, store.current.sensitivity)
                val found = ArrayList<FloatArray>()
                var at = 0
                while (at < samples.size) {
                    val n = minOf(RATE / 10, samples.size - at)
                    found += detector.feed(samples.copyOfRange(at, at + n), n)
                    at += n
                }
                found += detector.feed(FloatArray(RATE), RATE)
                Log.i(TAG, "Sprachbefehl-Test: ${found.size} Äußerungen in ${File(path).name}")
                for (u in found) {
                    handle(whisper, u, store.current)
                    delay(1_500)
                }
                Log.i(TAG, "Sprachbefehl-Test fertig")
            } finally {
                whisper.close()
            }
        }
    }

    companion object {
        const val TAG = "LauscherVoice"
        private const val RATE = WhisperNative.SAMPLE_RATE
        /** A command with wake word and amount is about 12 tokens; more is the recogniser repeating itself. */
        private const val MAX_TOKENS = 32
        /** Candidates of the careful second try. */
        private const val BEAM = 4
        private val HEADPHONES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }
}
