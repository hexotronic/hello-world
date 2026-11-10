package io.github.hexotronic.lauscher.analysis

import android.util.Log
import io.github.hexotronic.lauscher.analysis.core.AiLogBuffer
import io.github.hexotronic.lauscher.analysis.core.AiLogEntry
import io.github.hexotronic.lauscher.analysis.core.AiStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the AI did, step by step, for the "KI-Protokoll" text window: what speech recognition
 * heard, what the language model was asked and answered, which scenes and music came of it.
 * Kept in memory only (the last entries); also written to logcat under [TAG].
 * May be called from any thread, including the audio thread (it only appends to a list).
 */
class AiLog {
    private val buffer = AiLogBuffer(CAPACITY)
    private val _entries = MutableStateFlow<List<AiLogEntry>>(emptyList())
    val entries: StateFlow<List<AiLogEntry>> = _entries.asStateFlow()

    fun add(step: AiStep, text: String, detail: String? = null, bookId: Long? = null, positionMs: Long? = null) {
        _entries.value = buffer.add(step, text, detail, bookId, positionMs, System.currentTimeMillis())
        Log.i(TAG, "${step.label}: $text")
    }

    fun clear() {
        _entries.value = buffer.clear()
    }

    companion object {
        const val TAG = "LauscherKI"
        private const val CAPACITY = 400
    }
}
