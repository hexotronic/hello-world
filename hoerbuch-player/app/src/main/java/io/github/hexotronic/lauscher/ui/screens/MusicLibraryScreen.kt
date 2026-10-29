@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.LibraryDownload
import io.github.hexotronic.lauscher.music.library.core.LibraryTrack
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.music.library.core.MusicCollection
import io.github.hexotronic.lauscher.music.library.core.MusicLicense
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.components.moodColor
import io.github.hexotronic.lauscher.ui.components.onMoodColor
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import java.util.Locale

/** Downloadable fantasy soundtrack pieces, grouped by mood, with their credits. */
@Composable
fun MusicLibraryScreen(vm: MainViewModel) {
    val s by vm.musicSettings.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val installed by vm.libraryInstalled.collectAsStateWithLifecycle()
    val download by vm.libraryDownload.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val all = MusicCatalog.tracks
    val missing = all.filter { it.id !in installed }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
            title = { Text("Fantasy-Musik") },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                "Echte Orchester- und Folkmusik, von Menschen komponiert und eingespielt – keine KI-Musik. " +
                    "Sie stammt aus den Soundtracks freier Fantasy-Spiele, steht unter GPL oder Creative Commons " +
                    "und wird unverändert aus deren Archiven geladen. Danach spielt sie offline, passend zur Szene.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, bottom = 8.dp)
                    .block(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(14.dp),
            ) {
                Text("${installed.size} von ${all.size} Stücken geladen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${megabytes(MusicCatalog.totalBytes(all.filter { it.id in installed }))} auf dem Gerät · " +
                        "${formatHuman(all.filter { it.id in installed }.sumOf { it.seconds } * 1000L)} Musik",
                    style = MaterialTheme.typography.labelSmall,
                )
                DownloadProgress(download, onCancel = vm::cancelMusicDownload, onDismiss = vm::dismissMusicDownloadError)
                if (download?.running != true) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (missing.isNotEmpty()) {
                            RetroButton(onClick = { vm.downloadMusic(missing.map { it.id }) }) {
                                Text(if (installed.isEmpty()) "Alles laden (${megabytes(MusicCatalog.totalBytes(missing))})" else "Rest laden (${megabytes(MusicCatalog.totalBytes(missing))})")
                            }
                        }
                        if (installed.isNotEmpty()) {
                            RetroButton(onClick = { vm.deleteMusic(installed) }, style = RetroButtonStyle.PLAIN) { Text("Alle löschen") }
                        }
                    }
                }
            }

            SwitchRow(
                "Für die Hintergrundmusik verwenden",
                if (installed.isEmpty()) "Lade zuerst Stücke herunter" else "Wo einer Stimmung Stücke fehlen, spielt der Synthesizer",
                s.source == MusicSource.LIBRARY,
                enabled = installed.isNotEmpty(),
            ) { on -> vm.updateMusic { it.copy(source = if (on) MusicSource.LIBRARY else MusicSource.SYNTH) } }

            np.musicTrackId?.let { MusicCatalog.byId(it) }?.let { playing ->
                Text(
                    "Läuft gerade: ${playing.credit}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            SectionHeader("Stimmungen")
            Mood.entries.forEach { mood ->
                MoodPack(
                    mood = mood,
                    tracks = MusicCatalog.forMood(mood),
                    installed = installed,
                    expanded = expanded == mood.name,
                    playingId = np.musicTrackId.takeIf { np.musicPlaying },
                    busy = download?.running == true,
                    onToggle = { expanded = if (expanded == mood.name) null else mood.name },
                    onDownload = vm::downloadMusic,
                    onPreview = vm::previewTrack,
                    onStop = vm::stopMusicPreview,
                    onDelete = { vm.deleteMusic(listOf(it.id)) },
                )
            }

            SectionHeader("Herkunft und Lizenzen")
            Credits()
        }
    }
}

