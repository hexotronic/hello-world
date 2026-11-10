@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.hexotronic.lauscher.analysis.core.RangeEnd
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.core.Timeline
import io.github.hexotronic.lauscher.data.Chapter
import io.github.hexotronic.lauscher.music.MusicSettings
import io.github.hexotronic.lauscher.ui.components.MoodMap
import io.github.hexotronic.lauscher.ui.formatHuman
import kotlin.math.roundToInt

/** Where an analysis started by the listener begins. */
private enum class StartAt(val label: String) {
    POSITION("Meine Stelle"),
    GAP("Erste Lücke"),
    BEGINNING("Anfang"),
    CHAPTER("Kapitel …"),
    TIME("Zeitpunkt …"),
}

/**
 * "Analysieren": choose where to start (my position, the first part not analysed yet, the
 * beginning, a chapter or any time – also by tapping the map) and how far (15 minutes, an hour,
 * to the end of the chapter or of the book). Shows how much of it is done already and about how
 * long the phone will compute.
 */
@Composable
fun AnalyzeRangeDialog(
    scenes: List<Scene>,
    chapters: List<Chapter>,
    durationMs: Long,
    positionMs: Long,
    settings: MusicSettings,
    presetFromMs: Long? = null,
    onDismiss: () -> Unit,
    onStart: (fromMs: Long, untilMs: Long) -> Unit,
) {
    val firstGap = remember(scenes, positionMs) { Timeline.firstGap(scenes, positionMs, durationMs) }
    var mode by rememberSaveable { mutableStateOf(if (presetFromMs != null) StartAt.TIME else StartAt.POSITION) }
    var time by rememberSaveable { mutableLongStateOf(presetFromMs ?: positionMs) }
    var chapterIndex by rememberSaveable(chapters.size) { mutableStateOf(chapters.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)) }
    var end by rememberSaveable { mutableStateOf(RangeEnd.END) }
    val chapterStarts = remember(chapters) { chapters.map { it.startMs }.sorted() }

    val from = when (mode) {
        StartAt.POSITION -> positionMs
        StartAt.GAP -> firstGap ?: positionMs
        StartAt.BEGINNING -> 0L
        StartAt.CHAPTER -> chapters.getOrNull(chapterIndex)?.startMs ?: 0L
        StartAt.TIME -> time
    }.coerceIn(0L, durationMs)
    val until = Timeline.rangeEnd(end, from, chapterStarts, durationMs)
    val done = Timeline.coverage(scenes, from, until)
    val plan = settings.samplingPlan
    val speech = if (settings.measuredSpeechFactor > 0) settings.measuredSpeechFactor else MusicSettings.DEFAULT_SPEECH_FACTOR
    val model = if (settings.measuredModelSeconds > 0) settings.measuredModelSeconds else MusicSettings.DEFAULT_MODEL_SECONDS
    val computeMs = (plan.secondsPerHour(speech, model) * (until - from) / 3_600_000f * (1f - done) * 1000).toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Analysieren") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Ab wo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StartAt.entries.forEach { m ->
                        if (m == StartAt.GAP && firstGap == null) return@forEach
                        if (m == StartAt.CHAPTER && chapters.size < 2) return@forEach
                        val label = when (m) {
                            StartAt.POSITION -> "${m.label} (${Timeline.clock(positionMs)})"
                            StartAt.GAP -> "${m.label} (${Timeline.clock(firstGap ?: 0)})"
                            else -> m.label
                        }
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(label) })
                    }
                }
                when (mode) {
                    StartAt.CHAPTER -> Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        chapters.forEachIndexed { i, c ->
                            val cover = Timeline.coverage(scenes, c.startMs, c.endMs)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (i == chapterIndex) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable { chapterIndex = i }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    Timeline.clock(c.startMs) + " · " + if (cover >= 0.995f) "fertig" else "${(cover * 100).roundToInt()} %",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    StartAt.TIME -> Column {
                        Text(Timeline.clock(time), style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = time.toFloat(),
                            onValueChange = { time = it.toLong() },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(-5, -1, 1, 5).forEach { m ->
                                TextButton(onClick = { time = (time + m * 60_000L).coerceIn(0L, durationMs) }) {
                                    Text((if (m > 0) "+" else "−") + "${kotlin.math.abs(m)} Min.")
                                }
                            }
                        }
                    }
                    else -> Unit
                }
                Spacer(Modifier.height(8.dp))
                MoodMap(
                    scenes,
                    durationMs,
                    positionMs,
                    height = 28.dp,
                    chapterStarts = chapterStarts,
                    highlight = from..until,
                    onTap = {
                        mode = StartAt.TIME
                        time = it
                    },
                )
                Text(
                    "Auf die Karte tippen wählt den Start.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Text("Wie weit", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RangeEnd.entries.forEach { e ->
                        if (e == RangeEnd.CHAPTER && chapters.size < 2) return@forEach
                        FilterChip(selected = end == e, onClick = { end = e }, label = { Text(e.label) })
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "${Timeline.clock(from)} – ${Timeline.clock(until)} (${formatHuman(until - from)})",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        until <= from -> "Hier gibt es nichts zu analysieren."
                        done >= 0.995f -> "Dieser Bereich ist schon ganz analysiert."
                        else -> "${(done * 100).roundToInt()} % davon schon analysiert · Rechenzeit ca. ${formatHuman(computeMs)}" +
                            if (settings.measuredSpeechFactor > 0) " (auf diesem Gerät gemessen)" else " (geschätzt)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = until > from && done < 0.995f, onClick = { onStart(from, until) }) { Text("Analysieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
