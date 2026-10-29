package io.github.hexotronic.lauscher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import io.github.hexotronic.lauscher.ui.components.RetroIconButton
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTab
import io.github.hexotronic.lauscher.ui.components.RetroTabBar
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.screens.BookDetailScreen
import io.github.hexotronic.lauscher.ui.screens.LibraryScreen
import io.github.hexotronic.lauscher.ui.screens.MusicLibraryScreen
import io.github.hexotronic.lauscher.ui.screens.MusicScreen
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
        Screen.Music -> FullScreen(snackbar) { MusicScreen(vm) }
        Screen.MusicLibrary -> FullScreen(snackbar) { MusicLibraryScreen(vm) }
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
                RetroTabBar(
                    listOf(
                        RetroTab("Bücher", Icons.AutoMirrored.Filled.LibraryBooks, screen == Screen.Library || screen is Screen.BookDetail) {
                            vm.navigate(Screen.Library)
                        },
                        RetroTab("Statistik", Icons.Filled.BarChart, screen == Screen.Stats) { vm.navigate(Screen.Stats) },
                        RetroTab("Optionen", Icons.Filled.Settings, screen == Screen.Settings) { vm.navigate(Screen.Settings) },
                    ),
                )
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
    val retro = LocalRetro.current
    Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        HorizontalDivider(thickness = 2.dp, color = retro.ink)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { if (loaded) vm.navigate(Screen.Player) else vm.playBook(book.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book.coverPath, book.title, Modifier.size(46.dp))
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
                Spacer(Modifier.height(4.dp))
                RetroProgress(progress, height = 7.dp)
            }
            Spacer(Modifier.width(8.dp))
            if (loaded) {
                RetroIconButton(Icons.Filled.Replay, "Zurückspulen", { vm.withCore { it.rewind() } }, size = 40.dp, iconSize = 22.dp)
                Spacer(Modifier.width(8.dp))
            }
            RetroIconButton(
                if (np.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (np.playWhenReady) "Pause" else "Abspielen",
                { vm.togglePlayPause() },
                size = 44.dp,
                iconSize = 26.dp,
                filled = true,
            )
        }
    }
}
