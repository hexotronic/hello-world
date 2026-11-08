@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.ChoiceDialog
import io.github.hexotronic.lauscher.ui.components.ClickRow
import io.github.hexotronic.lauscher.ui.components.LabeledSlider
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.voice.VoiceFeedback
import io.github.hexotronic.lauscher.voice.VoiceInput
import io.github.hexotronic.lauscher.voice.VoiceStatus
import io.github.hexotronic.lauscher.voice.core.VoiceAction
import io.github.hexotronic.lauscher.voice.core.VoiceCommands

private enum class VoicePicker { INPUT, FEEDBACK, IDLE, LANGUAGE }

private val LANGUAGES = listOf("de" to "Deutsch", "en" to "Englisch")

/** Voice control: on/off, what it hears, the commands (with own phrases) and the microphone settings. */
@Composable
fun VoiceScreen(vm: MainViewModel) {
    val s by vm.voiceSettings.collectAsStateWithLifecycle()
    val status by vm.voiceStatus.collectAsStateWithLifecycle()
    val lastHeard by vm.voiceLastHeard.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<VoicePicker?>(null) }
    var addingTo by remember { mutableStateOf<VoiceAction?>(null) }
    val askMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setVoiceEnabled(true)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            title = { Text("Sprachsteuerung") },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SwitchRow(
                "Sprachsteuerung",
                "Hört zu, während ein Hörbuch läuft – auch bei ausgeschaltetem Bildschirm. Erkannt wird auf dem Handy, nichts wird gespeichert oder gesendet.",
                s.enabled,
            ) { on ->
                if (on) askMicrophone.launch(Manifest.permission.RECORD_AUDIO) else vm.setVoiceEnabled(false)
            }
            Text(
                when (val st = status) {
                    VoiceStatus.Off -> if (s.enabled) "Startet mit dem nächsten Hörbuch" else "Aus"
                    is VoiceStatus.Waiting -> st.reason
                    is VoiceStatus.Listening -> "Hört zu: ${st.microphone}"
                } + (lastHeard?.let { "\nZuletzt: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (!vm.voiceHasModel) {
                ClickRow(
                    "Spracherkennungsmodell fehlt",
                    "Die Befehle erkennt das Whisper-Modell der Szenen-Analyse – unter „KI-Modelle“ laden (Whisper Base, 60 MB)",
                ) { vm.navigate(Screen.Music) }
            }

            SectionHeader("Aktivierungswort")
            SwitchRow(
                "Erst nach dem Aktivierungswort",
                "„${s.commands.wakeWord}, Pause“ – so löst ein Gespräch oder das Hörbuch selbst nichts aus. Ohne zählen nur kurze Befehle.",
                s.commands.wakeWordEnabled,
            ) { v -> vm.updateVoice { it.copy(commands = it.commands.copy(wakeWordEnabled = v)) } }
            if (s.commands.wakeWordEnabled) {
                OutlinedTextField(
                    value = s.commands.wakeWord,
                    onValueChange = { w -> vm.updateVoice { it.copy(commands = it.commands.copy(wakeWord = w.take(30))) } },
                    label = { Text("Aktivierungswort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            SectionHeader("Befehle")
            Text(
                "Jede Aktion reagiert auf alle ihre Wörter. Zahlen gehen dazu: „30 Sekunden zurück“, „2 Minuten vor“, „Schlaftimer 20 Minuten“.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            VoiceAction.entries.forEach { action ->
                val setting = s.commands.setting(action)
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(action.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = setting.enabled,
                            onCheckedChange = { v -> vm.updateVoice { it.copy(commands = it.commands.update(action) { c -> c.copy(enabled = v) }) } },
                        )
                    }
                    if (setting.enabled) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            setting.phrases.forEach { phrase ->
                                // Tapping a word removes it; the last one stays.
                                InputChip(
                                    selected = false,
                                    onClick = {
                                        vm.updateVoice { it.copy(commands = it.commands.update(action) { c -> c.copy(phrases = c.phrases - phrase) }) }
                                    },
                                    label = { Text(phrase) },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "„$phrase“ entfernen") },
                                    enabled = setting.phrases.size > 1,
                                )
                            }
                            AssistChip(
                                onClick = { addingTo = action },
                                label = { Text("Wort hinzufügen") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            )
                        }
                    }
                }
            }
            ClickRow("Alle Befehle zurücksetzen", "Stellt die ursprünglichen Wörter und das Aktivierungswort wieder her") {
                vm.updateVoice { it.copy(commands = VoiceCommands()) }
            }

            SectionHeader("Audioeingang")
            ClickRow("Mikrofon", "${s.input.label} – ${s.input.hint}") { picker = VoicePicker.INPUT }
            SwitchRow(
                "Echo-Unterdrückung",
                "Filtert das Hörbuch aus dem Mikrofon, wenn es über den Lautsprecher läuft",
                s.echoCancel,
            ) { v -> vm.updateVoice { it.copy(echoCancel = v) } }
            SwitchRow(
                "Nur mit Kopfhörern zuhören",
                "Über den Lautsprecher könnte das Hörbuch selbst Befehle auslösen",
                s.onlyWithHeadphones,
            ) { v -> vm.updateVoice { it.copy(onlyWithHeadphones = v) } }
            LabeledSlider(
                "Empfindlichkeit",
                s.sensitivity,
                when {
                    s.sensitivity < 0.34f -> "niedrig"
                    s.sensitivity < 0.67f -> "mittel"
                    else -> "hoch"
                },
                subtitle = "Höher hört auch leises Sprechen (in der Tasche), reagiert aber öfter auf Geräusche",
            ) { v -> vm.updateVoice { it.copy(sensitivity = v) } }
            SwitchRow(
                "Beim Sprechen leiser",
                "Das Hörbuch wird leiser, sobald du sprichst – so wird der Befehl besser verstanden",
                s.duckWhileSpeaking,
            ) { v -> vm.updateVoice { it.copy(duckWhileSpeaking = v) } }
            ClickRow("Rückmeldung", s.feedback.label + " – „Wo bin ich?“ wird immer angesagt") { picker = VoicePicker.FEEDBACK }
            ClickRow("Nach einer Pause weiter zuhören", "${s.idleMinutes} Minuten, damit „weiter“ noch geht") { picker = VoicePicker.IDLE }
            ClickRow("Sprache der Befehle", LANGUAGES.firstOrNull { it.first == s.language }?.second ?: s.language) { picker = VoicePicker.LANGUAGE }
        }
    }

    when (picker) {
        VoicePicker.INPUT -> ChoiceDialog("Mikrofon", VoiceInput.entries, s.input, { it.label }, { picker = null }) { v ->
            vm.updateVoice { it.copy(input = v) }
        }
        VoicePicker.FEEDBACK -> ChoiceDialog("Rückmeldung", VoiceFeedback.entries, s.feedback, { it.label }, { picker = null }) { v ->
            vm.updateVoice { it.copy(feedback = v) }
        }
        VoicePicker.IDLE -> ChoiceDialog("Weiter zuhören", listOf(5, 15, 30, 60, 120), s.idleMinutes, { "$it Minuten" }, { picker = null }) { v ->
            vm.updateVoice { it.copy(idleMinutes = v) }
        }
        VoicePicker.LANGUAGE -> ChoiceDialog("Sprache der Befehle", LANGUAGES.map { it.first }, s.language, { code ->
            LANGUAGES.first { it.first == code }.second
        }, { picker = null }) { v -> vm.updateVoice { it.copy(language = v) } }
        null -> Unit
    }

    addingTo?.let { action ->
        var text by remember(action) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingTo = null },
            title = { Text("Wort für „${action.label}“") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, label = { Text("z. B. „stopp jetzt“") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val phrase = text.trim()
                    if (phrase.isNotEmpty()) {
                        vm.updateVoice { it.copy(commands = it.commands.update(action) { c -> c.copy(phrases = (c.phrases + phrase).distinct()) }) }
                    }
                    addingTo = null
                }) { Text("Hinzufügen") }
            },
            dismissButton = { TextButton(onClick = { addingTo = null }) { Text("Abbrechen") } },
        )
    }
}
