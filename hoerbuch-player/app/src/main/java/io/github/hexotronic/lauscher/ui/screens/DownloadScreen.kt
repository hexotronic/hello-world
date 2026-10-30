@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.hexotronic.lauscher.download.DownloadJob
import io.github.hexotronic.lauscher.download.JobState
import io.github.hexotronic.lauscher.download.PHASE_DOWNLOAD
import io.github.hexotronic.lauscher.download.Source
import io.github.hexotronic.lauscher.download.core.ArchiveOrg
import io.github.hexotronic.lauscher.download.core.Grouping
import io.github.hexotronic.lauscher.download.core.LinkInput
import io.github.hexotronic.lauscher.download.core.MediaItem
import io.github.hexotronic.lauscher.download.core.Mp3Quality
import io.github.hexotronic.lauscher.download.core.OutputFormat
import io.github.hexotronic.lauscher.ui.DownloadBrowser
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.download.core.TransferEstimate
import io.github.hexotronic.lauscher.ui.formatHuman
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import io.github.hexotronic.lauscher.ui.formatClock

/** Free audiobooks (LibriVox), videos as audio via search or link, and converting files on the phone. */
@Composable
fun DownloadScreen(vm: MainViewModel) {
    val d = vm.downloads
    val jobs by d.jobs.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> d.convertFiles(uris) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) d.chooseFolder(uri) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
            title = { Text("Herunterladen") },
        )
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadBrowser.Tab.entries.forEach { t ->
                        FilterChip(selected = d.tab == t, onClick = { d.tab = t }, label = { Text(t.label) })
                    }
                }
            }
            item {
                // Where new downloads go; each book gets its own subfolder there.
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Speichern in", style = MaterialTheme.typography.labelMedium)
                        Text(d.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { folderPicker.launch(null) }) { Text("Ordner wählen") }
                    if (d.customFolder) TextButton(onClick = d::useDefaultFolder) { Text("Standard") }
                }
            }
            if (jobs.isNotEmpty()) {
                item { SectionHeader("Aufträge") }
                val open = jobs.count { it.active || it.state == JobState.PAUSED }
                if (open > 1) {
                    item {
                        Row(Modifier.padding(horizontal = 8.dp)) {
                            if (jobs.any { it.active }) TextButton(onClick = d::pauseAll) { Text("Alle pausieren") }
                            if (jobs.any { it.state == JobState.PAUSED }) TextButton(onClick = d::resumeAll) { Text("Alle fortsetzen") }
                        }
                    }
                }
                items(jobs.asReversed(), key = { "job-${it.id}" }) { job ->
                    JobCard(
                        job,
                        onPause = { d.pause(job.id) },
                        onResume = { d.resume(job.id) },
                        onCancel = { d.cancel(job.id) },
                        onOpen = { id -> vm.navigate(Screen.BookDetail(id)) },
                    )
                }
                if (jobs.any { it.finished }) {
                    item {
                        TextButton(onClick = d::clearFinished, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Erledigte entfernen") }
                    }
                }
            }
            when (d.tab) {
                DownloadBrowser.Tab.BOOKS -> booksTab(d)
                DownloadBrowser.Tab.MEDIA -> mediaTab(d)
                DownloadBrowser.Tab.FILES -> item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Hint(
                            "Videos oder Tondateien vom Handy in MP3 umwandeln – oder nur die Tonspur herauslösen. " +
                                "Mehrere Dateien können ein Hörbuch mit Kapiteln werden.",
                        )
                        DownloadOptions(d, grouping = true)
                        Spacer(Modifier.height(12.dp))
                        RetroButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }) { Text("Dateien auswählen …") }
                    }
                }
            }
            item {
                Text(
                    "Tipp: Wählst du als Speicherort deinen Hörbuch-Ordner (oder einen Ordner darin), erscheinen " +
                        "Downloads dort wie alle anderen Bücher – ohne doppelte Einträge in der Bibliothek.\n\n" +
                        "Lade nur herunter, was du darfst: gemeinfreie oder frei lizenzierte Inhalte (z. B. LibriVox, " +
                        "Creative Commons), eigene Aufnahmen oder Inhalte mit Erlaubnis der Rechteinhaber. Manche " +
                        "Plattformen, etwa YouTube, untersagen das Herunterladen in ihren Nutzungsbedingungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private fun LazyListScope.booksTab(d: DownloadBrowser) {
    item {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Hint(
                "Gemeinfreie Hörbücher von LibriVox – von Freiwilligen eingelesen, kostenlos und legal. " +
                    "Es wird das ganze Buch mit allen Kapiteln geladen.",
            )
            OutlinedTextField(
                value = d.bookQuery,
                onValueChange = { d.bookQuery = it },
                label = { Text("Titel oder Autor (leer: die beliebtesten)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { d.searchBooks() }),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArchiveOrg.Language.entries.forEach { lang ->
                    FilterChip(selected = d.bookLanguage == lang, onClick = { d.bookLanguage = lang }, label = { Text(lang.label) })
                }
            }
            RetroButton(onClick = { d.searchBooks() }, enabled = !d.bookLoading) { Text(if (d.bookLoading) "Suche …" else "Suchen") }
            d.bookError?.let { ErrorText(it) }
            if (d.bookTotal >= 0) {
                Text(
                    "${d.bookTotal} Hörbücher gefunden",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
        }
    }
    items(d.bookResults, key = { "book-${it.id}" }) { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
                .block(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.author, item.language, "${item.downloads} Downloads").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.width(10.dp))
            RetroButton(onClick = { d.downloadBook(item) }) { Text("Laden") }
        }
    }
    if (d.bookResults.isNotEmpty() && d.bookResults.size < d.bookTotal) {
        item {
            RetroButton(
                onClick = { d.searchBooks(more = true) },
                enabled = !d.bookLoading,
                style = RetroButtonStyle.PLAIN,
                modifier = Modifier.padding(16.dp),
            ) { Text("Mehr anzeigen") }
        }
    }
}

private fun LazyListScope.mediaTab(d: DownloadBrowser) {
    item {
        val clipboard = LocalClipboardManager.current
        Column(Modifier.padding(horizontal = 16.dp)) {
            Hint(
                "Suchen oder Links einfügen – einzelne Videos, ganze Playlists oder Kanäle. Gespeichert wird nur der " +
                    "Ton, als MP3 oder im Originalformat. Mehrere Links auf einmal gehen auch.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Source.entries.forEach { s ->
                    FilterChip(selected = d.source == s, onClick = { d.source = s }, label = { Text(s.label) })
                }
            }
            OutlinedTextField(
                value = d.mediaInput,
                onValueChange = { d.mediaInput = it },
                label = { Text("Suchbegriff oder Link(s)") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { d.go() }),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val isLink = LinkInput.firstUrl(d.mediaInput) != null
                RetroButton(onClick = d::go, enabled = !d.mediaLoading && d.mediaInput.isNotBlank()) {
                    Text(if (d.mediaLoading) "Lädt …" else if (isLink) "Link öffnen" else "Suchen")
                }
                RetroButton(
                    onClick = { clipboard.getText()?.text?.let { d.mediaInput = it.trim() } },
                    style = RetroButtonStyle.PLAIN,
                ) { Text("Einfügen") }
            }
            d.mediaError?.let { ErrorText(it) }
            if (d.mediaItems.isNotEmpty()) {
                d.mediaTitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
                FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${d.selected.size} von ${d.mediaItems.size} ausgewählt",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                    TextButton(onClick = d::selectAll) { Text("Alle") }
                    TextButton(onClick = d::selectNone) { Text("Keine") }
                    if (d.hasMore) TextButton(onClick = d::loadAll, enabled = !d.mediaLoading) { Text("Alle Seiten laden") }
                }
            }
        }
    }
    items(d.mediaItems, key = { "media-${it.url}" }) { item ->
        MediaRow(item, checked = item.url in d.selected, onToggle = { d.toggle(item) })
    }
    if (d.hasMore) {
        item {
            RetroButton(onClick = d::loadMore, enabled = !d.mediaLoading, style = RetroButtonStyle.PLAIN, modifier = Modifier.padding(16.dp)) {
                Text("Mehr laden")
            }
        }
    }
    if (d.mediaItems.isNotEmpty()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                DownloadOptions(d, grouping = d.selected.size > 1)
                Spacer(Modifier.height(12.dp))
                RetroButton(onClick = d::downloadSelected, enabled = d.selected.isNotEmpty()) {
                    Text(if (d.selected.size == 1) "1 herunterladen" else "${d.selected.size} herunterladen")
                }
            }
        }
    }
}