@Composable
private fun DownloadProgress(download: LibraryDownload?, onCancel: () -> Unit, onDismiss: () -> Unit) {
    download ?: return
    Spacer(Modifier.height(10.dp))
    if (download.running) {
        Text(
            "Lade ${download.doneCount + 1} von ${download.totalCount}: ${download.currentTitle}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        RetroProgress(download.progress)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${megabytes(download.bytesDone)} von ${megabytes(download.bytesTotal)}",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("Abbrechen") }
        }
    } else if (download.error != null) {
        Text("Fehler: ${download.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

@Composable
private fun MoodPack(
    mood: Mood,
    tracks: List<LibraryTrack>,
    installed: Set<String>,
    expanded: Boolean,
    playingId: String?,
    busy: Boolean,
    onToggle: () -> Unit,
    onDownload: (List<String>) -> Unit,
    onPreview: (LibraryTrack) -> Unit,
    onStop: () -> Unit,
    onDelete: (LibraryTrack) -> Unit,
) {
    val have = tracks.count { it.id in installed }
    val missing = tracks.filter { it.id !in installed }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, bottom = 10.dp)
            .block(MaterialTheme.colorScheme.surfaceContainerLow, shadow = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelBadge(mood.label, moodColor(mood), onMoodColor(mood))
                    Spacer(Modifier.width(8.dp))
                    Text("$have/${tracks.size} geladen", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(4.dp))
                Text(mood.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Zuklappen" else "Aufklappen")
        }
        if (missing.isNotEmpty() && !busy) {
            RetroButton(
                onClick = { onDownload(missing.map { it.id }) },
                style = RetroButtonStyle.SECONDARY,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
            ) { Text("${missing.size} Stücke laden (${megabytes(MusicCatalog.totalBytes(missing))})") }
        }
        if (expanded) {
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
            tracks.forEach { t ->
                TrackRow(
                    track = t,
                    mood = mood,
                    installed = t.id in installed,
                    playing = t.id == playingId,
                    busy = busy,
                    onDownload = { onDownload(listOf(t.id)) },
                    onPreview = { onPreview(t) },
                    onStop = onStop,
                    onDelete = { onDelete(t) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: LibraryTrack,
    mood: Mood,
    installed: Boolean,
    playing: Boolean,
    busy: Boolean,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(track.composer, track.year?.toString(), track.collection.title).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${formatClock(track.seconds * 1000L)} · ${megabytes(track.bytes)} · ${track.license.label}" +
                    (track.moods.filter { it != mood }.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " · auch ") { it.label } ?: ""),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        when {
            installed && playing -> IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, contentDescription = "Hörprobe stoppen") }
            installed -> IconButton(onClick = onPreview) { Icon(Icons.Filled.PlayArrow, contentDescription = "Hörprobe") }
            !busy -> IconButton(onClick = onDownload) { Icon(Icons.Filled.Download, contentDescription = "Laden") }
        }
        if (installed) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
    }
}

@Composable
private fun Credits() {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Alle Stücke stammen unverändert aus den offenen Spiele-Projekten unten, jeweils in einer festen " +
                "Version, und werden beim Laden per Prüfsumme kontrolliert. Komponisten und Lizenzen stehen in den " +
                "Credits der Projekte und in den Dateien selbst.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MusicCollection.entries.forEach { c ->
            val tracks = MusicCatalog.tracks.filter { it.collection == c }
            Spacer(Modifier.height(12.dp))
            Text(c.title, fontFamily = PixelFont, fontSize = 12.sp)
            Text(c.note, style = MaterialTheme.typography.bodySmall)
            Text(
                c.repository.removePrefix("https://"),
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uri.openUri(c.repository) }.padding(vertical = 2.dp),
            )
            tracks.groupBy { it.composer }.forEach { (composer, list) ->
                Text(
                    "$composer: " + list.joinToString(", ") { it.title } + " (" + list.map { it.license.label }.distinct().joinToString(", ") + ")",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Lizenztexte", fontFamily = PixelFont, fontSize = 12.sp)
        MusicLicense.entries.forEach { l ->
            Text(
                l.label,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uri.openUri(l.url) }.padding(vertical = 3.dp),
            )
        }
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.GERMANY, "%.1f MB", bytes / 1_000_000.0)
