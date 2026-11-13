package io.github.hexotronic.lauscher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hexotronic.lauscher.R
import io.github.hexotronic.lauscher.data.ThemeMode

/** Blocky pixel font for headings and numbers (Silkscreen, OFL). */
val PixelFont = FontFamily(
    Font(R.font.silkscreen_regular, FontWeight.Normal),
    Font(R.font.silkscreen_bold, FontWeight.Bold),
)

/** Retro monospace for everything that has to be read (Space Mono, OFL). */
val MonoFont = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Extra colours of the retro look that Material has no slot for. */
@Immutable
data class RetroColors(
    /** Outlines of all blocks. */
    val ink: Color,
    /** The hard, offset drop shadow under blocks. */
    val shadow: Color,
    /** Earth tones for generated covers and accents. */
    val earth: List<Color>,
)

val LocalRetro = staticCompositionLocalOf {
    RetroColors(Color(0xFF2E2118), Color(0xFF2E2118), EarthTones)
}

val EarthTones = listOf(
    Color(0xFFB4532A), // terracotta
    Color(0xFF6B7B34), // moss
    Color(0xFFC8932F), // ochre
    Color(0xFF6E4B2E), // umber
    Color(0xFF3F5E5A), // slate
    Color(0xFFA86B4E), // clay
    Color(0xFF4E5B2A), // olive
    Color(0xFF8C3B22), // rust
)

private val PaperColors = lightColorScheme(
    primary = Color(0xFFB4532A),
    onPrimary = Color(0xFFFFF6E8),
    primaryContainer = Color(0xFFE9B48F),
    onPrimaryContainer = Color(0xFF3A1606),
    secondary = Color(0xFF6B7B34),
    onSecondary = Color(0xFFFFF6E8),
    secondaryContainer = Color(0xFFCBD198),
    onSecondaryContainer = Color(0xFF232A08),
    tertiary = Color(0xFFC8932F),
    onTertiary = Color(0xFF2E2118),
    tertiaryContainer = Color(0xFFEDD39A),
    onTertiaryContainer = Color(0xFF2E2118),
    background = Color(0xFFEFE4CC),
    onBackground = Color(0xFF2E2118),
    surface = Color(0xFFEFE4CC),
    onSurface = Color(0xFF2E2118),
    surfaceVariant = Color(0xFFDCCBA6),
    onSurfaceVariant = Color(0xFF5E4B3A),
    surfaceContainerLowest = Color(0xFFF7F0DF),
    surfaceContainerLow = Color(0xFFEBDFC3),
    surfaceContainer = Color(0xFFE4D5B4),
    surfaceContainerHigh = Color(0xFFDCCBA6),
    surfaceContainerHighest = Color(0xFFD2BF96),
    inverseSurface = Color(0xFF2E2118),
    inverseOnSurface = Color(0xFFEFE4CC),
    outline = Color(0xFF2E2118),
    outlineVariant = Color(0xFFB8A27E),
    error = Color(0xFFA3261B),
    onError = Color(0xFFFFF6E8),
)

private val NightEarthColors = darkColorScheme(
    primary = Color(0xFFE07B4C),
    onPrimary = Color(0xFF2A1205),
    primaryContainer = Color(0xFF7A3317),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFA9B86A),
    onSecondary = Color(0xFF1E2405),
    secondaryContainer = Color(0xFF4A5520),
    onSecondaryContainer = Color(0xFFE3EBB8),
    tertiary = Color(0xFFE3B652),
    onTertiary = Color(0xFF2B1E05),
    tertiaryContainer = Color(0xFF6A4E10),
    onTertiaryContainer = Color(0xFFFFE6AE),
    background = Color(0xFF1F1812),
    onBackground = Color(0xFFF0E3C8),
    surface = Color(0xFF1F1812),
    onSurface = Color(0xFFF0E3C8),
    surfaceVariant = Color(0xFF403328),
    onSurfaceVariant = Color(0xFFC2AE8E),
    surfaceContainerLowest = Color(0xFF17110C),
    surfaceContainerLow = Color(0xFF241C15),
    surfaceContainer = Color(0xFF2B2219),
    surfaceContainerHigh = Color(0xFF352A20),
    surfaceContainerHighest = Color(0xFF403328),
    inverseSurface = Color(0xFFF0E3C8),
    inverseOnSurface = Color(0xFF1F1812),
    outline = Color(0xFFC2AE8E),
    outlineVariant = Color(0xFF5C4C3B),
    error = Color(0xFFFF8A75),
    onError = Color(0xFF3B0A04),
)

private fun Typography.retro(): Typography {
    fun pixel(style: TextStyle, size: Int) =
        style.copy(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size + 8).sp, letterSpacing = 0.5.sp)
    fun mono(style: TextStyle, size: Int, weight: FontWeight = FontWeight.Normal) =
        style.copy(fontFamily = MonoFont, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4f).sp, letterSpacing = 0.sp)
    return copy(
        displayLarge = pixel(displayLarge, 48),
        displayMedium = pixel(displayMedium, 40),
        displaySmall = pixel(displaySmall, 32),
        headlineLarge = pixel(headlineLarge, 28),
        headlineMedium = pixel(headlineMedium, 24),
        headlineSmall = pixel(headlineSmall, 20),
        titleLarge = pixel(titleLarge, 18),
        titleMedium = mono(titleMedium, 15, FontWeight.Bold),
        titleSmall = mono(titleSmall, 13, FontWeight.Bold),
        bodyLarge = mono(bodyLarge, 14),
        bodyMedium = mono(bodyMedium, 13),
        bodySmall = mono(bodySmall, 11),
        labelLarge = mono(labelLarge, 13, FontWeight.Bold),
        labelMedium = mono(labelMedium, 12),
        labelSmall = mono(labelSmall, 10),
    )
}

/** Every Material container gets hard, square corners. */
private val BlockShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun LauscherTheme(mode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val context = LocalContext.current
    var scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> NightEarthColors
        else -> PaperColors
    }
    if (mode == ThemeMode.BLACK) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0E0B08),
            surfaceContainer = Color(0xFF15100C),
            surfaceContainerHigh = Color(0xFF1E1812),
            surfaceContainerHighest = Color(0xFF282018),
        )
    }
    val retro = if (dark) {
        RetroColors(ink = scheme.outline, shadow = Color(0xFF000000), earth = EarthTones)
    } else {
        RetroColors(ink = Color(0xFF2E2118), shadow = Color(0xFF2E2118), earth = EarthTones)
    }
    CompositionLocalProvider(LocalRetro provides retro) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography().retro(),
            shapes = BlockShapes,
            content = content,
        )
    }
}
