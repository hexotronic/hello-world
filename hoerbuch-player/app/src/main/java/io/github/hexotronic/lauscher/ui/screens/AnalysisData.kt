package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.SwitchRow

/**
 * Settings for the scene analysis: learning from feedback, liked and hidden pieces and
 * deleting analyses (of all books, optionally keeping the listener's corrections).
 */
@Composable
fun AnalysisDataSection(vm: MainViewModel) {
    val s by vm.musicSettings.collectAsStateWithLifecycle()
    val stats by vm.analysisStats.collectAsStateWithLifecycle()
    var clearAll by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshAnalysisStats() }

    SwitchRow(
        "Aus meinen Korrekturen lernen",
        "Korrigierte und bestätigte Szenen dienen der KI als Beispiele für den Rest des Buchs",
        s.learnFromFeedback,
    ) { v -> vm.updateMusic { it.copy(learnFromFeedback = v) } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        stats?.let { st ->
            Text(
                "${st.books} " + (if (st.books == 1) "Buch" else "Bücher") + " analysiert · ${st.windows} Abschnitte · " +
                    "${st.corrections} von dir korrigiert · Mitschrift ${st.transcriptChars / 1000} Tsd. Zeichen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${s.likedTracks.size} Lieblingsstücke · ${s.hiddenTracks.size} ausgeblendete Stücke",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            if (s.hiddenTracks.isNotEmpty()) {
                TextButton(onClick = { vm.showAllTracks() }) { Text("Ausgeblendete wieder zulassen") }
            }
            if (s.likedTracks.isNotEmpty()) {
                TextButton(onClick = { vm.updateMusic { it.copy(likedTracks = emptySet()) } }) { Text("Lieblinge zurücksetzen") }
            }
        }
        RetroButton(onClick = { clearAll = true }, style = RetroButtonStyle.PLAIN) { Text("Alle Analysen löschen …") }
    }
    if (clearAll) {
        ClearAnalysisDialog(allBooks = true, onDismiss = { clearAll = false }) { keep, transcripts ->
            vm.clearAnalysis(allBooks = true, keepCorrections = keep, transcripts = transcripts)
            clearAll = false
        }
    }
}

/** Asks what to delete: everything, or the language model's results only (keeping corrections). */
@Composable
fun ClearAnalysisDialog(allBooks: Boolean, onDismiss: () -> Unit, onConfirm: (keepCorrections: Boolean, transcripts: Boolean) -> Unit) {
    var keep by remember { mutableStateOf(true) }
    var transcripts by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (allBooks) "Alle Analysen löschen?" else "Analyse dieses Buchs löschen?") },
        text = {
            Column {
                Text(
                    "Die Szenen werden bei Bedarf neu analysiert. Die Musikbibliothek und die KI-Modelle bleiben erhalten.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                CheckRow("Meine Korrekturen behalten", keep) { keep = it }
                CheckRow("Auch die Mitschrift löschen (Suche und Rückblick brauchen sie)", transcripts) { transcripts = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(keep, transcripts) }) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
