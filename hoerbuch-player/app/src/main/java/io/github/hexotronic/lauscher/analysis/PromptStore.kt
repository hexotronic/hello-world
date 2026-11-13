package io.github.hexotronic.lauscher.analysis

import android.content.Context
import io.github.hexotronic.lauscher.analysis.core.PromptKind
import io.github.hexotronic.lauscher.analysis.core.PromptSettings
import io.github.hexotronic.lauscher.analysis.core.Prompts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The listener's own instructions and additions for the language model, kept on the phone. */
class PromptStore(context: Context) {
    private val prefs = context.getSharedPreferences("prompts", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(PromptSettings.fromJson(prefs.getString("settings", null)))
    val settings: StateFlow<PromptSettings> = _settings.asStateFlow()
    val current: PromptSettings get() = _settings.value

    /** The instructions the model gets for [kind] right now. */
    fun system(kind: PromptKind): String = Prompts.effective(kind, current)

    fun update(transform: (PromptSettings) -> PromptSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", next.toJson()).apply()
    }
}
