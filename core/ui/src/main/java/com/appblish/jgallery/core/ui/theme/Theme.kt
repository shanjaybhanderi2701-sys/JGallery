package com.appblish.jgallery.core.ui.theme

import androidx.compose.material3.MaterialTheme
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
 * App theme — light, image-forward, blue accent. This is the ONLY theme feature modules apply; there
 * is intentionally no dynamic-color / dark-mode branch in Wave 1 (the design is a fixed light system;
 * viewer chrome uses [JGalleryViewerTheme]).
 */
@Composable
fun JGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JGalleryLightColorScheme,
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
