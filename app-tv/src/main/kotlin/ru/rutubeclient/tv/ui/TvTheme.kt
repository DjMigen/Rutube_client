package ru.rutubeclient.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    background = Color(0xFF101010),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF1B1B1B),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvColors,
        content = content,
    )
}
