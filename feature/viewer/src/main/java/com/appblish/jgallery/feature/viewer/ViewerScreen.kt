package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.ui.theme.JGalleryViewerTheme

/**
 * Full-screen viewer. Scaffold placeholder — the swipe pager, pinch-zoom / double-tap / pan gesture
 * arbitration, and Media3 video playback are built in the Wave-1 viewer ticket. Wrapped in the
 * viewer-only dark chrome theme so the visual boundary is established from day one.
 */
@Composable
fun ViewerScreen(
    initialId: MediaId,
    modifier: Modifier = Modifier,
) {
    JGalleryViewerTheme {
        Box(modifier = modifier.fillMaxSize().testTag("viewer_screen"))
    }
}
