package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AccentPalette
import com.example.data.local.AppThemeMode

private data class LightPaletteTokens(
    val primary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color
)

private fun lightSchemeFor(palette: AccentPalette): ColorScheme {
    val tokens = when (palette) {
        AccentPalette.NAVY -> LightPaletteTokens(
            Color(0xFF0A2540), Color(0xFFDBEAFE), Color(0xFF0A2540),
            SafeTeal700, SafeTeal100, SafeTeal700
        )
        AccentPalette.COBALT -> LightPaletteTokens(
            SafeBlue700, SafeBlue100, SafeBlue900,
            SafeTeal700, SafeTeal100, SafeTeal700
        )
        AccentPalette.TEAL -> LightPaletteTokens(
            SafeTeal700, SafeTeal100, Color(0xFF064E3B),
            SafeBlue700, SafeBlue100, SafeBlue900
        )
        AccentPalette.EMERALD -> LightPaletteTokens(
            SafeGreen700, SafeGreen100, Color(0xFF052E16),
            SafeBlue700, SafeBlue100, SafeBlue900
        )
    }
    return lightColorScheme(
        primary = tokens.primary,
        onPrimary = Color.White,
        primaryContainer = tokens.primaryContainer,
        onPrimaryContainer = tokens.onPrimaryContainer,
        secondary = tokens.secondary,
        onSecondary = Color.White,
        secondaryContainer = tokens.secondaryContainer,
        onSecondaryContainer = tokens.onSecondaryContainer,
        tertiary = ReportGoldDark,
        onTertiary = Color.White,
        tertiaryContainer = ReportGoldSoft,
        onTertiaryContainer = ReportNavy,
        background = CanvasLight,
        onBackground = TextPrimaryLight,
        surface = SurfaceWhite,
        onSurface = TextPrimaryLight,
        surfaceVariant = SurfaceMutedLight,
        onSurfaceVariant = TextSecondaryLight,
        outline = OutlineLight,
        error = EmergencyRed600,
        onError = Color.White,
        errorContainer = EmergencyRed100,
        onErrorContainer = EmergencyRed700
    )
}

private fun darkSchemeFor(palette: AccentPalette): ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer, secondary) = when (palette) {
        AccentPalette.NAVY -> listOf(
            Color(0xFF93C5FD), Color(0xFF0A2540), Color(0xFF163A5F), Color(0xFFE0F2FE), SafeTeal300
        )
        AccentPalette.COBALT -> listOf(
            SafeBlue300, SafeBlue900, SafeBlue900, SafeBlue100, SafeTeal300
        )
        AccentPalette.TEAL -> listOf(
            SafeTeal300, Color(0xFF042F2E), Color(0xFF115E59), SafeTeal100, SafeBlue300
        )
        AccentPalette.EMERALD -> listOf(
            SafeGreen300, Color(0xFF052E16), Color(0xFF14532D), SafeGreen100, SafeBlue300
        )
    }
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = Color(0xFF042F2E),
        secondaryContainer = Color(0xFF115E59),
        onSecondaryContainer = SafeTeal100,
        tertiary = ReportGold,
        onTertiary = Color(0xFF1E1400),
        tertiaryContainer = Color(0xFF423007),
        onTertiaryContainer = ReportGoldSoft,
        background = CanvasDark,
        onBackground = TextPrimaryDark,
        surface = SurfaceDark,
        onSurface = TextPrimaryDark,
        surfaceVariant = SurfaceElevatedDark,
        onSurfaceVariant = TextSecondaryDark,
        outline = OutlineDark,
        error = Color(0xFFFCA5A5),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = EmergencyRed100
    )
}

private fun TextStyle.scaled(factor: Float): TextStyle {
    if (kotlin.math.abs(factor - 1.0f) < 0.01f) return this
    return copy(
        fontSize = (fontSize.value * factor).sp,
        lineHeight = (lineHeight.value * factor).sp
    )
}

private fun scaledTypography(factor: Float): Typography {
    if (kotlin.math.abs(factor - 1.0f) < 0.01f) return SafeTrackTypography
    return SafeTrackTypography.copy(
        displaySmall = SafeTrackTypography.displaySmall.scaled(factor),
        headlineMedium = SafeTrackTypography.headlineMedium.scaled(factor),
        headlineSmall = SafeTrackTypography.headlineSmall.scaled(factor),
        titleLarge = SafeTrackTypography.titleLarge.scaled(factor),
        titleMedium = SafeTrackTypography.titleMedium.scaled(factor),
        titleSmall = SafeTrackTypography.titleSmall.scaled(factor),
        bodyLarge = SafeTrackTypography.bodyLarge.scaled(factor),
        bodyMedium = SafeTrackTypography.bodyMedium.scaled(factor),
        bodySmall = SafeTrackTypography.bodySmall.scaled(factor),
        labelLarge = SafeTrackTypography.labelLarge.scaled(factor),
        labelMedium = SafeTrackTypography.labelMedium.scaled(factor),
        labelSmall = SafeTrackTypography.labelSmall.scaled(factor)
    )
}

val SafeTrackShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SafeTrackTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    accentPalette: AccentPalette = AccentPalette.NAVY,
    fontScale: Float = 1.0f,
    darkTheme: Boolean = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    content: @Composable () -> Unit
) {
    val colorScheme = remember(darkTheme, accentPalette) {
        if (darkTheme) darkSchemeFor(accentPalette) else lightSchemeFor(accentPalette)
    }
    val typography = remember(fontScale) { scaledTypography(fontScale) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = SafeTrackShapes,
        content = content
    )
}
