package io.github.hexotronic.lauscher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.music.synth.Mood
import io.github.hexotronic.lauscher.ui.theme.LocalRetro

/** Earth-tone colour per mood, used on the mood map and in badges. */
fun moodColor(mood: Mood?): Color = when (mood) {
    Mood.CALM -> Color(0xFF3F5E5A)
    Mood.TENSE -> Color(0xFFC8932F)
    Mood.SAD -> Color(0xFF4A6178)
    Mood.HAPPY -> Color(0xFFE3B652)
    Mood.MYSTERIOUS -> Color(0xFF6B4E71)
    Mood.ACTION -> Color(0xFF8C3B22)
    Mood.ROMANTIC -> Color(0xFFC0786A)
    Mood.EERIE -> Color(0xFF3B3530)
    Mood.IDYLLIC -> Color(0xFF6B7B34)
    Mood.NEUTRAL -> Color(0xFFB8A27E)
    null -> Color.Transparent
}

/** Text colour that stays readable on [moodColor]. */
fun onMoodColor(mood: Mood?): Color = when (mood) {
    Mood.HAPPY, Mood.TENSE, Mood.NEUTRAL, null -> Color(0xFF2E2118)
    else -> Color(0xFFF7F0DF)
}

/**
 * A stretch of the book ([fromMs]..[untilMs], by default all of it) as a bar of coloured blocks:
 * one block per scene, as high as its tension; unanalysed parts stay empty, scenes without music
 * are hatched. Thin marks at the top are chapter starts, the bold line is the listening position,
 * [highlight] is outlined (the zoomed stretch or a chosen range) and [selectedMs] gets a pointer.
 * [onTap] gets the tapped time, [onSceneClick] the tapped scene, [onDrag] the dragged distance in ms.
 */
@Composable
fun MoodMap(
    scenes: List<Scene>,
    durationMs: Long,
    positionMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    fromMs: Long = 0L,
    untilMs: Long = durationMs,
    chapterStarts: List<Long> = emptyList(),
    highlight: LongRange? = null,
    selectedMs: Long? = null,
    onTap: ((Long) -> Unit)? = null,
    onDrag: ((Long) -> Unit)? = null,
    onSceneClick: (Scene) -> Unit = {},
) {
    val retro = LocalRetro.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val hatch = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.primary
    val chapterMark = retro.ink.copy(alpha = 0.55f)
    val currentScenes = rememberUpdatedState(scenes)
    val range = rememberUpdatedState(fromMs to untilMs)
    val currentClick = rememberUpdatedState(onSceneClick)
    val currentTap = rememberUpdatedState(onTap)
    val currentDrag = rememberUpdatedState(onDrag)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val (a, b) = range.value
                    if (b <= a) return@detectTapGestures
                    val ms = a + (pos.x / size.width * (b - a)).toLong()
                    currentTap.value?.invoke(ms)
                    currentScenes.value.firstOrNull { ms >= it.startMs && ms < it.endMs }?.let { currentClick.value(it) }
                }
            }
            .then(
                // Only a map that can be moved takes horizontal drags (the others leave them to the page).
                if (onDrag == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dx ->
                            val drag = currentDrag.value ?: return@detectHorizontalDragGestures
                            val (a, b) = range.value
                            if (b <= a) return@detectHorizontalDragGestures
                            change.consume()
                            drag((dx / size.width * (b - a)).toLong())
                        }
                    }
                },
            ),
    ) {
        drawRect(empty)
        val span = (untilMs - fromMs).toFloat()
        if (span > 0f) {
            fun x(ms: Long) = size.width * ((ms - fromMs) / span)
            for (scene in scenes) {
                if (scene.endMs <= fromMs || scene.startMs >= untilMs) continue
                val x0 = x(maxOf(scene.startMs, fromMs))
                val x1 = x(minOf(scene.endMs, untilMs))
                val w = (x1 - x0).coerceAtLeast(1f)
                if (scene.mood == null) {
                    // Hatched: analysed, but deliberately without music.
                    var hx = x0
                    while (hx < x1) {
                        drawRect(hatch, Offset(hx, 0f), Size(1.5f, size.height))
                        hx += 5f
                    }
                } else {
                    val top = size.height * (0.45f - 0.15f * scene.level.coerceIn(1, 3))
                    drawRect(moodColor(scene.mood), Offset(x0, top), Size(w, size.height - top))
                }
            }
            for (c in chapterStarts) {
                if (c <= fromMs || c >= untilMs) continue
                drawRect(chapterMark, Offset(x(c) - 0.5.dp.toPx(), 0f), Size(1.dp.toPx(), size.height * 0.3f))
            }
            highlight?.let { h ->
                val a = x(h.first.coerceIn(fromMs, untilMs))
                val b = x(h.last.coerceIn(fromMs, untilMs))
                drawRect(retro.ink, Offset(a, 1.dp.toPx()), Size((b - a).coerceAtLeast(2f), size.height - 2.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
            if (positionMs in fromMs..untilMs) {
                drawRect(marker, Offset(x(positionMs) - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
            }
            selectedMs?.takeIf { it in fromMs..untilMs }?.let { sel ->
                val sx = x(sel)
                val tip = 7.dp.toPx()
                val path = Path().apply {
                    moveTo(sx - tip, size.height)
                    lineTo(sx + tip, size.height)
                    lineTo(sx, size.height - tip * 1.3f)
                    close()
                }
                drawPath(path, retro.ink)
                drawRect(retro.ink, Offset(sx - 0.75.dp.toPx(), 0f), Size(1.5.dp.toPx(), size.height))
            }
        }
        drawInkFrame(retro.ink, 2.dp.toPx())
    }
}
