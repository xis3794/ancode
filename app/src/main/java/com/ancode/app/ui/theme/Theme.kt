package com.ancode.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AncodeColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F2A47),
    onPrimaryContainer = AccentSoft,
    secondary = Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A1526),
    onSecondaryContainer = Color(0xFFC4B5FD),
    tertiary = Cyan,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgDeep,
    onSurface = TextPrimary,
    surfaceVariant = BgHover,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = BgElevated,
    surfaceContainerHigh = BgHover,
    surfaceContainerHighest = BorderDim,
    error = Error,
    onError = Color.White,
    outline = BorderDim,
    outlineVariant = Color(0xFF181818),
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