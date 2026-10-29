@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.playback.SleepMode
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.components.BookCover
import io.github.hexotronic.lauscher.ui.components.RetroBox
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroIconButton
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroSlider
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.components.hardShadow
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman
import io.github.hexotronic.lauscher.ui.formatSpeed
import kotlinx.coroutines.delay

enum class PlayerSheet { CHAPTERS, BOOKMARKS, SPEED, SLEEP }

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lastBook by vm.lastBook.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var locked by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }

    if (settings.keepScreenOn) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
    }

    val book = np.book
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            RetroTopBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Schließen")
                    }
                },
                title = { Text("Wiedergabe", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    IconButton(onClick = { vm.navigate(Screen.SoundStudio) }) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = "Klangstudio")
                    }
                    IconButton(onClick = { locked = true }, enabled = book != null) {
                        Icon(Icons.Filled.Lock, contentDescription = "Bedienung sperren")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mehr") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (book != null) {
                                DropdownMenuItem(text = { Text("Details zum Hörbuch") }, onClick = {
                                    menu = false
                                    vm.navigate(Screen.BookDetail(book.id))
                                })
                                DropdownMenuItem(text = { Text("Lesezeichen mit Notiz …") }, onClick = {
                                    menu = false
                                    noteDialog = true
                                })
                            }
                            DropdownMenuItem(
                                text = { Text(if (settings.skipSilence) "Stille nicht mehr überspringen" else "Stille überspringen") },
                                onClick = {
                                    menu = false
                                    vm.updateSettings { it.copy(skipSilence = !it.skipSilence) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (settings.keepScreenOn) "Bildschirm darf ausgehen" else "Bildschirm anlassen") },
                                onClick = {
                                    menu = false
                                    vm.updateSettings { it.copy(keepScreenOn = !it.keepScreenOn) }
                                },
                            )
                        }
                    }
                },
            )

            if (book == null) {
                NoBookLoaded(vm, lastBook?.title) { lastBook?.let { vm.playBook(it.id, openPlayer = false) } }
            } else {
                PlayerContent(vm, np, sleep.mode, sleep.remainingMs, settings.rewindSeconds, settings.forwardSeconds) { sheet = it }
            }
        }

        if (locked) LockOverlay { locked = false }
    }

    when (sheet) {
        PlayerSheet.CHAPTERS -> ChaptersSheet(np, onDismiss = { sheet = null }) { index ->
            vm.withCore { it.jumpToChapter(index) }
            sheet = null
        }
        PlayerSheet.BOOKMARKS -> BookmarksSheet(vm, np) { sheet = null }
        PlayerSheet.SPEED -> SpeedSheet(vm, np) { sheet = null }
        PlayerSheet.SLEEP -> SleepSheet(vm, np) { sheet = null }
        null -> Unit
    }
    if (noteDialog) {
        BookmarkEditDialog(title = np.chapter?.title.orEmpty(), note = "", heading = "Lesezeichen setzen", onDismiss = { noteDialog = false }) { t, n ->
            vm.addBookmark(t, n)
            noteDialog = false
        }
    }
}

@Composable
private fun NoBookLoaded(vm: MainViewModel, lastTitle: String?, onResume: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Gerade läuft nichts", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        if (lastTitle != null) {
            RetroButton(onClick = onResume) { Text("„$lastTitle“ fortsetzen") }
        } else {
            RetroButton(onClick = { vm.navigate(Screen.Library) }) { Text("Zur Bibliothek") }
        }
    }
}

