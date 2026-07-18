package com.appblish.jgallery.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val JGalleryLightColorScheme = lightColorScheme(
    primary = JGalleryColors.Accent,
    onPrimary = JGalleryColors.OnAccent,
    primaryContainer = JGalleryColors.AccentSoft,
    onPrimaryContainer = JGalleryColors.Accent,
    background = JGalleryColors.Background,
    onBackground = JGalleryColors.Text,
    surface = JGalleryColors.Background,
    onSurface = JGalleryColors.Text,
    surfaceVariant = JGalleryColors.Surface,
    onSurfaceVariant = JGalleryColors.TextSecondary,
)

/**
 * App-wide dark scheme (G2 Settings §5, SET-05) — the first real dark theme (Wave 1 was light-only).
 * Tokens are the redlined dark values: lifted accent for AA on the #121317 background, a deep-blue
 * pill container, and a slightly-lighter destructive/warn hue. The viewer keeps its own black chrome
 * ([JGalleryViewerColorScheme]) independent of this.
 */
private val JGalleryDarkColorScheme = darkColorScheme(
    primary = JGalleryColors.DarkAccent,
    onPrimary = JGalleryColors.OnAccent,
    primaryContainer = JGalleryColors.DarkAccentSoft,
    onPrimaryContainer = JGalleryColors.DarkAccent,
    background = JGalleryColors.DarkBackground,
    onBackground = JGalleryColors.DarkText,
    surface = JGalleryColors.DarkSurface,
    onSurface = JGalleryColors.DarkText,
    surfaceVariant = JGalleryColors.DarkSurface,
    onSurfaceVariant = JGalleryColors.DarkTextSecondary,
    outline = JGalleryColors.DarkOutline,
    error = JGalleryColors.DarkDestructive,
)

/** Dark scheme used ONLY inside the full-screen viewer (spec §1: dark chrome is viewer-only). */
private val JGalleryViewerColorScheme = lightColorScheme(
    primary = JGalleryColors.Accent,
    onPrimary = JGalleryColors.OnAccent,
    background = JGalleryColors.ViewerCanvas,
    onBackground = JGalleryColors.OnAccent,
    surface = JGalleryColors.ViewerSheet,
    onSurface = JGalleryColors.OnAccent,
)

/**
 * App theme — image-forward, blue accent. Light is the historical Wave-1 system; [darkTheme] (new in
 * G2, threaded from the Settings `ThemeMode` at the app root) selects the dark scheme instead. Default
 * follows the device via [isSystemInDarkTheme] so the light-only default behavior is preserved on a
 * light device. Viewer chrome uses [JGalleryViewerTheme] and is unaffected.
 */
@Composable
fun JGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) JGalleryDarkColorScheme else JGalleryLightColorScheme,
        typography = JGalleryTypography,
        content = content,
    )
}

/** Dark chrome scope for the full-screen viewer only. */
@Composable
fun JGalleryViewerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JGalleryViewerColorScheme,
        typography = JGalleryTypography,
        content = content,
    )
}
