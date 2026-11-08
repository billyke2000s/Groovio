package com.groovio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Groovio's own palette. The accent is a warm coral, deliberately not Spotify green. */
object GroovioColors {
    val Accent = Color(0xFFFF7A45)
    val OnAccent = Color(0xFF1A0E08)
    val Background = Color(0xFF0F1115)
    val Surface = Color(0xFF1A1D23)
    val SurfaceHigh = Color(0xFF262A33)
    val Text = Color(0xFFF4F5F7)
    val TextMuted = Color(0xFFA7ADBA)
    val ArtworkFallback = Color(0xFF3A2A24)
}

private val scheme = darkColorScheme(
    primary = GroovioColors.Accent,
    onPrimary = GroovioColors.OnAccent,
    background = GroovioColors.Background,
    onBackground = GroovioColors.Text,
    surface = GroovioColors.Background,
    onSurface = GroovioColors.Text,
    surfaceVariant = GroovioColors.Surface,
    onSurfaceVariant = GroovioColors.TextMuted,
    surfaceContainer = GroovioColors.Surface,
    surfaceContainerHigh = GroovioColors.SurfaceHigh,
)

@Composable
fun GroovioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
