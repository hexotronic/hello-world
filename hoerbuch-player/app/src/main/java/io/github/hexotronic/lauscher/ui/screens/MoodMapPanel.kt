@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.Timeline
import io.github.hexotronic.lauscher.data.Chapter
import io.github.hexotronic.lauscher.ui.components.MoodMap
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.moodColor
import io.github.hexotronic.lauscher.ui.components.onMoodColor
import io.github.hexotronic.lauscher.ui.formatHuman
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import kotlin.math.abs
import kotlin.math.roundToInt

/** How much of the book the magnifier shows. */
private enum class Zoom(val label: String, val spanMs: Long?) {
    MIN_10("10 Min.", 10 * 60_000L),
    MIN_30("30 Min.", 30 * 60_000L),
    HOUR("1 Std.", 60 * 60_000L),
    CHAPTER("Kapitel", null),
}

/**
 * The mood map of a book: the whole book with chapter marks, below it a magnifier around the
 * listening position (or wherever the listener taps or drags it) with a time axis, and a card
 * about the tapped spot – what the AI found there, jump there, correct it or analyse from there.
 */
@Composable
fun MoodMapPanel(
    scenes: List<Scene>,
    chapters: List<Chapter>,
    durationMs: Long,
    positionMs: Long,
    onJump: (Long) -> Unit,
    onEdit: (Scene) -> Unit,
    onAnalyzeFrom: (Long) -> Unit,
) {
    if (durationMs <= 0) return
    val chapterStarts = remember(chapters) { chapters.map { it.startMs }.sorted() }
    var zoom by rememberSaveable { mutableStateOf(Zoom.MIN_30) }
    // The magnifier follows the listening position until the listener moves it.
    var following by rememberSaveable { mutableStateOf(true) }
    var center by rememberSaveable { mutableLongStateOf(positionMs) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(positionMs, following) { if (following) center = positionMs }

    val chapterAtCenter = chapters.lastOrNull { it.startMs <= center } ?: chapters.firstOrNull()
    val window = when (val span = zoom.spanMs) {
        null -> chapterAtCenter?.let { it.startMs..maxOf(it.endMs, it.startMs + 1) } ?: Timeline.window(center, 30 * 60_000L, durationMs)
        else -> Timeline.window(center, span, durationMs)
    }

    Column {
        // The whole book.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ganzes Buch", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                "${(Timeline.coverage(scenes, 0, durationMs) * 100).roundToInt()} % analysiert",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        MoodMap(
            scenes,
            durationMs,
            positionMs,
            height = 34.dp,
            chapterStarts = chapterStarts,
            highlight = window,
            selectedMs = selected,
            onTap = { ms ->
                following = false
                center = ms
                selected = ms
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(Timeline.clock(durationMs), style = MaterialTheme.typography.labelSmall)
        }

        // The magnifier.
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lupe", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (!following || abs(center - positionMs) > 1000) {
                TextButton(onClick = {
                    following = true
                    selected = null
                }) { Text("Zur Hörstelle") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Zoom.entries.filter { it != Zoom.CHAPTER || chapters.size > 1 }.forEach { z ->
                FilterChip(selected = zoom == z, onClick = { zoom = z }, label = { Text(z.label) })
            }
        }
        MoodMap(
            scenes,
            durationMs,
            positionMs,
            height = 64.dp,
            fromMs = window.first,
            untilMs = window.last,
            chapterStarts = chapterStarts,
            selectedMs = selected,
            onTap = { selected = it },
            onDrag = { deltaMs ->
                following = false
                center = (center - deltaMs).coerceIn(0, durationMs)
            },
        )
        TimeAxis(window.first, window.last)
        chapterAtCenter?.takeIf { chapters.size > 1 }?.let {
            Text(
                "Kapitel: ${it.title}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "Tippen wählt eine Stelle, Ziehen verschiebt die Lupe. Höhe der Blöcke = Spannung, schraffiert = bewusst ohne Musik, leer = noch nicht analysiert.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        val spot = selected ?: positionMs
        SpotCard(
            spot = spot,
            isPosition = selected == null,
            scene = SceneMerger.sceneAt(scenes, spot),
            nextGap = Timeline.firstGap(scenes, spot, durationMs),
            onJump = onJump,
            onEdit = onEdit,
            onAnalyzeFrom = onAnalyzeFrom,
        )
        MoodShares(scenes)
    }
}

/** Round times under the magnifier, each at its place. */
@Composable
private fun TimeAxis(fromMs: Long, untilMs: Long) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
        val ticks = Timeline.ticks(fromMs, untilMs, 4)
        val width = maxWidth
        for (t in ticks) {
            val frac = (t - fromMs).toFloat() / (untilMs - fromMs).coerceAtLeast(1)
            // Labels near the right edge are moved left so they stay readable.
            val x = (width * frac - 14.dp).coerceAtMost(width - 44.dp).coerceAtLeast(0.dp)
            Text(Timeline.clock(t), fontSize = 10.sp, modifier = Modifier.offset(x = x))
        }
    }
}

/** What the AI found at [spot], with what can be done there. */
@Composable
private fun SpotCard(
    spot: Long,
    isPosition: Boolean,
    scene: Scene?,
    nextGap: Long?,
    onJump: (Long) -> Unit,
    onEdit: (Scene) -> Unit,
    onAnalyzeFrom: (Long) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(2.dp, LocalRetro.current.ink)
            .background(colors.surfaceContainerLowest)
            .padding(10.dp),
    ) {
        Text(
            (if (isPosition) "Hörstelle " else "Gewählt: ") + Timeline.clock(spot) +
                (scene?.let { " · Szene ${Timeline.clock(it.startMs)}–${Timeline.clock(it.endMs)}" } ?: ""),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        if (scene == null) {
            Text("Hier ist noch nichts analysiert.", style = MaterialTheme.typography.bodySmall)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val mood = scene.mood
                if (mood != null) {
                    PixelBadge(mood.label, moodColor(mood), onMoodColor(mood))
                    val level = scene.level.coerceIn(1, 3)
                    PixelBadge("Spannung " + "●".repeat(level) + "○".repeat(3 - level), colors.surfaceContainerHighest, colors.onSurface)
                } else {
                    PixelBadge("ohne Musik", colors.surfaceContainerHighest, colors.onSurface)
                }
                scene.setting?.let { PixelBadge(it.label, colors.surfaceContainerHighest, colors.onSurface) }
                if (mood != null) {
                    scene.cues.forEach { PixelBadge(it.label, colors.secondaryContainer, colors.onSecondaryContainer) }
                    scene.pace?.let { PixelBadge("Tempo ${it.label}", colors.secondaryContainer, colors.onSecondaryContainer) }
                }
                if (scene.manual) PixelBadge("von dir", colors.primary, colors.onPrimary)
            }
            if (scene.title.isNotBlank()) {
                Text("„${scene.title}“", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        nextGap?.let {
            Text(
                if (it <= spot + 1000) "Ab hier fehlt die Analyse." else "Analysiert bis ${Timeline.clock(it)} (${formatHuman(it - spot)} ab hier).",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isPosition) RetroButton(onClick = { onJump(spot) }, style = RetroButtonStyle.SECONDARY) { Text("Hierhin springen") }
            if (scene != null) RetroButton(onClick = { onEdit(scene) }, style = RetroButtonStyle.PLAIN) { Text("Korrigieren") }
            RetroButton(onClick = { onAnalyzeFrom(nextGap?.takeIf { scene != null } ?: spot) }, style = RetroButtonStyle.PLAIN) {
                Text(if (scene != null && nextGap != null) "Lücke danach analysieren …" else "Ab hier analysieren …")
            }
        }
    }
}

/** The moods of the analysed part with their shares. */
@Composable
private fun MoodShares(scenes: List<Scene>) {
    val shares = SceneMerger.shares(scenes) { it.mood }
    if (shares.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        shares.forEach { (m, share) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(moodColor(m)).border(1.dp, LocalRetro.current.ink))
                Spacer(Modifier.width(4.dp))
                Text("${m.label} ${(share * 100).roundToInt().coerceAtLeast(1)} %", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
