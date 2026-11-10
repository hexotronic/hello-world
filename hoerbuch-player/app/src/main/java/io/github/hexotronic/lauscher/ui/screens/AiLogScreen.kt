@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.core.AiGroup
import io.github.hexotronic.lauscher.analysis.core.AiLogEntry
import io.github.hexotronic.lauscher.analysis.core.AiLogText
import io.github.hexotronic.lauscher.analysis.core.AiStep
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.Screen
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import java.util.TimeZone

private fun zoneOffset(): Long = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()

@Composable
private fun stepColor(step: AiStep): Color {
    val c = MaterialTheme.colorScheme
    return when (step.group) {
        AiGroup.LISTEN -> c.tertiary
        AiGroup.MODEL -> c.primary
        AiGroup.SCENES -> c.secondary
        AiGroup.MUSIC -> c.onSurface
        AiGroup.PROBLEMS -> c.error
        AiGroup.OTHER -> c.onSurfaceVariant
    }
}

private val Mono = FontFamily.Monospace

/**
 * A small live text window: the last steps of the AI (for [bookId], or all), newest at the
 * bottom, scrolling along as new ones arrive. "Ganzes Protokoll" opens the full log.
 */
@Composable
fun AiLogWindow(vm: MainViewModel, bookId: Long?, modifier: Modifier = Modifier) {
    val all by vm.aiLog.collectAsStateWithLifecycle()
    val entries = remember(all, bookId) { all.filter { bookId == null || it.bookId == null || it.bookId == bookId }.takeLast(WINDOW_LINES) }
    val offset = remember { zoneOffset() }
    val scroll = rememberScrollState()
    var following by remember { mutableStateOf(true) }
    // Follow the newest line, unless the listener scrolled up to read.
    LaunchedEffect(entries.lastOrNull()?.id) {
        val wasAtEnd = following || scroll.maxValue - scroll.value < FOLLOW_SLACK_PX
        withFrameNanos { } // the new line is laid out now
        if (wasAtEnd) scroll.animateScrollTo(scroll.maxValue)
        following = false
    }
    Column(modifier.padding(horizontal = 16.dp)) {
        Row {
            Text("Was die KI gerade macht", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(top = 12.dp))
            TextButton(onClick = { vm.navigate(Screen.AiLog) }) { Text("Ganzes Protokoll") }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .border(2.dp, LocalRetro.current.ink)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .verticalScroll(scroll)
                .padding(8.dp),
        ) {
            if (entries.isEmpty()) {
                Text(
                    "Noch nichts. Sobald die KI zuhört, das Sprachmodell fragt oder Musik auswählt, steht es hier – Zeile für Zeile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            entries.forEach { e ->
                Text(
                    AiLogText.line(e, offset),
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = stepColor(e.step),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

/**
 * The whole AI log: filter by kind, only the current book or all, and each entry opens to show
 * what exactly was heard, asked and answered. Everything can be copied as text.
 */
@Composable
fun AiLogScreen(vm: MainViewModel) {
    val all by vm.aiLog.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    var groups by remember { mutableStateOf(AiGroup.entries.map { it.name }.toSet()) }
    var onlyBook by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf(setOf<Long>()) }
    val bookId = np.book?.id
    val entries = remember(all, groups, onlyBook, bookId) {
        all.filter { it.step.group.name in groups && (!onlyBook || bookId == null || it.bookId == null || it.bookId == bookId) }
    }
    val offset = remember { zoneOffset() }
    val clipboard = LocalClipboardManager.current
    val list = rememberLazyListState()
    var first by remember { mutableStateOf(true) }
    // Opens at the newest entry and follows new ones while the end is in view.
    LaunchedEffect(entries.lastOrNull()?.id) {
        val info = list.layoutInfo
        val nearEnd = (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        if (entries.isNotEmpty() && (first || nearEnd)) list.scrollToItem(entries.size)
        first = false
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        RetroTopBar(
            title = { Text("KI-Protokoll") },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Hier steht, was die KI auf dem Handy tut: was die Spracherkennung gehört hat, was das Sprachmodell " +
                            "gefragt wurde und geantwortet hat, welche Szenen daraus wurden und warum welche Musik spielt. " +
                            "Tippe auf einen Eintrag für den ganzen Text. Das Protokoll bleibt nur im Speicher (die letzten 400 Schritte).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiGroup.entries.forEach { g ->
                            val on = g.name in groups
                            FilterChip(
                                selected = on,
                                onClick = { groups = if (on) groups - g.name else groups + g.name },
                                label = { Text("${g.label} (${all.count { it.step.group == g }})") },
                            )
                        }
                    }
                }
                SwitchRow("Nur das aktuelle Hörbuch", np.book?.title, onlyBook, enabled = bookId != null) { onlyBook = it }
                SwitchRow("Fragen und Antworten ganz zeigen", "Was gehört, gefragt und geantwortet wurde, direkt unter jeder Zeile", showDetails) { showDetails = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    RetroButton(onClick = { clipboard.setText(AnnotatedString(AiLogText.export(entries, offset))) }, style = RetroButtonStyle.SECONDARY) {
                        Text("Kopieren")
                    }
                    RetroButton(onClick = { vm.clearAiLog() }, style = RetroButtonStyle.PLAIN) { Text("Leeren") }
                }
                if (entries.isEmpty()) {
                    Text(
                        "Noch keine Einträge. Spiel ein Buch mit Szenen-Musik ab oder starte eine Analyse.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(entries, key = { it.id }) { e ->
                LogEntry(e, offset, expanded = showDetails || e.id in open) {
                    open = if (e.id in open) open - e.id else open + e.id
                }
            }
        }
    }
}

@Composable
private fun LogEntry(e: AiLogEntry, offset: Long, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = e.detail != null, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row {
            Text(
                AiLogText.timeOfDay(e.timeMs, offset) + " ",
                fontFamily = Mono,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                e.step.label.uppercase() + (e.positionMs?.let { " @${io.github.hexotronic.lauscher.analysis.core.ScenePrompt.clock(it)}" } ?: ""),
                fontFamily = Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = stepColor(e.step),
            )
            if (e.detail != null) {
                Text(if (expanded) "  ▾" else "  ▸", fontFamily = Mono, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(e.text, style = MaterialTheme.typography.bodySmall)
        if (expanded && e.detail != null) {
            SelectionContainer {
                Text(
                    e.detail,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .border(1.dp, LocalRetro.current.ink)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(6.dp),
                )
            }
        }
    }
}

private const val WINDOW_LINES = 60
private const val FOLLOW_SLACK_PX = 120
