package com.groovio.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.groovio.app.R
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

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/**
 * Figtree: a free geometric sans close to Spotify's (paid) Circular. Downloaded once by
 * Google Play services; falls back to the system font on devices without them.
 */
private val figtree = GoogleFont("Figtree")
val GroovioFont = FontFamily(
    Font(googleFont = figtree, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = figtree, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = figtree, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = figtree, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = figtree, fontProvider = provider, weight = FontWeight.Black),
)

private val typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = GroovioFont),
        displayMedium = base.displayMedium.copy(fontFamily = GroovioFont),
        displaySmall = base.displaySmall.copy(fontFamily = GroovioFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = GroovioFont, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = GroovioFont, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = GroovioFont, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = GroovioFont, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = GroovioFont, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = GroovioFont, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = GroovioFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = GroovioFont),
        bodySmall = base.bodySmall.copy(fontFamily = GroovioFont),
        labelLarge = base.labelLarge.copy(fontFamily = GroovioFont, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = GroovioFont),
        labelSmall = base.labelSmall.copy(fontFamily = GroovioFont),
    )
}

@Composable
fun GroovioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
