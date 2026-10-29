@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.data.Stats
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.EmptyState
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.RetroTopBar
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.theme.MonoFont
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.StatTile
import io.github.hexotronic.lauscher.ui.formatHours
import io.github.hexotronic.lauscher.ui.formatHuman
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun StatsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    Column(modifier) {
        RetroTopBar(title = { Text("Statistik") })
        val s = stats
        if (s == null || s.totalMs == 0L) {
            EmptyState(
                "Noch keine Hörzeit",
                "Sobald du hörst, siehst du hier deine Hörzeit pro Tag, deine Serie und deine meistgehörten Bücher.",
                Modifier.fillMaxSize(),
            )
        } else {
            StatsContent(s)
        }
    }
}

@Composable
private fun StatsContent(stats: Stats) {
    val today = LocalDate.now()
    val byDay = stats.days.associate { LocalDate.parse(it.day) to it.listenedMs }
    val todayMs = byDay[today] ?: 0L
    val weekMs = (0L until 7L).sumOf { byDay[today.minusDays(it)] ?: 0L }
    var streak = 0
    var day = if ((byDay[today] ?: 0L) > 0) today else today.minusDays(1)
    while ((byDay[day] ?: 0L) > 60_000) {
        streak++
        day = day.minusDays(1)
    }
    var range by remember { mutableIntStateOf(7) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Heute", formatHuman(todayMs), Modifier.weight(1f))
            StatTile("7 Tage", formatHours(weekMs), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Gesamt", formatHours(stats.totalMs), Modifier.weight(1f))
            StatTile("Serie", if (streak == 1) "1 Tag" else "$streak Tage", Modifier.weight(1f))
            StatTile("Beendet", "${stats.finishedBooks}", Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                .block(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row {
                    Text("Hörzeit pro Tag", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(7, 30, 90).forEachIndexed { i, days ->
                        SegmentedButton(
                            selected = range == days,
                            onClick = { range = days },
                            shape = SegmentedButtonDefaults.itemShape(i, 3, baseShape = RoundedCornerShape(0.dp)),
                        ) { Text("$days Tage") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val values = (range - 1 downTo 0).map { offset ->
                    val d = today.minusDays(offset.toLong())
                    d to (byDay[d] ?: 0L)
                }
                DailyChart(values)
                val active = values.count { it.second > 0 }
                val avg = if (active > 0) values.sumOf { it.second } / active else 0L
                Text(
                    "An $active von $range Tagen gehört · Ø ${formatHuman(avg)} pro Hörtag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SectionHeader("Meistgehört")
        val top = stats.perBook.take(15)
        val maxMs = top.maxOfOrNull { it.listenedMs } ?: 1L
        top.forEach { b ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row {
                    Text(
                        b.title.ifBlank { "Unbekannt" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatHuman(b.listenedMs), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                RetroProgress(b.listenedMs.toFloat() / maxMs, height = 10.dp)
            }
        }
    }
}

/** Column chart: one series, thin columns with rounded tops, tap a day for its exact value. */
@Composable
private fun DailyChart(values: List<Pair<LocalDate, Long>>) {
    val measurer = rememberTextMeasurer()
    var selected by remember(values.size) { mutableIntStateOf(values.lastIndex) }
    val barColor = MaterialTheme.colorScheme.primary
    val dimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = LocalRetro.current.ink
    val maxMs = max(values.maxOf { it.second }, 60_000L)
    // Round the axis up to whole quarter hours (or hours above 2 h).
    val stepMs = if (maxMs > 2 * 3_600_000L) 3_600_000L else 15 * 60_000L
    val axisMax = ceil(maxMs.toDouble() / stepMs).toLong() * stepMs
    val dateFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .pointerInput(values) {
                detectTapGestures { pos ->
                    val left = 44.dp.toPx()
                    val slot = (size.width - left) / values.size
                    selected = ((pos.x - left) / slot).toInt().coerceIn(0, values.lastIndex)
                }
            },
    ) {
        val left = 44.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val plotH = bottom - top
        val slot = (size.width - left) / values.size
        val barW = (slot * 0.62f).coerceAtMost(24.dp.toPx()).coerceAtLeast(2.dp.toPx())
        fun y(ms: Long) = bottom - plotH * (ms.toFloat() / axisMax)

        for (tick in listOf(0L, axisMax / 2, axisMax)) {
            drawLine(grid, Offset(left, y(tick)), Offset(size.width, y(tick)), strokeWidth = 1f)
            val t = measurer.measure(axisLabel(tick), TextStyle(fontSize = 10.sp, color = label, fontFamily = MonoFont))
            drawText(t, topLeft = Offset(0f, y(tick) - t.size.height / 2f))
        }
        values.forEachIndexed { i, (date, ms) ->
            val x = left + i * slot + (slot - barW) / 2
            if (ms > 0) {
                // Square blocks fit the retro look; the selected day gets an ink outline.
                drawRect(if (i == selected) barColor else dimColor, Offset(x, y(ms)), Size(barW, bottom - y(ms)))
                if (i == selected) {
                    val w = 2.dp.toPx()
                    drawRect(ink, Offset(x + w / 2, y(ms) + w / 2), Size(barW - w, bottom - y(ms) - w), style = Stroke(w))
                }
            }
            val showLabel = values.size <= 7 || i == values.lastIndex || (values.size - 1 - i) % (values.size / 6) == 0
            if (showLabel) {
                val text = if (values.size <= 7) {
                    date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.GERMANY)
                } else {
                    "${date.dayOfMonth}.${date.monthValue}."
                }
                val t = measurer.measure(text, TextStyle(fontSize = 9.sp, color = label, fontFamily = PixelFont))
                drawText(t, topLeft = Offset(left + i * slot + slot / 2 - t.size.width / 2f, size.height - t.size.height))
            }
        }
        values.getOrNull(selected)?.let { (date, ms) ->
            val t = measurer.measure(
                "${date.format(dateFormat)}: ${if (ms > 0) formatHuman(ms) else "nicht gehört"}",
                TextStyle(fontSize = 12.sp, color = ink, fontFamily = MonoFont, fontWeight = FontWeight.Bold),
            )
            drawText(t, topLeft = Offset(left, 0f))
        }
    }
}

private fun axisLabel(ms: Long): String = when {
    ms == 0L -> "0"
    ms % 3_600_000L == 0L -> "${ms / 3_600_000L} h"
    ms >= 3_600_000L -> String.format(Locale.GERMANY, "%.1f h", ms / 3_600_000.0)
    else -> "${ms / 60_000L} min"
}
