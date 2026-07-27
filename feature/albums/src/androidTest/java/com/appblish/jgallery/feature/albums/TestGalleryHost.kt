package com.appblish.jgallery.feature.albums

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.core.ui.window.LocalWindowSizeClass

/**
 * Test host mirroring the app root (APP-651): provides a **Compact** [LocalWindowSizeClass] and then
 * applies [JGalleryTheme]. Screens read the window size class for adaptive grid columns (APP-653); on
 * Compact the size-class bonus is zero, so every screen renders at its phone baseline and existing
 * assertions are unaffected. Without a provider the seam intentionally fails loud.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal fun TestGalleryHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWindowSizeClass provides WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp)),
    ) {
        JGalleryTheme(content = content)
    }
}
