@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.BookCover
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.hardShadow
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman

@Composable
fun BookDetailScreen(vm: MainViewModel, bookId: Long) {
    val detailsFlow = remember(bookId) { vm.repository.observeBook(bookId) }
    val bookmarksFlow = remember(bookId) { vm.repository.observeBookmarks(bookId) }
    val details by detailsFlow.collectAsStateWithLifecycle(null)
    val bookmarks by bookmarksFlow.collectAsStateWithLifecycle(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val book = details?.book

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && book != null) vm.setCover(book, uri)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            navigationIcon = { IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") } },
            title = { Text(book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            actions = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mehr") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (book != null) {
                        DropdownMenuItem(text = { Text("Infos bearbeiten") }, onClick = {
                            menu = false
                            editInfo = true
                        })
                        DropdownMenuItem(text = { Text("Cover ändern") }, onClick = {
                            menu = false
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        })
                        DropdownMenuItem(text = { Text(if (book.finished) "Als ungehört markieren" else "Als beendet markieren") }, onClick = {
                            menu = false
                            vm.setFinished(book, !book.finished)
                        })
                        DropdownMenuItem(text = { Text("Fortschritt zurücksetzen") }, onClick = {
                            menu = false
                            vm.resetProgress(book)
                        })
                        DropdownMenuItem(text = { Text("Aus Bibliothek entfernen") }, onClick = {
                            menu = false
                            confirmDelete = true
                        })
                    }
                }
            },
        )
        val d = details
        if (book != null && d != null) {
            BookDetailBody(vm, d, bookmarks, tab) { tab = it }
        }
    }

    if (editInfo && book != null) {
        EditInfoDialog(book, onDismiss = { editInfo = false }) { title, author, narrator ->
            vm.updateInfo(book, title, author, narrator)
            editInfo = false
        }
    }
    if (confirmDelete && book != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hörbuch entfernen?") },
            text = {
                Text(
                    "„${book.title}“ wird mit Fortschritt und Lesezeichen aus der Bibliothek entfernt. " +
                        "Die Dateien bleiben erhalten; beim nächsten Einlesen taucht das Buch wieder auf.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteBook(book)
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun EditInfoDialog(book: Book, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author.orEmpty()) }
    var narrator by remember { mutableStateOf(book.narrator.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Infos bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titel") }, singleLine = true)
                OutlinedTextField(value = author, onValueChange = { author = it }, label = { Text("Autor") }, singleLine = true)
                OutlinedTextField(value = narrator, onValueChange = { narrator = it }, label = { Text("Sprecher") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, author, narrator) }) { Text("Speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun BookDetailBody(
    vm: MainViewModel,
    d: io.github.hexotronic.lauscher.data.BookDetails,
    bookmarks: List<io.github.hexotronic.lauscher.data.Bookmark>,
    tab: Int,
    onTab: (Int) -> Unit,
) {
    val book = d.book
    Column {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            BookCover(book.coverPath, book.title, Modifier.size(132.dp).hardShadow(LocalRetro.current.shadow, 5.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                book.narrator?.let {
                    Text("Gelesen von $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(book.year, formatHuman(book.durationMs), "${d.chapters.size} Kapitel", "${book.fileCount} Dateien")
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (book.audioSettingsJson != null) {
                    Text(
                        "Eigene Klangeinstellungen",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RetroProgress(book.progress)
                Text(
                    if (book.finished) "Beendet" else "${formatClock(book.positionMs)} von ${formatClock(book.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RetroButton(onClick = { vm.playBook(book.id) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(if (book.isStarted) "Weiterhören" else "Abspielen")
            }
        }
        book.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
        }

        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            Tab(selected = tab == 0, onClick = { onTab(0) }, text = { Text("Kapitel") })
            Tab(selected = tab == 1, onClick = { onTab(1) }, text = { Text("Lesezeichen (${bookmarks.size})") })
            Tab(selected = tab == 2, onClick = { onTab(2) }, text = { Text("Dateien") })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            when (tab) {
                0 -> items(d.chapters) { ch ->
                    val current = book.positionMs >= ch.startMs && book.positionMs < ch.endMs && !book.finished
                    ListItem(
                        headlineContent = { Text(ch.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${formatClock(ch.startMs)} · ${formatClock(ch.durationMs)}") },
                        leadingContent = {
                            Text(
                                "%02d".format(ch.index + 1),
                                fontFamily = PixelFont,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { vm.playBook(book.id, startAtMs = ch.startMs) },
                    )
                }
                1 -> items(bookmarks, key = { it.id }) { mark ->
                    ListItem(
                        headlineContent = { Text(mark.title) },
                        supportingContent = { if (mark.note.isNotBlank()) Text(mark.note) },
                        leadingContent = { Text(formatClock(mark.positionMs)) },
                        modifier = Modifier.clickable { vm.playBook(book.id, startAtMs = mark.positionMs) },
                    )
                }
                else -> items(d.tracks) { t ->
                    ListItem(
                        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(formatClock(t.durationMs)) },
                        leadingContent = { Text("${t.index + 1}") },
                        modifier = Modifier.clickable { vm.playBook(book.id, startAtMs = t.startMs) },
                    )
                }
            }
        }
    }
}
