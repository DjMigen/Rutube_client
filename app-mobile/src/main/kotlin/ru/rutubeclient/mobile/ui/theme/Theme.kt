package ru.rutubeclient.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RuTubeRed = Color(0xFFE53935)

private val Colors = darkColorScheme(
    primary = RuTubeRed,
    onPrimary = Color.White,
    secondary = Color(0xFF90CAF9),
    background = Color(0xFF121212),
    surface = Color(0xFF1B1B1B),
    surfaceVariant = Color(0xFF262626),
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
