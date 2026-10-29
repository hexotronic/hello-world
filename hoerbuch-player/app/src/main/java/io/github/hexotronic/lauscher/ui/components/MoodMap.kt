package io.github.hexotronic.lauscher.ui.components

import androidx.compose.foundation.Canvas
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
 * The whole book as one bar of coloured blocks: one block per scene, unanalysed parts stay
 * empty, scenes without music are hatched. Tap a scene to edit it.
 */
@Composable
fun MoodMap(
    scenes: List<Scene>,
    durationMs: Long,
    positionMs: Long,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    onSceneClick: (Scene) -> Unit = {},
) {
    val retro = LocalRetro.current
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val hatch = MaterialTheme.colorScheme.outlineVariant
    val marker = MaterialTheme.colorScheme.primary
    val currentScenes = rememberUpdatedState(scenes)
    val currentDuration = rememberUpdatedState(durationMs)
    val currentClick = rememberUpdatedState(onSceneClick)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val total = currentDuration.value
                    if (total <= 0) return@detectTapGestures
                    val ms = (pos.x / size.width * total).toLong()
                    currentScenes.value.firstOrNull { ms >= it.startMs && ms < it.endMs }?.let { currentClick.value(it) }
                }
            },
    ) {
        drawRect(empty)
        if (durationMs > 0) {
            for (scene in scenes) {
                val x0 = size.width * scene.startMs / durationMs
                val x1 = size.width * scene.endMs / durationMs
                val w = (x1 - x0).coerceAtLeast(1f)
                if (scene.mood == null) {
                    // Hatched: analysed, but deliberately without music.
                    var x = x0
                    while (x < x1) {
                        drawRect(hatch, Offset(x, 0f), Size(1.5f, size.height))
                        x += 5f
                    }
                } else {
                    val top = size.height * (0.45f - 0.15f * scene.level.coerceIn(1, 3))
                    drawRect(moodColor(scene.mood), Offset(x0, top), Size(w, size.height - top))
                }
            }
            val px = size.width * positionMs / durationMs
            drawRect(marker, Offset(px - 2.dp.toPx(), 0f), Size(4.dp.toPx(), size.height))
        }
        drawInkFrame(retro.ink, 2.dp.toPx())
    }
}
