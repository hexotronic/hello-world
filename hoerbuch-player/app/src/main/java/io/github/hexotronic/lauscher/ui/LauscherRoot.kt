package io.github.hexotronic.lauscher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.ui.components.BookCover
import io.github.hexotronic.lauscher.ui.screens.BookDetailScreen
import io.github.hexotronic.lauscher.ui.screens.LibraryScreen
import io.github.hexotronic.lauscher.ui.screens.PlayerScreen
import io.github.hexotronic.lauscher.ui.screens.SettingsScreen
import io.github.hexotronic.lauscher.ui.screens.SoundStudioScreen
import io.github.hexotronic.lauscher.ui.screens.StatsScreen

@Composable
fun LauscherRoot(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val screen = vm.current
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    when (screen) {
        Screen.Player -> FullScreen(snackbar) { PlayerScreen(vm) }
        Screen.SoundStudio -> FullScreen(snackbar) { SoundStudioScreen(vm) }
        is Screen.BookDetail -> TabScaffold(vm, snackbar) { BookDetailScreen(vm, screen.bookId) }
        else -> TabScaffold(vm, snackbar) {
            when (screen) {
                Screen.Stats -> StatsScreen(vm, Modifier.fillMaxSize())
                Screen.Settings -> SettingsScreen(vm, Modifier.fillMaxSize())
                else -> LibraryScreen(vm, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun FullScreen(snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    // Screens draw edge to edge; their top app bars handle the status bar inset.
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { _ ->
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun TabScaffold(vm: MainViewModel, snackbar: SnackbarHostState, content: @Composable () -> Unit) {
    val screen = vm.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                MiniPlayer(vm)
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.Library || screen is Screen.BookDetail,
                        onClick = { vm.navigate(Screen.Library) },
                        icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                        label = { Text("Bibliothek") },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Stats,
                        onClick = { vm.navigate(Screen.Stats) },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                        label = { Text("Statistik") },
                    )
                    NavigationBarItem(
                        selected = screen == Screen.Settings,
                        onClick = { vm.navigate(Screen.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Einstellungen") },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) { content() }
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel) {
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val last by vm.lastBook.collectAsStateWithLifecycle()
    val book = np.book ?: last ?: return
    val loaded = np.book != null
    val progress = if (loaded) {
        if (np.durationMs > 0) np.positionMs.toFloat() / np.durationMs else 0f
    } else {
        book.progress
    }
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { if (loaded) vm.navigate(Screen.Player) else vm.playBook(book.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(book.coverPath, book.title, Modifier.size(44.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        np.chapter?.title?.takeIf { loaded && np.chapters.size > 1 }
                            ?: if (loaded) "noch ${formatHuman((np.remainingMs / np.speed).toLong())}" else "Tippen zum Weiterhören",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (loaded) {
                    IconButton(onClick = { vm.withCore { it.rewind() } }) {
                        Icon(Icons.Filled.Replay, contentDescription = "Zurückspulen")
                    }
                }
                IconButton(onClick = { vm.togglePlayPause() }) {
                    Icon(
                        if (np.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (np.playWhenReady) "Pause" else "Abspielen",
                    )
                }
            }
        }
    }
}
