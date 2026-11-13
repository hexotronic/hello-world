@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.core.PromptKind
import io.github.hexotronic.lauscher.analysis.core.PromptSettings
import io.github.hexotronic.lauscher.analysis.core.Prompts
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.block

/** Ready-made additions per kind, as a start for the listener's own. */
private fun suggestions(kind: PromptKind): List<String> = when (kind) {
    PromptKind.SCENES -> listOf(
        "Kampfszenen immer mit Stärke 3.",
        "Reine Dialoge ohne Handlung bekommen NONE (keine Musik).",
        "Lieber CALM als NEUTRAL, wenn nichts Besonderes passiert.",
        "Das Buch spielt im Weltraum: Orte an Bord sind SPACE.",
    )
    PromptKind.SCENE_CUT -> listOf("Im Zweifel A – die Musik soll lieber etwas später wechseln.")
    PromptKind.RECAP -> listOf("Höchstens drei Sätze.", "Nenne die wichtigsten Figuren mit Namen.", "Erzähle in der Du-Form.")
    PromptKind.ASSISTANT -> listOf(
        "Antworte in höchstens zwei Sätzen.",
        "Nenne bei Antworten die Zeit der passenden Stelle, z. B. (1:23:00).",
        "Sprich mich mit Du an.",
    )
}

/**
 * "KI-Anweisungen": the built-in instructions of the on-device language model for each of its jobs –
 * to read, to add to, to rewrite or reset, and to check with the test story.
 */
@Composable
fun PromptsScreen(vm: MainViewModel) {
    val settings by vm.promptSettings.collectAsStateWithLifecycle()
    val changes by vm.models.changes.collectAsStateWithLifecycle()
    var view by remember { mutableStateOf<PromptKind?>(null) }
    var extra by remember { mutableStateOf<PromptKind?>(null) }
    var edit by remember { mutableStateOf<PromptKind?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            title = { Text("KI-Anweisungen") },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "head") {
                Text(
                    "Diese Anweisungen bekommt das Sprachmodell auf dem Handy bei jeder Aufgabe, zusammen mit dem Text des Buchs. " +
                        "Sie sind auf Englisch – so folgen ihnen auch kleine Modelle am genauesten. Du kannst eine Ergänzung anhängen " +
                        "(gern auf Deutsch, z. B. „Kampfszenen immer mit Stärke 3“) oder den ganzen Text ändern. Die Form der Antwort " +
                        "bleibt fest, die App versteht das Modell also immer. Ob eine Änderung genauer ist, zeigt „Prüfen“ unten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(PromptKind.entries, key = { it.name }) { kind ->
                PromptCard(kind, settings, onView = { view = kind }, onExtra = { extra = kind }, onEdit = { edit = kind }, onReset = { vm.resetPrompt(kind) })
            }
            item(key = "whisper") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 20.dp, bottom = 12.dp)
                        .block(MaterialTheme.colorScheme.surfaceContainerLow, shadow = 3.dp)
                        .padding(12.dp),
                ) {
                    Text("Spracherkennung (Whisper)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Sie bekommt keine Anweisung, nur Hinweiswörter für die Sprachsteuerung: deine Befehle samt Weckwort. Die " +
                            "änderst du unter „Sprachsteuerung“.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { vm.navigate(Screen.Voice) }) { Text("Zur Sprachsteuerung") }
                }
            }
            item(key = "test") {
                SectionHeader("Prüfen")
                Text(
                    "Der Kurztest lässt das gewählte Sprachmodell die eingebaute Testgeschichte bearbeiten – mit den Anweisungen, " +
                        "die gerade gelten. Ergebnisse mit eigenen Anweisungen stehen getrennt von denen mit den eingebauten, so " +
                        "lässt sich vergleichen. Szenen und „Frag das Buch“ werden gemessen; Szenenwechsel und Rückblick nicht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                RetroButton(onClick = { vm.testPrompts() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(if (settings.changed) "Mit meinen Anweisungen prüfen" else "Mit den eingebauten Anweisungen prüfen")
                }
                ModelTestPanel(vm, changes)
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    view?.let { PromptViewDialog(it, settings) { view = null } }
    extra?.let { kind -> PromptExtraDialog(kind, settings.extra[kind].orEmpty(), onDismiss = { extra = null }) { vm.setPromptExtra(kind, it); extra = null } }
    edit?.let { kind ->
        PromptEditDialog(kind, settings.custom[kind] ?: Prompts.default(kind), onDismiss = { edit = null }) { vm.setPromptText(kind, it); edit = null }
    }
}

