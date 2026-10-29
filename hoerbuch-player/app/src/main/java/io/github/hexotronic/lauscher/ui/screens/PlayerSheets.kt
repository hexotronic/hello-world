@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.data.Bookmark
import io.github.hexotronic.lauscher.playback.AudiobookPlayerCore
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.playback.SleepMode
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman
import io.github.hexotronic.lauscher.ui.formatSpeed
import kotlinx.coroutines.flow.flowOf
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun ChaptersSheet(np: NowPlaying, onDismiss: () -> Unit, onJump: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            "Kapitel (${np.chapters.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, np.chapterIndex - 2))
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 560.dp)) {
            itemsIndexed(np.chapters) { index, chapter ->
                val current = index == np.chapterIndex
                ListItem(
                    headlineContent = {
                        Text(
                            chapter.title,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        if (current) {
                            Column {
                                Text("läuft · noch ${formatClock(chapter.endMs - np.positionMs)}")
                                LinearProgressIndicator(
                                    progress = { np.chapterPositionMs.toFloat() / max(1L, chapter.durationMs) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        } else {
                            Text("beginnt bei ${formatClock(chapter.startMs)}")
                        }
                    },
                    leadingContent = {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = { Text(formatClock(chapter.durationMs), style = MaterialTheme.typography.labelMedium) },
                    colors = if (current) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable { onJump(index) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun BookmarksSheet(vm: MainViewModel, np: NowPlaying, onDismiss: () -> Unit) {
    val bookId = np.book?.id
    val flow = remember(bookId) { bookId?.let { vm.repository.observeBookmarks(it) } ?: flowOf(emptyList()) }
    val bookmarks by flow.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    var adding by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Lesezeichen", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { adding = true }) { Text("Mit Notiz") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.addBookmark() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Hier")
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "Noch keine Lesezeichen. Tippe auf „Hier“, um die aktuelle Stelle zu merken. " +
                    "Beim Einschlafen mit Timer wird automatisch eines gesetzt.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
        }
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(bookmarks, key = { it.id }) { mark ->
                ListItem(
                    headlineContent = { Text(mark.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            if (mark.note.isNotBlank()) Text(mark.note)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(mark.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    leadingContent = { Text(formatClock(mark.positionMs), style = MaterialTheme.typography.labelLarge) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = mark }) { Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten") }
                            IconButton(onClick = { vm.deleteBookmark(mark.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
                        }
                    },
                    modifier = Modifier.clickable {
                        vm.withCore { it.seekTo(mark.positionMs) }
                        onDismiss()
                    },
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    editing?.let { mark ->
        BookmarkEditDialog(mark.title, mark.note, "Lesezeichen bearbeiten", onDismiss = { editing = null }) { t, n ->
            vm.updateBookmark(mark.id, t, n)
            editing = null
        }
    }
    if (adding) {
        BookmarkEditDialog(np.chapter?.title.orEmpty(), "", "Lesezeichen setzen", onDismiss = { adding = false }) { t, n ->
            vm.addBookmark(t, n)
            adding = false
        }
    }
}

@Composable
fun BookmarkEditDialog(title: String, note: String, heading: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var t by remember { mutableStateOf(title) }
    var n by remember { mutableStateOf(note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column {
                OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Titel") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Notiz") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(t, n) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
fun SpeedSheet(vm: MainViewModel, np: NowPlaying, onDismiss: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var speed by remember { mutableStateOf(np.speed) }
    fun applySpeed(value: Float) {
        speed = ((value * 20).roundToInt() / 20f).coerceIn(AudiobookPlayerCore.MIN_SPEED, AudiobookPlayerCore.MAX_SPEED)
        vm.withCore { it.setSpeed(speed) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Geschwindigkeit", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
            Text(formatSpeed(speed), style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Restzeit: ${formatHuman((np.remainingMs / speed).toLong())}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                FilledTonalIconButton(onClick = { applySpeed(speed - 0.05f) }) { Icon(Icons.Filled.Remove, contentDescription = "Langsamer") }
                Slider(
                    value = speed,
                    onValueChange = { speed = ((it * 20).roundToInt() / 20f) },
                    onValueChangeFinished = { applySpeed(speed) },
                    valueRange = AudiobookPlayerCore.MIN_SPEED..AudiobookPlayerCore.MAX_SPEED,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                FilledTonalIconButton(onClick = { applySpeed(speed + 0.05f) }) { Icon(Icons.Filled.Add, contentDescription = "Schneller") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(0.75f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f).forEach { preset ->
                    FilterChip(selected = speed == preset, onClick = { applySpeed(preset) }, label = { Text(formatSpeed(preset)) })
                }
            }
            Text(
                "Die Geschwindigkeit wird pro Hörbuch gespeichert. Die Tonhöhe bleibt dabei natürlich.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FilledTonalButton(
                onClick = { vm.updateSettings { it.copy(defaultSpeed = speed) } },
                enabled = settings.defaultSpeed != speed,
            ) {
                Text("Als Standard für neue Hörbücher (${formatSpeed(settings.defaultSpeed)})")
            }
        }
        SwitchRow("Stille überspringen", "Lange Pausen werden automatisch übersprungen", settings.skipSilence) { on ->
            vm.updateSettings { it.copy(skipSilence = on) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SleepSheet(vm: MainViewModel, np: NowPlaying, onDismiss: () -> Unit) {
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf(settings.sleepDefaultMinutes.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Schlaf-Timer", style = MaterialTheme.typography.titleLarge)
            if (sleep.mode != SleepMode.OFF) {
                Text(
                    when (sleep.mode) {
                        SleepMode.TIMER -> formatClock(sleep.remainingMs)
                        else -> "Kapitelende"
                    },
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                )
                if (sleep.mode == SleepMode.END_OF_CHAPTER) {
                    Text(
                        "Pause in ${formatHuman(sleep.remainingMs)} (${np.chapter?.title.orEmpty()})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    FilledTonalButton(onClick = { vm.withCore { it.extendSleepTimer(5) } }, modifier = Modifier.weight(1f)) { Text("+5 Min.") }
                    FilledTonalButton(onClick = { vm.withCore { it.extendSleepTimer(15) } }, modifier = Modifier.weight(1f)) { Text("+15 Min.") }
                    Button(onClick = { vm.withCore { it.cancelSleepTimer() } }, modifier = Modifier.weight(1f)) { Text("Aus") }
                }
            } else {
                Text(
                    "Die Wiedergabe stoppt automatisch. Die Zeit läuft nur, während abgespielt wird.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20, 30, 45, 60, 90, 120).forEach { minutes ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                vm.withCore { it.startSleepTimer(minutes) }
                                onDismiss()
                            },
                            label = { Text("$minutes Min.") },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = {
                            vm.withCore { it.sleepAtEndOfChapter() }
                            onDismiss()
                        },
                        label = { Text("Ende des Kapitels") },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Slider(
                        value = custom,
                        onValueChange = { custom = it.roundToInt().toFloat() },
                        valueRange = 1f..180f,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        vm.withCore { it.startSleepTimer(custom.toInt()) }
                        onDismiss()
                    }) { Text("${custom.toInt()} Min.") }
                }
            }
        }
        SwitchRow("Leise ausblenden", "Die letzten Sekunden werden sanft leiser", settings.sleepFadeOut) { on ->
            vm.updateSettings { it.copy(sleepFadeOut = on) }
        }
        SwitchRow("Schütteln verlängert", "Handy schütteln startet den Timer neu", settings.sleepShakeToExtend) { on ->
            vm.updateSettings { it.copy(sleepShakeToExtend = on) }
        }
        SwitchRow("Lesezeichen beim Einschlafen", "Merkt die Stelle, an der der Timer gestoppt hat", settings.sleepBookmark) { on ->
            vm.updateSettings { it.copy(sleepBookmark = on) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
