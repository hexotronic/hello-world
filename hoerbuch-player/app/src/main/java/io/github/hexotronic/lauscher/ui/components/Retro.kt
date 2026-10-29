@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.hexotronic.lauscher.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hexotronic.lauscher.ui.theme.LocalRetro
import io.github.hexotronic.lauscher.ui.theme.PixelFont
import kotlin.math.abs
import kotlin.math.floor

/** Hard, offset drop shadow drawn behind the element (no blur – it's retro). */
fun Modifier.hardShadow(color: Color, offset: Dp): Modifier =
    if (offset <= 0.dp) this else drawBehind {
        val o = offset.toPx()
        drawRect(color, Offset(o, o), size)
    }

/** Square block: background, ink outline and hard shadow. */
@Composable
fun Modifier.block(
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadow: Dp = 4.dp,
    border: Dp = 2.dp,
): Modifier {
    val retro = LocalRetro.current
    return this
        .hardShadow(retro.shadow, shadow)
        .background(background)
        .border(border, retro.ink)
}

/**
 * Clickable block that sinks into its shadow while pressed.
 */
@Composable
fun RetroBox(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shadow: Dp = 4.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shift = if (pressed && onClick != null && enabled) shadow else 0.dp
    Box(
        modifier
            .offset(shift, shift)
            .block(background, shadow - shift)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

enum class RetroButtonStyle { PRIMARY, SECONDARY, PLAIN }

@Composable
fun RetroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: RetroButtonStyle = RetroButtonStyle.PRIMARY,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (style) {
        RetroButtonStyle.PRIMARY -> colors.primary to colors.onPrimary
        RetroButtonStyle.SECONDARY -> colors.secondaryContainer to colors.onSecondaryContainer
        RetroButtonStyle.PLAIN -> colors.surfaceContainerLow to colors.onSurface
    }
    RetroBox(
        modifier = modifier,
        background = if (enabled) bg else colors.surfaceContainerHigh,
        shadow = if (enabled) 3.dp else 0.dp,
        enabled = enabled,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) fg else colors.onSurfaceVariant) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    Modifier.padding(PaddingValues(horizontal = 16.dp, vertical = 10.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

@Composable
fun RetroIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    RetroBox(
        modifier = modifier.size(size),
        background = if (filled) colors.primary else colors.surfaceContainerLow,
        shadow = if (size >= 64.dp) 5.dp else 3.dp,
        enabled = enabled,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (filled) colors.onPrimary else colors.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Progress bar made of blocks, like an old loading screen. */
@Composable
fun RetroProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val retro = LocalRetro.current
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.fillMaxWidth().height(height)) {
        val border = 1.5.dp.toPx()
        drawRect(track)
        val inner = size.height - 2 * border - 2 * border
        val gap = 2.dp.toPx()
        val segment = (inner * 0.8f).coerceAtLeast(3.dp.toPx())
        val usable = size.width - 2 * border - gap
        val count = floor(usable / (segment + gap)).toInt().coerceAtLeast(1)
        val step = usable / count
        val filled = (progress.coerceIn(0f, 1f) * count).let { if (progress > 0f && it < 1f) 1 else it.toInt() }
        for (i in 0 until filled) {
            drawRect(
                fill,
                Offset(border + gap + i * step, border + gap),
                Size(step - gap, size.height - 2 * (border + gap)),
            )
        }
        drawInkFrame(retro.ink, border * 2)
    }
}

/** Outline that stays completely inside the drawing area. */
fun DrawScope.drawInkFrame(color: Color, width: Float) {
    drawRect(color, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
}

/** Block toggle switch with a square knob. */
@Composable
fun RetroToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val retro = LocalRetro.current
    val colors = MaterialTheme.colorScheme
    val knobOffset by animateDpAsState(if (checked) 22.dp else 0.dp, label = "knob")
    Box(
        Modifier
            .size(width = 48.dp, height = 26.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .background(if (checked && enabled) colors.secondary else colors.surfaceContainerHighest)
            .border(2.dp, if (enabled) retro.ink else colors.outlineVariant),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = knobOffset)
                .size(20.dp)
                .background(if (checked) colors.surfaceContainerLowest else colors.onSurfaceVariant)
                .border(2.dp, if (enabled) retro.ink else colors.outlineVariant),
        )
    }
}

/** Material slider with a block thumb and a chunky segmented track. */
@Composable
fun RetroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val retro = LocalRetro.current
    val colors = MaterialTheme.colorScheme
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(width = 16.dp, height = 26.dp)
                    .hardShadow(retro.shadow, if (enabled) 2.dp else 0.dp)
                    .background(if (enabled) colors.tertiary else colors.surfaceContainerHighest)
                    .border(2.dp, if (enabled) retro.ink else colors.outlineVariant),
            )
        },
        track = { state: SliderState ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            // A centred range (e.g. balance) fills from the middle.
            val centered = state.valueRange.start < 0f && state.valueRange.endInclusive > 0f
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val b = 2.dp.toPx()
                drawRect(colors.surfaceContainerHighest)
                val fillColor = if (enabled) colors.primary else colors.outlineVariant
                if (centered) {
                    val mid = size.width * (-state.valueRange.start / span)
                    val x = size.width * fraction
                    drawRect(fillColor, Offset(minOf(mid, x), 0f), Size(abs(x - mid), size.height))
                } else {
                    drawRect(fillColor, Offset.Zero, Size(size.width * fraction, size.height))
                }
                // Tick marks every 10 % give it the look of an old fader.
                for (i in 1 until 10) {
                    val x = size.width * i / 10f
                    drawRect(retro.ink.copy(alpha = 0.35f), Offset(x - 0.5.dp.toPx(), size.height * 0.55f), Size(1.dp.toPx(), size.height * 0.45f))
                }
                drawInkFrame(if (enabled) retro.ink else colors.outlineVariant, b)
            }
        },
    )
}

/** Top app bar with a thick ink rule underneath. */
@Composable
fun RetroTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
) {
    Column(modifier) {
        TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions, colors = colors)
        HorizontalDivider(thickness = 2.dp, color = LocalRetro.current.ink)
    }
}

data class RetroTab(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

/** Bottom navigation as a row of blocks; the selected block is inverted. */
@Composable
fun RetroTabBar(tabs: List<RetroTab>) {
    val retro = LocalRetro.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.surfaceContainer)) {
        HorizontalDivider(thickness = 2.dp, color = retro.ink)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            tabs.forEach { tab ->
                Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    RetroBox(
                        modifier = Modifier.fillMaxWidth(),
                        background = if (tab.selected) colors.primary else colors.surfaceContainerLow,
                        shadow = if (tab.selected) 0.dp else 3.dp,
                        onClick = tab.onClick,
                        contentAlignment = Alignment.Center,
                    ) {
                        val fg = if (tab.selected) colors.onPrimary else colors.onSurface
                        Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(tab.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                            Text(
                                tab.label.uppercase(),
                                color = fg,
                                fontFamily = PixelFont,
                                fontSize = 10.sp,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small pixel-font badge, e.g. "NEU" or "FERTIG". */
@Composable
fun PixelBadge(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(background)
            .border(1.5.dp, LocalRetro.current.ink)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(text.uppercase(), color = foreground, fontFamily = PixelFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
