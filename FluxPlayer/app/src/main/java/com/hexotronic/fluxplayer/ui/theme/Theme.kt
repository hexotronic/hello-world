package com.hexotronic.fluxplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF7A3D)
val AccentDim = Color(0xFF8A3A14)
val Teal = Color(0xFF3DD9C1)

private val Scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A0A02),
    primaryContainer = AccentDim,
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Teal,
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF0F4A41),
    onSecondaryContainer = Color(0xFFB9FFF1),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFE7E7EA),
    surface = Color(0xFF15171B),
    onSurface = Color(0xFFE7E7EA),
    surfaceVariant = Color(0xFF23262C),
    onSurfaceVariant = Color(0xFFB9BCC4),
    surfaceContainer = Color(0xFF1B1D22),
    surfaceContainerHigh = Color(0xFF22252B),
    surfaceContainerHighest = Color(0xFF2A2D34),
    surfaceContainerLow = Color(0xFF17191D),
    outline = Color(0xFF4A4E57),
    outlineVariant = Color(0xFF33363D),
    error = Color(0xFFFF6B6B)
)

@Composable
fun FluxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
