package com.ancode.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AncodeColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1D3A5F),
    onPrimaryContainer = AccentSoft,
    secondary = Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2D2440),
    onSecondaryContainer = Color(0xFFC4B5FD),
    tertiary = Cyan,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgElevated,
    onSurface = TextPrimary,
    surfaceVariant = BgHover,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = BgElevated,
    surfaceContainerHigh = BgHover,
    surfaceContainerHighest = Color(0xFF22303F),
    error = Error,
    onError = Color.White,
    outline = BorderDim,
    outlineVariant = Color(0xFF2A3A4C),
    scrim = Color.Black
)

@Composable
fun AncodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AncodeColorScheme,
        typography = AncodeTypography,
        content = content
    )
}