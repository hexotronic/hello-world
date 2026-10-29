@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.BuildConfig
import io.github.hexotronic.lauscher.data.LibraryFolder
import io.github.hexotronic.lauscher.data.ThemeMode
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.ChoiceDialog
import io.github.hexotronic.lauscher.ui.components.ClickRow
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.formatSpeed
import java.time.LocalDate

private enum class Picker { REWIND, FORWARD, SPEED, SLEEP_DEFAULT, SLEEP_FROM, SLEEP_TO, THEME }

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmRemove by remember { mutableStateOf<LibraryFolder?>(null) }
    var confirmStats by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.addFolder(uri)
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }

    Column(modifier) {
        TopAppBar(title = { Text("Einstellungen") })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionHeader("Bibliothek")
            folders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.name) },
                    supportingContent = { Text(Uri.decode(folder.uri).substringAfterLast(':'), maxLines = 1) },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { confirmRemove = folder }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Ordner entfernen")
                        }
                    },
                )
            }
            ClickRow("Ordner hinzufügen", "Jeder Unterordner mit Audiodateien wird ein Hörbuch") { pickFolder.launch(null) }
            ClickRow("Bibliothek neu einlesen", "Findet neue und geänderte Hörbücher") { vm.rescan() }
            ClickRow("Alles komplett neu einlesen", "Liest Kapitel, Cover und Längen aller Bücher erneut") { vm.rescan(force = true) }
            SwitchRow("Beim Start automatisch einlesen", null, s.autoRescan) { v -> vm.updateSettings { it.copy(autoRescan = v) } }
            ClickRow("Fehlende Hörbücher entfernen", "Bücher, deren Dateien nicht mehr gefunden werden") { vm.deleteMissing() }

            SectionHeader("Wiedergabe")
            ClickRow("Zurückspulen", "${s.rewindSeconds} Sekunden") { picker = Picker.REWIND }
            ClickRow("Vorspulen", "${s.forwardSeconds} Sekunden") { picker = Picker.FORWARD }
            ClickRow("Standard-Geschwindigkeit", formatSpeed(s.defaultSpeed)) { picker = Picker.SPEED }
            SwitchRow(
                "Intelligentes Zurückspulen",
                "Nach einer Pause ein paar Sekunden zurück – je länger die Pause, desto mehr",
                s.smartRewind,
            ) { v -> vm.updateSettings { it.copy(smartRewind = v) } }
            SwitchRow(
                "Kopfhörertasten springen zwischen Kapiteln",
                if (s.headsetSkipsChapters) "Weiter/Zurück wechselt das Kapitel" else "Weiter/Zurück spult vor und zurück",
                s.headsetSkipsChapters,
            ) { v -> vm.updateSettings { it.copy(headsetSkipsChapters = v) } }
            SwitchRow(
                "Nach Anrufen fortsetzen",
                "Nach kurzen Unterbrechungen automatisch weiterspielen",
                s.resumeAfterCall,
            ) { v -> vm.updateSettings { it.copy(resumeAfterCall = v) } }
            SwitchRow("Stille überspringen", null, s.skipSilence) { v -> vm.updateSettings { it.copy(skipSilence = v) } }

            SectionHeader("Schlaf-Timer")
            ClickRow("Standarddauer", "${s.sleepDefaultMinutes} Minuten") { picker = Picker.SLEEP_DEFAULT }
            SwitchRow("Leise ausblenden", null, s.sleepFadeOut) { v -> vm.updateSettings { it.copy(sleepFadeOut = v) } }
            SwitchRow("Schütteln verlängert den Timer", null, s.sleepShakeToExtend) { v ->
                vm.updateSettings { it.copy(sleepShakeToExtend = v) }
            }
            SwitchRow("Lesezeichen beim Einschlafen", null, s.sleepBookmark) { v -> vm.updateSettings { it.copy(sleepBookmark = v) } }
            SwitchRow(
                "Automatischer Schlaf-Timer",
                "Startet nachts von selbst, wenn du abspielst",
                s.sleepAuto,
            ) { v -> vm.updateSettings { it.copy(sleepAuto = v) } }
            if (s.sleepAuto) {
                ClickRow("Ab", "${s.sleepAutoFromHour}:00 Uhr") { picker = Picker.SLEEP_FROM }
                ClickRow("Bis", "${s.sleepAutoToHour}:00 Uhr") { picker = Picker.SLEEP_TO }
            }

            SectionHeader("Darstellung")
            ClickRow("Design", s.theme.label) { picker = Picker.THEME }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchRow("Farben vom Hintergrundbild (Material You)", null, s.dynamicColor) { v ->
                    vm.updateSettings { it.copy(dynamicColor = v) }
                }
            }
            SwitchRow("Bildschirm im Player anlassen", null, s.keepScreenOn) { v -> vm.updateSettings { it.copy(keepScreenOn = v) } }

            SectionHeader("Daten")
            ClickRow("Sicherung exportieren", "Fortschritt, Lesezeichen, Statistik und Einstellungen als Datei") {
                exportBackup.launch("lauscher-sicherung-${LocalDate.now()}.json")
            }
            ClickRow("Sicherung importieren", "Zum Beispiel auf einem neuen Handy") {
                importBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            ClickRow("Statistik zurücksetzen") { confirmStats = true }

            SectionHeader("Über")
            ClickRow("Lauscher ${BuildConfig.VERSION_NAME}", "Hörbuch-Player mit Klangstudio · Open Source") {}
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }

    when (picker) {
        Picker.REWIND -> ChoiceDialog("Zurückspulen", listOf(5, 10, 15, 20, 30, 45, 60), s.rewindSeconds, { "$it Sekunden" },
            { picker = null }) { v -> vm.updateSettings { it.copy(rewindSeconds = v) } }
        Picker.FORWARD -> ChoiceDialog("Vorspulen", listOf(10, 15, 20, 30, 45, 60, 90, 120), s.forwardSeconds, { "$it Sekunden" },
            { picker = null }) { v -> vm.updateSettings { it.copy(forwardSeconds = v) } }
        Picker.SPEED -> ChoiceDialog(
            "Standard-Geschwindigkeit",
            listOf(0.75f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.3f, 1.4f, 1.5f, 1.75f, 2f),
            s.defaultSpeed,
            { formatSpeed(it) },
            { picker = null },
        ) { v -> vm.updateSettings { it.copy(defaultSpeed = v) } }
        Picker.SLEEP_DEFAULT -> ChoiceDialog("Standarddauer", listOf(5, 10, 15, 20, 30, 45, 60, 90), s.sleepDefaultMinutes,
            { "$it Minuten" }, { picker = null }) { v -> vm.updateSettings { it.copy(sleepDefaultMinutes = v) } }
        Picker.SLEEP_FROM -> ChoiceDialog("Automatisch ab", (0..23).toList(), s.sleepAutoFromHour, { "$it:00 Uhr" },
            { picker = null }) { v -> vm.updateSettings { it.copy(sleepAutoFromHour = v) } }
        Picker.SLEEP_TO -> ChoiceDialog("Automatisch bis", (0..23).toList(), s.sleepAutoToHour, { "$it:00 Uhr" },
            { picker = null }) { v -> vm.updateSettings { it.copy(sleepAutoToHour = v) } }
        Picker.THEME -> ChoiceDialog("Design", ThemeMode.entries, s.theme, { it.label }, { picker = null }) { v ->
            vm.updateSettings { it.copy(theme = v) }
        }
        null -> Unit
    }

    confirmRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Ordner entfernen?") },
            text = { Text("„${folder.name}“ und alle darin gefundenen Hörbücher werden aus der Bibliothek entfernt. Die Dateien bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeFolder(folder.uri)
                    confirmRemove = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Abbrechen") } },
        )
    }
    if (confirmStats) {
        AlertDialog(
            onDismissRequest = { confirmStats = false },
            title = { Text("Statistik zurücksetzen?") },
            text = { Text("Die gesamte Hörzeit-Statistik wird gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearStatistics()
                    confirmStats = false
                }) { Text("Zurücksetzen") }
            },
            dismissButton = { TextButton(onClick = { confirmStats = false }) { Text("Abbrechen") } },
        )
    }
}