@Composable
private fun PromptCard(kind: PromptKind, s: PromptSettings, onView: () -> Unit, onExtra: () -> Unit, onEdit: () -> Unit, onReset: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, bottom = 12.dp)
            .block(
                if (s.isChanged(kind)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                shadow = 3.dp,
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            when {
                s.isCustom(kind) -> PixelBadge("Eigene Fassung", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                s.hasExtra(kind) -> PixelBadge("Mit Ergänzung", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                else -> PixelBadge("Eingebaut", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(kind.purpose, style = MaterialTheme.typography.bodySmall)
        Text("Antwort: ${kind.answerForm}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.extra[kind]?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text("Deine Ergänzung: „$it“", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
        if (s.isCustom(kind)) {
            Prompts.warnings(kind, s.custom[kind].orEmpty()).forEach {
                Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RetroButton(onClick = onView, style = RetroButtonStyle.SECONDARY) { Text("Ansehen") }
            RetroButton(onClick = onExtra, style = RetroButtonStyle.PLAIN) { Text("Ergänzen …") }
            RetroButton(onClick = onEdit, style = RetroButtonStyle.PLAIN) { Text("Bearbeiten …") }
            if (s.isChanged(kind)) RetroButton(onClick = onReset, style = RetroButtonStyle.PLAIN) { Text("Zurücksetzen") }
        }
    }
}

private enum class ViewPart(val label: String) { INSTRUCTION("Anweisung"), REQUEST("Beispiel-Anfrage"), ANSWER("Beispiel-Antwort") }

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)

@Composable
private fun PromptViewDialog(kind: PromptKind, s: PromptSettings, onDismiss: () -> Unit) {
    var part by rememberSaveable { mutableStateOf(ViewPart.INSTRUCTION) }
    val clipboard = LocalClipboardManager.current
    val text = remember(part, s) {
        when (part) {
            ViewPart.INSTRUCTION -> Prompts.effective(kind, s)
            ViewPart.REQUEST -> Prompts.example(kind)
            ViewPart.ANSWER -> Prompts.exampleAnswer(kind)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.label) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewPart.entries.forEach { p -> FilterChip(selected = part == p, onClick = { part = p }, label = { Text(p.label) }) }
                }
                Text(
                    when (part) {
                        ViewPart.INSTRUCTION -> if (s.isChanged(kind)) "So, wie sie gerade gilt – mit deinen Änderungen." else "Der eingebaute Text."
                        ViewPart.REQUEST -> "Was das Modell außerdem bekommt, hier mit der Testgeschichte „Die Glut von Weidenbach“."
                        ViewPart.ANSWER -> "So sieht eine Antwort aus."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(8.dp),
                ) {
                    SelectionContainer { Text(text, style = Mono) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
        dismissButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Kopieren") } },
    )
}

@Composable
private fun PromptExtraDialog(kind: PromptKind, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(kind) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ergänzung: ${kind.label}") },
        text = {
            Column {
                Text(
                    "Wird am Ende der Anweisung angehängt. Kurze, klare Regeln wirken am besten – eine je Zeile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 8,
                    placeholder = { Text("z. B. ${suggestions(kind).first()}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text("Vorschläge (antippen fügt hinzu):", style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions(kind).forEach { sug ->
                        AssistChip(onClick = { text = if (text.isBlank()) sug else text.trimEnd() + "\n" + sug }, label = { Text(sug) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(if (text.isBlank()) "Entfernen" else "Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun PromptEditDialog(kind: PromptKind, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable(kind) { mutableStateOf(current) }
    val warnings = remember(text) { Prompts.warnings(kind, text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bearbeiten: ${kind.label}") },
        text = {
            Column {
                Text(
                    "Der ganze Text der Anweisung. Die Listen der erlaubten Werte sollten drinbleiben, sonst kennt das Modell sie " +
                        "nicht. Für kleine Wünsche ist eine Ergänzung einfacher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = Mono,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 380.dp),
                )
                warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                Text("${text.length} Zeichen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { text = Prompts.default(kind) }) { Text("Eingebauten Text einsetzen") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
