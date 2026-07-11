package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import androidx.compose.runtime.remember
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.thumbs.fullImageRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.playerkit.VideoPlayerSurface
import androidx.media3.exoplayer.source.MediaSource

/**
 * One video page (spec §5, design W1-09). The player itself is the **shared, app-agnostic surface**
 * from `:core:playerkit` (APP-408, ported verbatim from CalcVault): ExoPlayer/`SurfaceView` host,
 * the gesture dispatcher on `VideoGestureMath`/`VideoZoomMath` (tap→chrome, double-tap→±10s seek,
 * pinch→zoom+pan), Fit⇄Fill on `VideoScaleMath`, and the scrub controls. This page supplies only the
 * JGallery-specific chrome: the boundary-routed poster and the §8 codec-error card, plus the accent
 * colour — so the same surface serves CalcVault's encrypted source unchanged.
 *
 * Adding the shared surface here closes the APP-400 P1 "pinch-to-zoom everywhere" for video pages.
 */
@Composable
internal fun VideoPage(
    item: MediaItem,
    createMediaSource: () -> MediaSource,
    isSettledPage: Boolean,
    chromeVisible: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
) {
    VideoPlayerSurface(
        pageKey = item.id,
        createMediaSource = createMediaSource,
        isActive = isSettledPage,
        chromeVisible = chromeVisible,
        onChromeVisibleChange = onChromeVisibleChange,
        initialAspect = item.aspectRatioOrZero(),
        initialDurationMs = item.durationMillis,
        accentColor = JGalleryColors.Accent,
        modifier = Modifier.testTag("viewer_video_page"),
        poster = { VideoPoster(item) },
        errorOverlay = { error ->
            VideoErrorCard(error = error.toVideoError(), onOpenWith = onOpenWith, onInfo = onInfo)
        },
    )
}

/** Poster: the grid thumbnail instantly, sharpened by a boundary-routed full-size frame decode. */
@Composable
private fun VideoPoster(item: MediaItem) {
    val context = LocalPlatformContext.current
    val placeholderRequest = remember(item.id, item.dateModifiedMillis) {
        ImageRequest.Builder(context)
            .data(item.thumbnailRequest())
            .size(POSTER_PLACEHOLDER_EDGE_PX)
            .build()
    }
    AsyncImage(
        model = placeholderRequest,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
    AsyncImage(
        model = item.fullImageRequest(), // VideoFrameDecoder serves the sharp frame
        contentDescription = item.displayName,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
    )
}

private const val POSTER_PLACEHOLDER_EDGE_PX = 384