@Composable
private fun MediaRow(item: MediaItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        if (item.thumbnail != null) {
            AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 72.dp, height = 40.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.uploader, item.durationSeconds.takeIf { it > 0 }?.let { formatClock(it * 1000) }).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Format, MP3 quality and (for several items) how they become books. */
@Composable
private fun DownloadOptions(d: DownloadBrowser, grouping: Boolean) {
    Text("Format", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutputFormat.entries.forEach { f -> FilterChip(selected = d.format == f, onClick = { d.format = f }, label = { Text(f.label) }) }
    }
    if (d.format == OutputFormat.MP3) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mp3Quality.entries.forEach { q -> FilterChip(selected = d.quality == q, onClick = { d.quality = q }, label = { Text(q.label) }) }
        }
        if (d.quality.kbps > 160) {
            Text(
                "Mehr als die Quelle hergibt, klingt nicht besser: YouTube liefert Ton mit etwa 130–160 kbit/s, " +
                    "SoundCloud und Bandcamp mit 128 kbit/s. 320 kbit/s macht die Datei nur größer – volle " +
                    "Qualität ohne Umwandlung gibt es mit „Original“.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
    if (grouping) {
        Text("Mehrere Dateien", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Grouping.entries.forEach { g -> FilterChip(selected = d.grouping == g, onClick = { d.grouping = g }, label = { Text(g.label) }) }
        }
    }
}

@Composable
private fun JobCard(job: DownloadJob, onPause: () -> Unit, onResume: () -> Unit, onCancel: () -> Unit, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)
            .block(colors.surfaceContainerLow)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(job.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            val (label, bg, fg) = when (job.state) {
                JobState.QUEUED -> Triple("Wartet", colors.surfaceContainerHighest, colors.onSurface)
                JobState.RUNNING -> Triple("Lädt", colors.primary, colors.onPrimary)
                JobState.PAUSED -> Triple("Pausiert", colors.secondaryContainer, colors.onSecondaryContainer)
                JobState.DONE -> Triple("Fertig", colors.tertiary, colors.onTertiary)
                JobState.FAILED -> Triple("Fehler", colors.error, colors.onError)
                JobState.CANCELLED -> Triple("Abgebrochen", colors.surfaceContainerHighest, colors.onSurface)
            }
            PixelBadge(label, bg, fg)
        }
        Text(job.detail, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        val paused = job.state == JobState.PAUSED
        if (job.active || paused) {
            Spacer(Modifier.height(8.dp))
            RetroProgress(job.overall, Modifier.fillMaxWidth())
            job.current?.let {
                Text(
                    (if (job.total > 1) "${minOf(job.done + 1, job.total)} von ${job.total}: " else "") + it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Network data: how much has arrived, how fast, over which connection.
            val running = job.state == JobState.RUNNING
            val data = listOfNotNull(
                job.phase?.takeIf { running && it != PHASE_DOWNLOAD }?.replaceFirstChar { it.uppercase() },
                when {
                    job.bytesTotal > 0 -> "${TransferEstimate.size(job.bytes)} von ${TransferEstimate.size(job.bytesTotal)}"
                    job.bytes > 0 -> "${TransferEstimate.size(job.bytes)} geladen"
                    else -> null
                },
                job.bytesPerSecond.takeIf { running && job.phase == PHASE_DOWNLOAD && it > 0 }?.let { "${TransferEstimate.size(it)}/s" },
                job.network?.takeIf { running && job.phase == PHASE_DOWNLOAD }?.let { if (it == "offline") "keine Verbindung" else "über $it" },
            ).joinToString(" · ")
            if (data.isNotEmpty()) Text(data, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
            if (running) {
                job.remainingMs?.let { ms ->
                    val end = LocalTime.now().plusSeconds(ms / 1000).format(DateTimeFormatter.ofPattern("HH:mm"))
                    Text(
                        "Fertig ca. $end Uhr (noch ${formatHuman(ms)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
            }
            if (paused) {
                Text("Pausiert – Fertiges und Angefangenes bleiben erhalten", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            Row {
                if (paused) {
                    TextButton(onClick = onResume) { Text("Fortsetzen") }
                } else {
                    TextButton(onClick = onPause) { Text("Pausieren") }
                }
                TextButton(onClick = onCancel) { Text("Abbrechen") }
            }
        }
        job.message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.state == JobState.FAILED) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (job.state == JobState.DONE && job.bookIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            RetroButton(onClick = { onOpen(job.bookIds.first()) }, style = RetroButtonStyle.SECONDARY) {
                Text(if (job.bookIds.size == 1) "Zum Hörbuch" else "Zum ersten von ${job.bookIds.size} Hörbüchern")
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
}