@Composable
private fun PlayerContent(
    vm: MainViewModel,
    np: NowPlaying,
    sleepMode: SleepMode,
    sleepRemaining: Long,
    rewindSeconds: Int,
    forwardSeconds: Int,
    openSheet: (PlayerSheet) -> Unit,
) {
    val book = np.book ?: return
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var flashKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashKey) {
        if (seekFlash != null) {
            delay(700)
            seekFlash = null
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover: double tap left/right half to rewind/forward.
        Box(
            Modifier
                .weight(1f, fill = false)
                .widthIn(max = 420.dp)
                .aspectRatio(1f)
                .padding(vertical = 8.dp)
                .pointerInput(rewindSeconds, forwardSeconds) {
                    detectTapGestures(onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            vm.withCore { it.rewind() }
                            seekFlash = "-$rewindSeconds s"
                        } else {
                            vm.withCore { it.forward() }
                            seekFlash = "+$forwardSeconds s"
                        }
                        flashKey++
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            BookCover(
                book.coverPath,
                book.title,
                Modifier.fillMaxSize().padding(end = 6.dp, bottom = 6.dp).hardShadow(LocalRetro.current.shadow, 6.dp),
                frame = 3.dp,
            )
            seekFlash?.let { flash ->
                Box(Modifier.block(MaterialTheme.colorScheme.tertiary, shadow = 4.dp).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text(flash, color = MaterialTheme.colorScheme.onTertiary, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            book.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(book.author, book.narrator?.let { "gelesen von $it" }).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val chapter = np.chapter
        if (chapter != null && np.chapters.size > 1) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { openSheet(PlayerSheet.CHAPTERS) },
            )
        }
        np.undoPositionMs?.let { undo ->
            AssistChip(
                onClick = { vm.withCore { it.undoJump() } },
                label = { Text("Zurück zu ${formatClock(undo)}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
        np.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        // Chapter seek bar.
        var drag by remember { mutableStateOf<Float?>(null) }
        val chapterDuration = np.chapterDurationMs.coerceAtLeast(1)
        val chapterStart = chapter?.startMs ?: 0L
        val shownPos = drag?.let { (it * chapterDuration).toLong() } ?: np.chapterPositionMs
        RetroSlider(
            value = (shownPos.toFloat() / chapterDuration).coerceIn(0f, 1f),
            onValueChange = { drag = it },
            onValueChangeFinished = {
                drag?.let { f ->
                    val target = chapterStart + (f * chapterDuration).toLong()
                    vm.withCore { it.seekTo(target) }
                }
                drag = null
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { contentDescription = "Position im Kapitel" },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(shownPos), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (np.chapters.size > 1) {
                Text(
                    "Kapitel ${np.chapterIndex + 1} / ${np.chapters.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            }
            Text("-" + formatClock(chapterDuration - shownPos), style = MaterialTheme.typography.labelMedium)
        }

        // Transport controls.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RetroIconButton(Icons.Filled.SkipPrevious, "Vorheriges Kapitel", { vm.withCore { it.previousChapter() } }, size = 46.dp, iconSize = 28.dp)
            SeekButton(rewindSeconds, forward = false) { vm.withCore { it.rewind() } }
            RetroIconButton(
                icon = when {
                    np.isBuffering && np.playWhenReady -> Icons.Filled.HourglassEmpty
                    np.playWhenReady -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = if (np.playWhenReady) "Pause" else "Abspielen",
                onClick = { vm.togglePlayPause() },
                size = 80.dp,
                iconSize = 46.dp,
                filled = true,
            )
            SeekButton(forwardSeconds, forward = true) { vm.withCore { it.forward() } }
            RetroIconButton(Icons.Filled.SkipNext, "Nächstes Kapitel", { vm.withCore { it.nextChapter() } }, size = 46.dp, iconSize = 28.dp)
        }

        // Whole-book progress.
        RetroProgress(if (np.durationMs > 0) np.positionMs.toFloat() / np.durationMs else 0f, height = 8.dp)
        val remainingAtSpeed = (np.remainingMs / np.speed).toLong()
        Text(
            "${formatClock(np.positionMs)} von ${formatClock(np.durationMs)} · noch ${formatHuman(remainingAtSpeed)}" +
                if (np.speed != 1f) " bei ${formatSpeed(np.speed)}" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Secondary actions.
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { openSheet(PlayerSheet.SPEED) },
                label = { Text(formatSpeed(np.speed)) },
                leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { openSheet(PlayerSheet.SLEEP) },
                label = {
                    Text(
                        when (sleepMode) {
                            SleepMode.OFF -> "Timer"
                            SleepMode.TIMER -> formatClock(sleepRemaining)
                            SleepMode.END_OF_CHAPTER -> "Kapitelende"
                        },
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = "Schlaf-Timer",
                        Modifier.size(18.dp),
                        tint = if (sleepMode != SleepMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            Box(
                Modifier
                    .size(44.dp)
                    .combinedClickable(
                        onClick = { vm.addBookmark() },
                        onLongClick = { openSheet(PlayerSheet.BOOKMARKS) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.BookmarkBorder, contentDescription = "Lesezeichen setzen")
            }
            IconButton(onClick = { openSheet(PlayerSheet.BOOKMARKS) }) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Lesezeichen")
            }
            IconButton(onClick = { openSheet(PlayerSheet.CHAPTERS) }) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Kapitel")
            }
        }
    }
}

@Composable
private fun SeekButton(seconds: Int, forward: Boolean, onClick: () -> Unit) {
    RetroBox(
        modifier = Modifier.size(58.dp).semantics {
            contentDescription = if (forward) "$seconds Sekunden vor" else "$seconds Sekunden zurück"
        },
        background = MaterialTheme.colorScheme.secondaryContainer,
        shadow = 3.dp,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(40.dp).graphicsLayer { if (forward) scaleX = -1f },
        )
        Text(
            "$seconds",
            fontFamily = PixelFont,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .block(MaterialTheme.colorScheme.surfaceContainerHigh, shadow = 5.dp)
                .combinedClickable(onClick = {}, onLongClick = onUnlock)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.LockOpen, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("Zum Entsperren lange drücken", style = MaterialTheme.typography.labelLarge)
        }
    }
}
