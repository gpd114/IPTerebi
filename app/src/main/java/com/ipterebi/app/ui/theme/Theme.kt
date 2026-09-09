package com.ipterebi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark only, and not because a light scheme would be hard: the app is a frame
// around moving video, and a light chrome around a dark picture is unpleasant
// to sit in front of. Revisit if there is ever a reason to.
private val ColorScheme = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF0B1622),
    secondary = Color(0xFF9DB2C8),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF161B21),
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF1E252D),
    onSurfaceVariant = Color(0xFFAEB9C4),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF1A0A08),
)

@Composable
fun IPTerebiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
