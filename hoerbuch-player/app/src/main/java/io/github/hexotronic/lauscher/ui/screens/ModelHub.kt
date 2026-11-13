package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.ModelKind
import io.github.hexotronic.lauscher.analysis.core.EvalReport
import io.github.hexotronic.lauscher.analysis.core.HfModels
import io.github.hexotronic.lauscher.analysis.core.RamFit
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.block
import kotlin.math.roundToInt

/**
 * Searching Hugging Face for further models: language models as GGUF (llama.cpp) or speech models
 * as ggml files (whisper.cpp). Shows downloads, size and whether the file fits into this phone's memory.
 */
@Composable
fun ModelHubDialog(vm: MainViewModel, kind: ModelKind, onDismiss: () -> Unit) {
    val hub by vm.hub.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(if (kind == ModelKind.SPEECH) "whisper" else "") }
    val ram = remember { vm.models.totalRamMb }
    LaunchedEffect(kind) { if (hub.repos.isEmpty() && !hub.searching) vm.hubSearch(query, kind) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hub.repo != null) {
                    IconButton(onClick = { vm.hubOpen(null, kind) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                }
                Text(hub.repo?.name ?: if (kind == ModelKind.SPEECH) "Whisper-Modelle suchen" else "Sprachmodelle suchen", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column {
                val repo = hub.repo
                if (repo == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(if (kind == ModelKind.SPEECH) "z. B. whisper german" else "z. B. qwen, gemma, llama, phi") },
                        trailingIcon = { IconButton(onClick = { vm.hubSearch(query, kind) }) { Icon(Icons.Filled.Search, contentDescription = "Suchen") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.hubSearch(query, kind) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Auf huggingface.co, die meistgeladenen zuerst. Für Fragen und Szenen eignen sich Chat-Modelle („Instruct“, „-it“) " +
                            "mit 1–4 Milliarden Parametern; dein Handy hat ${ram / 1000} GB Arbeitsspeicher.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    if (hub.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    hub.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hub.repos, key = { it.id }) { r ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .block(MaterialTheme.colorScheme.surfaceContainerLow, shadow = 2.dp)
                                    .clickable { vm.hubOpen(r, kind) }
                                    .padding(8.dp),
                            ) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "von ${r.author} · ${HfModels.count(r.downloads)} Downloads · ♥ ${r.likes}" +
                                        (HfModels.billionsOf(r.name)?.let { " · ${formatBillions(it)}" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (kind == ModelKind.LANGUAGE && !HfModels.looksLikeChatModel(r)) {
                                        PixelBadge("kein Chat-Modell?", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                    if (r.gated) PixelBadge("Anmeldung nötig", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        repo.id + " · " + HfModels.count(repo.downloads) + " Downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (repo.gated) {
                        Text(
                            "Dieses Modell gibt der Anbieter nur nach Anmeldung frei – der Download aus der App klappt dann nicht. " +
                                "Oft gibt es dasselbe Modell frei bei „unsloth“ oder „bartowski“.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (hub.loadingFiles) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    hub.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    val recommended = remember(hub.files) { HfModels.recommended(hub.files, ram) }
                    LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hub.files, key = { it.path }) { f ->
                            val fit = HfModels.fit(f.size, ram)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .block(
                                        if (f == recommended) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                        shadow = 2.dp,
                                    )
                                    .padding(8.dp),
                            ) {
                                Text(f.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${f.sizeMb} MB", style = MaterialTheme.typography.labelSmall)
                                    HfModels.quantOf(f.fileName)?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                    val (bg, fg) = when (fit) {
                                        RamFit.GOOD -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
                                        RamFit.TIGHT -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                                        RamFit.TOO_BIG -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                    }
                                    PixelBadge("RAM ${fit.label}", bg, fg)
                                    if (f == recommended) PixelBadge("Empfohlen", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                                }
                                Spacer(Modifier.height(4.dp))
                                RetroButton(
                                    onClick = {
                                        vm.hubAdd(kind, repo, f)
                                        onDismiss()
                                    },
                                    enabled = fit != RamFit.TOO_BIG,
                                    style = if (f == recommended) RetroButtonStyle.PRIMARY else RetroButtonStyle.SECONDARY,
                                ) { Text("Laden") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

private fun formatBillions(b: Float): String =
    if (b < 1f) "${(b * 1000).roundToInt()} Mio. Parameter" else "${"%.1f".format(b).removeSuffix(",0").removeSuffix(".0")} Mrd. Parameter"

/**
 * "Modelle testen": runs the built-in test story on the installed language models and shows how
 * well each understood it and how fast it is on this phone.
 */
@Composable
fun ModelTestPanel(vm: MainViewModel, changes: Int) {
    val results by vm.evalResults.collectAsStateWithLifecycle()
    val progress by vm.evalProgress.collectAsStateWithLifecycle()
    val userModels by vm.models.userModels.collectAsStateWithLifecycle()
    val installed = remember(changes, userModels) { vm.installedLanguageModels() }
    var choose by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)
            .block(MaterialTheme.colorScheme.surfaceContainerLow, shadow = 3.dp)
            .padding(12.dp),
    ) {
        Text("Modelle testen", style = MaterialTheme.typography.titleSmall)
        Text(
            "Eine kleine deutsche Testgeschichte mit bekannten Antworten: Stimmung, Ort und Musik-Merkmale von Szenen, Fragen " +
                "(auch eine Spoiler-Falle) und Befehle. Misst, wie gut jedes Modell versteht und wie schnell es auf diesem Handy ist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val p = progress
        if (p != null) {
            Spacer(Modifier.height(6.dp))
            Text("${p.label} (${p.model} von ${p.models}) – Schritt ${p.step} von ${p.steps}", style = MaterialTheme.typography.labelMedium)
            RetroProgress(((p.model - 1) + p.step.toFloat() / p.steps) / p.models)
            TextButton(onClick = { vm.cancelEvaluation() }) { Text("Abbrechen") }
        } else {
            Spacer(Modifier.height(6.dp))
            RetroButton(onClick = { choose = true }, enabled = installed.isNotEmpty(), style = RetroButtonStyle.SECONDARY) {
                Text(if (installed.isEmpty()) "Erst ein Sprachmodell laden" else "Test starten …")
            }
        }
        // Each model with the built-in instructions and, if tested so, with the listener's own.
        val shown = installed.flatMap { id ->
            listOf(false, true).mapNotNull { own -> results[EvalReport.key(id, own)]?.let { id to it } }
        }
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            val best = shown.maxOf { it.second.understanding }
            for ((id, r) in shown.sortedByDescending { it.second.understanding }) {
                val label = (vm.models.info(id)?.label ?: r.model) + if (r.customPrompts) " · eigene Anweisungen" else ""
                EvalRow(label, r, r.understanding == best && shown.size > 1)
            }
            Text(
                "Rechenzeit: nur das Sprachmodell, ohne Spracherkennung, bei einer Szene alle 2 Minuten.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (choose) {
        val picked = remember { mutableStateOf(installed.toSet()) }
        var quick by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { choose = false },
            title = { Text("Modelle testen") },
            text = {
                Column {
                    installed.forEach { id ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked.value = if (id in picked.value) picked.value - id else picked.value + id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = id in picked.value, onCheckedChange = null)
                            Spacer(Modifier.width(6.dp))
                            Text(vm.models.info(id)?.label ?: "Eigenes Modell", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = quick, onClick = { quick = true }, label = { Text("Kurz (~2–5 Min.)") })
                        FilterChip(selected = !quick, onClick = { quick = false }, label = { Text("Ganz (~5–15 Min.)") })
                    }
                    Text(
                        "Je Modell; größere Modelle brauchen länger. Die Hintergrund-Analyse pausiert so lange.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = picked.value.isNotEmpty(), onClick = {
                    vm.evaluateModels(installed.filter { it in picked.value }, quick)
                    choose = false
                }) { Text("Starten") }
            },
            dismissButton = { TextButton(onClick = { choose = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun EvalRow(label: String, r: EvalReport, best: Boolean) {
    fun pct(x: Float) = "${(x * 100).roundToInt()} %"
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (best) PixelBadge("Versteht am besten", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Verstehen ${pct(r.understanding)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(120.dp))
            RetroProgress(r.understanding, Modifier.weight(1f), height = 8.dp)
        }
        Text(
            "Stimmung ${pct(r.moods)} · Ort ${pct(r.places)} · Merkmale ${pct(r.cues)} · Fragen ${pct(r.answers)} · " +
                "Befehle ${pct(r.commands)}" + if (r.spoilerSafe < 1f) " · verriet Spoiler!" else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (r.spoilerSafe < 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${"%.1f".format(r.secondsPerQuestion)} s je Frage · Szene ${"%.1f".format(r.secondsPerSceneCall / 8)} s · " +
                "${r.promptTokensPerSecond.roundToInt()} Token/s lesen · ${"%.1f".format(r.generatedTokensPerSecond)} Token/s schreiben · " +
                "Rechenzeit ${"%.1f".format(r.modelSecondsPerHour / 60)} Min. je Stunde Hörbuch" + if (r.quick) " (Kurztest)" else "",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Unspecified,
        )
    }
}
