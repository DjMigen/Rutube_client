package ru.rutubeclient.tv.ui

import androidx.compose.material3.MaterialTheme as ComposeMaterialTheme
import androidx.compose.material3.darkColorScheme as composeDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

private val TvColors = tvDarkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    background = Color(0xFF141414),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1F1F1F),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

/**
 * The same palette for plain androidx.compose.material3 components (search
 * text field, sliders, progress bars). Without this, those components fall
 * back to the default light theme and render dark text on the dark background.
 */
private val ComposeColors = composeDarkColorScheme(
    primary = Color(0xFFE50914),
    onPrimary = Color.White,
    background = Color(0xFF141414),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1F1F1F),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    TvMaterialTheme(colorScheme = TvColors) {
        ComposeMaterialTheme(colorScheme = ComposeColors) {
            content()
        }
    }
}
