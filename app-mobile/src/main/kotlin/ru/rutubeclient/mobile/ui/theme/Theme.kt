package ru.rutubeclient.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NetflixRed = Color(0xFFE50914)

private val Colors = darkColorScheme(
    primary = NetflixRed,
    onPrimary = Color.White,
    secondary = Color(0xFF90CAF9),
    background = Color(0xFF141414),
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF2A2A2A),
    onBackground = Color(0xFFEAEAEA),
    onSurface = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFFB0B0B0),
)

@Composable
fun RuTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}
