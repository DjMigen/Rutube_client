package ru.rutubeclient.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
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
    MaterialTheme(
        colorScheme = TvColors,
        content = content,
    )
}
