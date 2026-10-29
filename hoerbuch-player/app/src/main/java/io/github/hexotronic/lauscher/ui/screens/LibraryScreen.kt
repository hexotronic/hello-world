@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.hexotronic.lauscher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.data.LibraryFilter
import io.github.hexotronic.lauscher.data.LibrarySort
import io.github.hexotronic.lauscher.scan.ScanState
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.BookCover
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroBox
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroIconButton
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.hardShadow
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.components.EmptyState
import io.github.hexotronic.lauscher.ui.formatHuman

@Composable
fun LibraryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val books by vm.libraryBooks.collectAsStateWithLifecycle()
    val all by vm.allBooks.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val scan by vm.scanState.collectAsStateWithLifecycle()
    val query by vm.searchQuery.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var menuBook by remember { mutableStateOf<Book?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.addFolder(uri)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }

    Column(modifier) {
        RetroTopBar(
            title = {
                if (searching) {
                    TextField(
                        value = query,
                        onValueChange = { vm.searchQuery.value = it },
                        placeholder = { Text("Titel, Autor, Sprecher …") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Bibliothek")
                }
            },
            actions = {
                IconButton(onClick = {
                    if (searching) vm.searchQuery.value = ""
                    searching = !searching
                }) {
                    Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Suchen")
                }
                IconButton(onClick = { vm.updateSettings { it.copy(libraryGrid = !it.libraryGrid) } }) {
                    Icon(
                        if (settings.libraryGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Ansicht wechseln",
                    )
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sortieren")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        LibrarySort.entries.forEach { sort ->
                            DropdownMenuItem(
                                text = { Text(sort.label) },
                                onClick = {
                                    vm.updateSettings { it.copy(librarySort = sort) }
                                    sortMenu = false
                                },
                                trailingIcon = {
                                    if (sort == settings.librarySort) Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { addMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Hinzufügen")
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Ordner hinzufügen") },
                            leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                pickFolder.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Einzelne Dateien hinzufügen") },
                            leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                pickFiles.launch(arrayOf("audio/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Bibliothek neu einlesen") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = {
                                addMenu = false
                                vm.rescan()
                            },
                        )
                    }
                }
            },
        )

        val running = scan as? ScanState.Running
        if (running != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    if (running.total > 0) "Lese ${running.current} (${running.done + 1}/${running.total})" else running.current,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                if (running.total > 0) {
                    RetroProgress(running.done.toFloat() / running.total)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (all.isEmpty() && folders.isEmpty() && running == null) {
            EmptyState(
                title = "Willkommen bei Lauscher",
                text = "Wähle den Ordner, in dem deine Hörbücher liegen. Jeder Unterordner wird zu einem Hörbuch, " +
                    ".m4b-Dateien mit Kapiteln werden automatisch erkannt.",
                modifier = Modifier.fillMaxSize(),
            ) {
                RetroButton(onClick = { pickFolder.launch(null) }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Hörbuch-Ordner auswählen")
                }
                Spacer(Modifier.height(16.dp))
                RetroButton(onClick = { pickFiles.launch(arrayOf("audio/*")) }, style = RetroButtonStyle.PLAIN) {
                    Text("Einzelne Dateien wählen")
                }
            }
        } else {
            LibraryContent(vm, books, all, query, running != null) { menuBook = it }
        }
        menuBook?.let { book -> BookActionsSheet(vm, book) { menuBook = null } }
    }
}

@Composable
private fun LibraryContent(
    vm: MainViewModel,
    books: List<Book>,
    all: List<Book>,
    query: String,
    refreshing: Boolean,
    onMenu: (Book) -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    run {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.rescan() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val continueBook = all.filter { it.isStarted }.maxByOrNull { it.lastPlayedAt }
            val showHero = continueBook != null && query.isBlank() && settings.libraryFilter == LibraryFilter.ALL
            LazyVerticalGrid(
                columns = if (settings.libraryGrid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(if (settings.libraryGrid) 16.dp else 4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FilterRow(all, settings.libraryFilter) { f -> vm.updateSettings { it.copy(libraryFilter = f) } }
                }
                if (showHero && continueBook != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueCard(continueBook) { vm.playBook(continueBook.id) }
                    }
                }
                if (books.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (query.isNotBlank()) "Nichts gefunden für „$query“" else "Keine Hörbücher in dieser Ansicht",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                }
                items(books, key = { it.id }) { book ->
                    if (settings.libraryGrid) {
                        BookGridItem(book, onClick = { vm.playBook(book.id) }, onLongClick = { onMenu(book) })
                    } else {
                        BookListItem(book, onClick = { vm.playBook(book.id) }, onLongClick = { onMenu(book) })
                    }
                }
            }
        }
    }
}

/** Long-press actions for a book. */
@Composable
fun BookActionsSheet(vm: MainViewModel, book: Book, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverPath, book.title, Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                book.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        fun act(block: () -> Unit) {
            onDismiss()
            block()
        }
        ListItem(
            headlineContent = { Text("Abspielen") },
            leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
            modifier = Modifier.clickable { act { vm.playBook(book.id) } },
        )
        ListItem(
            headlineContent = { Text("Details, Kapitel & Lesezeichen") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.clickable { act { vm.navigate(Screen.BookDetail(book.id)) } },
        )
        ListItem(
            headlineContent = { Text(if (book.finished) "Als ungehört markieren" else "Als beendet markieren") },
            leadingContent = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
            modifier = Modifier.clickable { act { vm.setFinished(book, !book.finished) } },
        )
        ListItem(
            headlineContent = { Text("Fortschritt zurücksetzen") },
            leadingContent = { Icon(Icons.Filled.Restore, contentDescription = null) },
            modifier = Modifier.clickable { act { vm.resetProgress(book) } },
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterRow(books: List<Book>, selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        items(LibraryFilter.entries) { f ->
            val count = when (f) {
                LibraryFilter.ALL -> books.size
                LibraryFilter.IN_PROGRESS -> books.count { it.isStarted }
                LibraryFilter.NEW -> books.count { !it.isStarted && !it.finished }
                LibraryFilter.FINISHED -> books.count { it.finished }
            }
            FilterChip(selected = f == selected, onClick = { onSelect(f) }, label = { Text("${f.label} ($count)") })
        }
    }
}

@Composable
private fun ContinueCard(book: Book, onPlay: () -> Unit) {
    RetroBox(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, end = 4.dp),
        background = MaterialTheme.colorScheme.primaryContainer,
        onClick = onPlay,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(book.coverPath, book.title, Modifier.size(84.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                PixelBadge("Weiterhören", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                Spacer(Modifier.height(4.dp))
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                RetroProgress(book.progress)
                Text(
                    "noch ${formatHuman(book.remainingMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(10.dp))
            RetroIconButton(Icons.Filled.PlayArrow, "Abspielen", onPlay, size = 52.dp, iconSize = 30.dp, filled = true)
        }
    }
}

@Composable
private fun BookGridItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shadow = LocalRetro.current.shadow
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box {
            BookCover(
                book.coverPath,
                book.title,
                Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp).aspectRatio(1f).hardShadow(shadow, 4.dp),
            )
            when {
                book.finished -> PixelBadge(
                    "Fertig",
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.onSecondary,
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                )
                !book.isStarted -> PixelBadge(
                    "Neu",
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.onTertiary,
                    Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
        }
        if (book.isStarted) {
            RetroProgress(book.progress, Modifier.padding(top = 6.dp, end = 4.dp), height = 8.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            book.author ?: "Unbekannt",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            statusText(book),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookListItem(book: Book, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book.coverPath, book.title, Modifier.size(64.dp).hardShadow(LocalRetro.current.shadow, 3.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(book.author, book.narrator?.let { "gelesen von $it" }).joinToString(" · ").ifEmpty { "Unbekannt" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (book.isStarted) RetroProgress(book.progress, height = 8.dp)
            Text(statusText(book), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun statusText(book: Book): String = when {
    book.finished -> "Beendet"
    book.isStarted -> "${(book.progress * 100).toInt()} % · noch ${formatHuman(book.remainingMs)}"
    else -> "Neu · ${formatHuman(book.durationMs)}"
}
