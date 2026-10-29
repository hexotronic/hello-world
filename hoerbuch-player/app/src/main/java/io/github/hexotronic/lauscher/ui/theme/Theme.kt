package io.github.hexotronic.lauscher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.hexotronic.lauscher.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E5E4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4EFD8),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF2B1800),
    tertiary = Color(0xFF4A6178),
    tertiaryContainer = Color(0xFFD0E4FF),
    background = Color(0xFFFAF8F3),
    surface = Color(0xFFFAF8F3),
    surfaceVariant = Color(0xFFE2E6E0),
    surfaceContainer = Color(0xFFF0EEE8),
    surfaceContainerHigh = Color(0xFFEAE8E2),
    surfaceContainerHighest = Color(0xFFE4E2DC),
    outline = Color(0xFF717973),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD5BC),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF13503F),
    onPrimaryContainer = Color(0xFFB4EFD8),
    secondary = Color(0xFFF2BD6E),
    onSecondary = Color(0xFF482A00),
    secondaryContainer = Color(0xFF673F00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFB3C9E3),
    tertiaryContainer = Color(0xFF33495F),
    background = Color(0xFF101614),
    surface = Color(0xFF101614),
    surfaceVariant = Color(0xFF3F4944),
    surfaceContainer = Color(0xFF1C2320),
    surfaceContainerHigh = Color(0xFF262D2A),
    surfaceContainerHighest = Color(0xFF313835),
    outline = Color(0xFF89938D),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    )
}

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
        dark -> DarkColors
        else -> LightColors
    }
    if (mode == ThemeMode.BLACK) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerLow = Color(0xFF080808),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1E1E1E),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
