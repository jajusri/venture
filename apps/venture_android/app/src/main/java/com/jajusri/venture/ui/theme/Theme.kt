package com.jajusri.venture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = VentureSkyDark,
    onPrimary = Color.White,
    // blue-100, matching the VentureSkyDark primary above (was a cyan-sky-200 tint tuned for
    // the old, lighter/cyaner primary -- kept in the same family, not the old cyan hue).
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = VentureSlate900,
    secondary = VentureSlate700,
    onSecondary = Color.White,
    secondaryContainer = VentureSlate100,
    onSecondaryContainer = VentureSlate900,
    tertiary = VentureSuccess,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = VentureSlate900,
    surface = Color.White,
    onSurface = VentureSlate900,
    surfaceVariant = VentureSlate100,
    onSurfaceVariant = VentureSlate700,
    outline = VentureSlate400,
    error = VentureError,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = VentureSky,
    onPrimary = VentureSlate900,
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = VentureOnDark,
    secondary = VentureSlate400,
    onSecondary = VentureSlate900,
    secondaryContainer = VentureSlate800,
    onSecondaryContainer = VentureOnDark,
    tertiary = VentureSuccess,
    onTertiary = VentureSlate900,
    background = VentureSlate900,
    onBackground = VentureOnDark,
    surface = VentureSlate800,
    onSurface = VentureOnDark,
    surfaceVariant = VentureSlate700,
    onSurfaceVariant = VentureSlate400,
    outline = VentureSlate700,
    error = VentureError,
    onError = Color.White,
)

/**
 * Venture Material 3 theme. Palette mirrors the desktop companion (slate + sky).
 */
@Composable
fun VentureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VentureTypography,
        content = content,
    )
}
