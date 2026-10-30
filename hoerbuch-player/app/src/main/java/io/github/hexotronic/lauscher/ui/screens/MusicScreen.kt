@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.AnalysisStatus
import io.github.hexotronic.lauscher.analysis.DownloadState
import io.github.hexotronic.lauscher.analysis.ModelCatalog
import io.github.hexotronic.lauscher.analysis.ModelKind
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.music.AnalysisDepth
import io.github.hexotronic.lauscher.music.MusicMode
import io.github.hexotronic.lauscher.music.MusicSettings
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.synth.Atmosphere
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.music.synth.Setting
import io.github.hexotronic.lauscher.music.synth.SoundSpec
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.LabeledSlider
import io.github.hexotronic.lauscher.ui.components.MoodMap
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroBox
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroToggle
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.components.moodColor
import io.github.hexotronic.lauscher.ui.components.onMoodColor
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import kotlin.math.roundToInt

@Composable
fun MusicScreen(vm: MainViewModel) {
    val s by vm.musicSettings.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val scenes by vm.scenes.collectAsStateWithLifecycle()
    val status by vm.analysisStatus.collectAsStateWithLifecycle()
    val download by vm.modelDownload.collectAsStateWithLifecycle()
    val changes by vm.modelChanges.collectAsStateWithLifecycle()
    val libraryInstalled by vm.libraryInstalled.collectAsStateWithLifecycle()
    var editScene by remember { mutableStateOf<Scene?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
            title = { Text("Hintergrundmusik") },
            actions = {
                RetroToggle(checked = s.enabled, onCheckedChange = { on -> vm.updateMusic { it.copy(enabled = on) } })
                Spacer(Modifier.width(12.dp))
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                "Leise Musik und Geräusche unter der Erzählung – vom eingebauten Synthesizer oder aus der " +
                    "Musikbibliothek. Sie macht unter Sprache Platz. Die Szenen erkennt eine lokale KI – " +
                    "nichts verlässt das Gerät.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            // Current scene and mood map of the loaded book.
            val book = np.book
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, bottom = 8.dp)
                    .block(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
            ) {
                Text("Stimmungskarte", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                if (book == null) {
                    Text("Starte ein Hörbuch, um seine Szenen zu sehen.", style = MaterialTheme.typography.bodySmall)
                } else {
                    val scene = np.scene
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (scene?.mood != null) {
                            PixelBadge(scene.mood.label, moodColor(scene.mood), onMoodColor(scene.mood))
                            Spacer(Modifier.width(8.dp))
                        }
                        scene?.setting?.let { place ->
                            PixelBadge(place.label, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                !s.enabled -> "Musik ist ausgeschaltet"
                                scene == null -> "Diese Stelle ist noch nicht analysiert"
                                scene.mood == null -> "Hier bewusst keine Musik"
                                else -> scene.title.ifBlank { scene.mood.hint }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MoodMap(scenes, np.durationMs, np.positionMs, onSceneClick = { editScene = it })
                    val covered = scenes.sumOf { it.endMs - it.startMs }
                    Text(
                        "${if (np.durationMs > 0) covered * 100 / np.durationMs else 0} % analysiert · ${scenes.count { it.mood != null }} Szenen mit Musik · Tippen zum Bearbeiten",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    MoodLegend(scenes)
                }
            }

            SectionHeader("Art der Musik")
            MusicMode.entries.forEach { mode ->
                ModeOption(mode, selected = s.mode == mode) { vm.updateMusic { it.copy(mode = mode, enabled = true) } }
            }

            when (s.mode) {
                MusicMode.FIXED -> FixedChoice(s, vm)
                MusicMode.SCENES -> AnalysisBlock(vm, s, status)
                MusicMode.CHAPTERS -> Text(
                    "Am Anfang jedes Kapitels spielt 25 Sekunden ruhige Musik, am Ende 15 Sekunden ein leiser Ausklang.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            SectionHeader("Klangquelle")
            MusicSource.entries.forEach { source ->
                SourceOption(source, selected = s.source == source, enabled = source == MusicSource.SYNTH || libraryInstalled.isNotEmpty()) {
                    vm.updateMusic { it.copy(source = source) }
                }
            }
            RetroButton(
                onClick = { vm.navigate(Screen.MusicLibrary) },
                style = if (libraryInstalled.isEmpty()) RetroButtonStyle.PRIMARY else RetroButtonStyle.SECONDARY,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    if (libraryInstalled.isEmpty()) {
                        "Musikbibliothek öffnen (${MusicCatalog.tracks.size} Stücke)"
                    } else {
                        "Musikbibliothek (${libraryInstalled.size}/${MusicCatalog.tracks.size} geladen)"
                    },
                )
            }

            SectionHeader("Mischung")
            LabeledSlider("Lautstärke", s.volume, "${(s.volume * 100).toInt()} %") { v ->
                vm.updateMusic { it.copy(volume = v) }
            }
            LabeledSlider(
                "Unter Sprache leiser",
                s.duckDepthDb,
                "${s.duckDepthDb.toInt()} dB",
                range = 0f..18f,
                subtitle = "Die Musik weicht der Stimme aus und kommt in Pausen zurück",
            ) { v -> vm.updateMusic { it.copy(duckDepthDb = v) } }
            LabeledSlider("Übergänge", s.crossfadeSeconds, "${s.crossfadeSeconds.toInt()} s", range = 1f..10f) { v ->
                vm.updateMusic { it.copy(crossfadeSeconds = v) }
            }
            SwitchRow("Gong bei Kapitelwechsel", null, s.chapterChime) { v -> vm.updateMusic { it.copy(chapterChime = v) } }

            SectionHeader("Hörproben")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Mood.entries.forEach { m ->
                    FilterChip(selected = false, onClick = { vm.previewMusic(SoundSpec(mood = m, intensity = 0.6f)) }, label = { Text(m.label) })
                }
                Atmosphere.entries.forEach { a ->
                    FilterChip(selected = false, onClick = { vm.previewMusic(SoundSpec(atmosphere = a)) }, label = { Text(a.label) })
                }
            }

            SectionHeader("KI-Modelle (lokal)")
            Text(
                "Einmal herunterladen, danach funktioniert alles offline. Die Spracherkennung ist nötig, das " +
                    "Sprachmodell macht die Szenenwahl klüger und schreibt den Rückblick „Was bisher geschah“.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ModelSection(vm, ModelKind.SPEECH, s.whisperModel, download, changes)
            ModelSection(vm, ModelKind.LANGUAGE, s.llmModel, download, changes)

            SectionHeader("Rückmeldungen und Daten")
            AnalysisDataSection(vm)
        }
    }

    editScene?.let { scene ->
        SceneEditDialog(
            scene,
            onDismiss = { editScene = null },
            onConfirm = { vm.confirmScene(scene); editScene = null },
            onReanalyze = { vm.deleteScene(scene, reanalyze = true); editScene = null },
            onDelete = { vm.deleteScene(scene, reanalyze = false); editScene = null },
        ) { mood, level, title, setting ->
            vm.overrideScene(scene, mood, level, title, setting)
            editScene = null
        }
    }
}

@Composable
private fun MoodLegend(scenes: List<Scene>) {
    val used = scenes.mapNotNull { it.mood }.distinct()
    if (used.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        used.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(moodColor(m)).border(1.dp, LocalRetro.current.ink))
                Spacer(Modifier.width(4.dp))
                Text(m.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ModeOption(mode: MusicMode, selected: Boolean, onClick: () -> Unit) =
    OptionBox(mode.label, mode.hint, selected, enabled = true, onClick)

@Composable
private fun SourceOption(source: MusicSource, selected: Boolean, enabled: Boolean, onClick: () -> Unit) =
    OptionBox(source.label, if (enabled) source.hint else "Lade zuerst Stücke herunter", selected, enabled, onClick)

@Composable
private fun OptionBox(label: String, hint: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    RetroBox(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 20.dp, bottom = 10.dp),
        background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shadow = if (selected) 0.dp else 3.dp,
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .border(2.dp, LocalRetro.current.ink)
                    .padding(3.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FixedChoice(s: MusicSettings, vm: MainViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Musik", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.fixedMood == null, onClick = { vm.updateMusic { it.copy(fixedMood = null) } }, label = { Text("Keine") })
            Mood.entries.forEach { m ->
                FilterChip(selected = s.fixedMood == m, onClick = { vm.updateMusic { it.copy(fixedMood = m) } }, label = { Text(m.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Ort (für die Musikbibliothek)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.fixedSetting == null, onClick = { vm.updateMusic { it.copy(fixedSetting = null) } }, label = { Text("Egal") })
            Setting.entries.forEach { p ->
                FilterChip(selected = s.fixedSetting == p, onClick = { vm.updateMusic { it.copy(fixedSetting = p) } }, label = { Text(p.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Geräuschkulisse", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.fixedAtmosphere == null,
                onClick = { vm.updateMusic { it.copy(fixedAtmosphere = null) } },
                label = { Text("Keine") },
            )
            Atmosphere.entries.forEach { a ->
                FilterChip(
                    selected = s.fixedAtmosphere == a,
                    onClick = { vm.updateMusic { it.copy(fixedAtmosphere = a) } },
                    label = { Text(a.label) },
                )
            }
        }
    }
}

@Composable
private fun AnalysisBlock(vm: MainViewModel, s: MusicSettings, status: AnalysisStatus) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (!vm.models.isInstalled(s.whisperModel)) {
            Text(
                "Für die Szenen-Erkennung lade unten zuerst ein Spracherkennungsmodell – oder füge in den Buch-Details " +
                    "unter „Buchtext“ den Text des Buchs ein, dann braucht es keine Spracherkennung.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when (status) {
            is AnalysisStatus.Running -> {
                Text(status.phase + if (status.automatic) " (vorausschauend)" else "", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                RetroProgress(if (status.total > 0) status.done.toFloat() / status.total else 0f)
                Text(
                    "${status.done} von ${status.total} Abschnitten" +
                        (status.remainingSeconds?.let { " · noch ca. ${formatHuman(it * 1000L)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.height(8.dp))
                RetroButton(onClick = { vm.stopAnalysis() }, style = RetroButtonStyle.PLAIN) { Text("Stopp") }
            }
            is AnalysisStatus.Finished -> Text(
                "Fertig: ${status.windows} " + (if (status.windows == 1) "Abschnitt " else "Abschnitte ") +
                    if (status.usedLanguageModel) "mit dem Sprachmodell analysiert" else "über Schlüsselwörter eingeordnet",
                style = MaterialTheme.typography.bodyMedium,
            )
            is AnalysisStatus.Failed -> Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            AnalysisStatus.Idle -> Unit
        }
        if (status !is AnalysisStatus.Running) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RetroButton(onClick = { vm.analyzeBook(fromCurrentPosition = true) }) { Text("Ab hier analysieren") }
                RetroButton(onClick = { vm.analyzeBook(fromCurrentPosition = false) }, style = RetroButtonStyle.SECONDARY) { Text("Ganzes Buch") }
            }
            var clearBook by remember { mutableStateOf(false) }
            TextButton(onClick = { clearBook = true }) { Text("Analyse dieses Buchs löschen …") }
            if (clearBook) {
                ClearAnalysisDialog(allBooks = false, onDismiss = { clearBook = false }) { keep, transcripts ->
                    vm.clearAnalysis(keepCorrections = keep, transcripts = transcripts)
                    clearBook = false
                }
            }
        }
    }
    SwitchRow(
        "Vorausschauend analysieren",
        "Beim Hören werden die nächsten Minuten im Hintergrund vorbereitet",
        s.analyzeAhead,
    ) { v -> vm.updateMusic { it.copy(analyzeAhead = v) } }
    SwitchRow(
        "Geräusche passend zum Ort",
        "Wald, Meer, Wind oder Kaminfeuer leise unter der Musik",
        s.placeSounds,
    ) { v -> vm.updateMusic { it.copy(placeSounds = v) } }
    SwitchRow(
        "Dezente Musik, wo noch nichts analysiert ist",
        null,
        s.neutralFallback,
    ) { v -> vm.updateMusic { it.copy(neutralFallback = v) } }
    SamplingSettings(vm, s)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Sprache der Hörbücher", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("de" to "Deutsch", "en" to "Englisch", "auto" to "Automatisch").forEach { (code, label) ->
                FilterChip(selected = s.language == code, onClick = { vm.updateMusic { it.copy(language = code) } }, label = { Text(label) })
            }
        }
    }
}

/** How often and how long the book is sampled, how fast the recognition listens, and what it costs. */
@Composable
private fun SamplingSettings(vm: MainViewModel, s: MusicSettings) {
    val plan = s.samplingPlan
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Stichproben", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalysisDepth.entries.forEach { d ->
                FilterChip(
                    selected = s.sampleEveryMinutes == d.everyMinutes && s.sampleSeconds == d.sampleSeconds,
                    onClick = { vm.updateMusic { it.copy(sampleEveryMinutes = d.everyMinutes, sampleSeconds = d.sampleSeconds) } },
                    label = { Text(d.label) },
                )
            }
        }
    }
    LabeledSlider(
        "Stichprobe alle",
        s.sampleEveryMinutes,
        formatMinutes(s.sampleEveryMinutes),
        range = 1f..10f,
        steps = 17,
        subtitle = "So oft kann die Musik die Szene wechseln",
    ) { v -> vm.updateMusic { it.copy(sampleEveryMinutes = (v * 2).roundToInt() / 2f) } }
    LabeledSlider(
        "Länge je Stichprobe",
        s.sampleSeconds.toFloat(),
        if (plan.full) "alles" else "${s.sampleSeconds} s",
        range = 5f..MusicSettings.FULL_SAMPLE.toFloat(),
        steps = 23,
        subtitle = "Mehr gehörter Text erkennt Szenen sicherer, dauert aber länger",
    ) { v -> vm.updateMusic { it.copy(sampleSeconds = (v / 5f).roundToInt() * 5) } }
    LabeledSlider(
        "Tempo der Spracherkennung",
        s.speechSpeed,
        speedText(s.speechSpeed) + "×",
        range = 1f..2f,
        steps = 3,
        subtitle = "Die Stimme wird für die Erkennung schneller abgespielt (Tonhöhe bleibt). Spart Zeit, erkennt aber ungenauer: " +
            "Lauscher vergleicht deshalb bei jedem Buch zuerst eine Probe mit 1× und nutzt das Tempo nur, wenn die Erkennung kaum leidet.",
    ) { v -> vm.updateMusic { it.copy(speechSpeed = (v * 4).roundToInt() / 4f) } }
    val probe by vm.speedCheck.collectAsStateWithLifecycle()
    probe?.let { c ->
        Text(
            "Letzte Tempoprobe: ${speedText(c.speed)}× erkannte ${(c.agreement * 100).roundToInt()} % der Wörter wie 1× – " +
                if (c.accepted) "wird genutzt." else "zu ungenau, dieses Buch wird mit 1× analysiert.",
            style = MaterialTheme.typography.bodySmall,
            color = if (c.accepted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
    }
    val measured = s.measuredSpeechFactor > 0 && s.measuredModelSeconds > 0
    val speech = if (s.measuredSpeechFactor > 0) s.measuredSpeechFactor else MusicSettings.DEFAULT_SPEECH_FACTOR
    val model = if (s.measuredModelSeconds > 0) s.measuredModelSeconds else MusicSettings.DEFAULT_MODEL_SECONDS
    val perHour = plan.secondsPerHour(speech, model)
    val perHourText = plan.secondsPerHour(speech, model, withSpeech = false)
    Text(
        "Gehört werden ${(plan.coverage * 100).roundToInt()} % des Buchs (${if (plan.full) "alles" else "${plan.heardMs / 1000} s"} " +
            "alle ${formatMinutes(s.sampleEveryMinutes)}), die Erkennung hört ${speedText(s.speechSpeed)}-fach schnell. " +
            "Rechenzeit je Stunde Hörbuch: ca. ${formatHuman((perHour * 1000).toLong())}, mit Buchtext ca. ${formatHuman((perHourText * 1000).toLong())} " +
            if (measured) "(auf diesem Gerät gemessen)." else "(Schätzung – nach der ersten Analyse auf diesem Gerät gemessen).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
    SwitchRow(
        "Buchtext per Hörprobe abgleichen",
        "Mit eingefügtem Buchtext alle 10 Minuten 12 Sekunden hören, damit Text und Zeit genau zusammenpassen",
        s.alignWithSpeech,
    ) { v -> vm.updateMusic { it.copy(alignWithSpeech = v) } }
}

private fun speedText(v: Float): String =
    String.format(java.util.Locale.GERMANY, "%.2f", v).trimEnd('0').trimEnd(',')

private fun formatMinutes(m: Float): String =
    if (m % 1f == 0f) "${m.toInt()} Min." else String.format(java.util.Locale.GERMANY, "%.1f Min.", m)

@Composable
private fun ModelSection(vm: MainViewModel, kind: ModelKind, selectedId: String?, download: DownloadState?, changes: Int) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.importModel(kind, uri)
    }
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            if (kind == ModelKind.SPEECH) "Spracherkennung (Whisper)" else "Sprachmodell (GGUF)",
            fontFamily = PixelFont,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        val entries = ModelCatalog.models.filter { it.kind == kind }
        // [changes] is read so the list refreshes after downloads and deletions.
        val installed = remember(changes) { (entries.map { it.id } + ModelCatalog.customId(kind)).filter { vm.models.isInstalled(it) }.toSet() }
        for (m in entries) {
            ModelRow(
                title = m.label,
                description = m.description,
                sizeText = "${m.sizeMb} MB",
                installed = m.id in installed,
                selected = m.id == selectedId,
                download = download?.takeIf { it.modelId == m.id },
                onDownload = { vm.downloadModel(m) },
                onCancel = { vm.cancelDownload() },
                onSelect = { vm.selectModel(kind, m.id) },
                onDelete = { vm.deleteModel(m.id) },
            )
        }
        val customId = ModelCatalog.customId(kind)
        if (customId in installed) {
            ModelRow(
                title = "Eigenes Modell",
                description = "Von dir gewählte Datei",
                sizeText = "${vm.models.installedSizeMb(customId)} MB",
                installed = true,
                selected = customId == selectedId,
                download = null,
                onDownload = {},
                onCancel = {},
                onSelect = { vm.selectModel(kind, customId) },
                onDelete = { vm.deleteModel(customId) },
            )
        }
        RetroButton(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            style = RetroButtonStyle.PLAIN,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(if (kind == ModelKind.SPEECH) "Eigene ggml-Datei wählen …" else "Eigene GGUF-Datei wählen …")
        }
    }
}

@Composable
private fun ModelRow(
    title: String,
    description: String,
    sizeText: String,
    installed: Boolean,
    selected: Boolean,
    download: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, bottom = 10.dp)
            .block(if (selected && installed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, shadow = 3.dp)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (selected && installed) {
                        Spacer(Modifier.width(8.dp))
                        PixelBadge("Aktiv", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                    }
                }
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sizeText, style = MaterialTheme.typography.labelSmall)
            }
            if (installed) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
            }
        }
        when {
            download != null && download.error == null -> {
                Spacer(Modifier.height(6.dp))
                RetroProgress(download.progress)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${download.bytes / 1_000_000} von ${if (download.total > 0) download.total / 1_000_000 else "?"} MB",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancel) { Text("Abbrechen") }
                }
            }
            installed && !selected -> {
                Spacer(Modifier.height(6.dp))
                RetroButton(onClick = onSelect, style = RetroButtonStyle.SECONDARY) { Text("Verwenden") }
            }
            !installed -> {
                download?.error?.let {
                    Text("Fehler: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                RetroButton(onClick = onDownload) { Text("Laden ($sizeText)") }
            }
        }
    }
}

@Composable
internal fun SceneEditDialog(
    scene: Scene,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    onReanalyze: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSave: (Mood?, Int, String, Setting?) -> Unit,
) {
    var mood by remember { mutableStateOf(scene.mood) }
    var level by remember { mutableStateOf(scene.level) }
    var title by remember { mutableStateOf(scene.title) }
    var setting by remember { mutableStateOf(scene.setting) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Szene ${formatClock(scene.startMs)} – ${formatClock(scene.endMs)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (onConfirm != null || onReanalyze != null || onDelete != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        onConfirm?.let { TextButton(onClick = it) { Text("Passt so") } }
                        onReanalyze?.let { TextButton(onClick = it) { Text("Neu analysieren") } }
                        onDelete?.let { TextButton(onClick = it) { Text("Analyse löschen") } }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titel") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mood == null, onClick = { mood = null }, label = { Text("Keine Musik") })
                    Mood.entries.forEach { m ->
                        FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(m.label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Spannung", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1 to "Dezent", 2 to "Deutlich", 3 to "Stark").forEach { (l, label) ->
                        FilterChip(selected = level == l, onClick = { level = l }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Ort", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = setting == null, onClick = { setting = null }, label = { Text("Unklar") })
                    Setting.entries.forEach { p ->
                        FilterChip(selected = setting == p, onClick = { setting = p }, label = { Text(p.label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(mood, level, title, setting) }) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
