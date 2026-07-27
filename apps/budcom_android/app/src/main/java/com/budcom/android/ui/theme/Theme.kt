package com.budcom.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BudcomSkyDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBAE6FD),
    onPrimaryContainer = BudcomSlate900,
    secondary = BudcomSlate700,
    onSecondary = Color.White,
    secondaryContainer = BudcomSlate100,
    onSecondaryContainer = BudcomSlate900,
    tertiary = BudcomSuccess,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = BudcomSlate900,
    surface = Color.White,
    onSurface = BudcomSlate900,
    surfaceVariant = BudcomSlate100,
    onSurfaceVariant = BudcomSlate700,
    outline = BudcomSlate400,
    error = BudcomError,
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = BudcomSky,
    onPrimary = BudcomSlate900,
    primaryContainer = Color(0xFF075985),
    onPrimaryContainer = BudcomOnDark,
    secondary = BudcomSlate400,
    onSecondary = BudcomSlate900,
    secondaryContainer = BudcomSlate800,
    onSecondaryContainer = BudcomOnDark,
    tertiary = BudcomSuccess,
    onTertiary = BudcomSlate900,
    background = BudcomSlate900,
    onBackground = BudcomOnDark,
    surface = BudcomSlate800,
    onSurface = BudcomOnDark,
    surfaceVariant = BudcomSlate700,
    onSurfaceVariant = BudcomSlate400,
    outline = BudcomSlate700,
    error = BudcomError,
    onError = Color.White,
)

/**
 * BudCom Material 3 theme. Palette mirrors the desktop companion (slate + sky).
 */
@Composable
fun BudcomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = BudcomTypography,
        content = content,
    )
}
