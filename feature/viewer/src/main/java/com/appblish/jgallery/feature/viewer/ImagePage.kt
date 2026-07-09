package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.thumbs.fullImageRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest

/**
 * One photo page: the E4 thumbnail renders instantly underneath (it is what the grid just showed —
 * requested at the default grid's cache bucket so it comes straight from the LRU/disk cache) while
 * the full-quality decode streams in over it through the §1.6 boundary, off the main thread, on
 * Coil's fetcher dispatcher (spec §1 rule 3). Full-size decode happens here ONLY (spec §1 rule 2);
 * Coil still subsamples it to the viewport, never a needless full-resolution bitmap.
 */
@Composable
internal fun ImagePage(
    item: MediaItem,
    onToggleChrome: () -> Unit,
) {
    val zoomState = remember(item.id) { ZoomState() }
    zoomState.contentAspectRatio = item.aspectRatioOrZero()
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val placeholderRequest = remember(item.id, item.dateModifiedMillis) {
        ImageRequest.Builder(context)
            .data(item.thumbnailRequest())
            .size(PLACEHOLDER_EDGE_PX)
            .build()
    }
    val zoomLayer = Modifier
        .fillMaxSize()
        .graphicsLayer {
            scaleX = zoomState.scale
            scaleY = zoomState.scale
            translationX = zoomState.offset.x
            translationY = zoomState.offset.y
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .viewerZoomGestures(zoomState, scope, onTap = onToggleChrome)
            .testTag("viewer_image_page"),
    ) {
        AsyncImage(
            model = placeholderRequest,
            contentDescription = null, // placeholder layer; the full image below carries semantics
            modifier = zoomLayer,
            contentScale = ContentScale.Fit,
        )
        AsyncImage(
            model = item.fullImageRequest(),
            contentDescription = item.displayName,
            modifier = zoomLayer,
            contentScale = ContentScale.Fit,
        )
    }
}

internal fun MediaItem.aspectRatioOrZero(): Float =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0f

/**
 * The default 3-column grid on a ~1080px screen asks the thumbnail pipeline for ≤384px tiles, so
 * requesting the same bucket here makes the placeholder a pure cache hit, not a third decode.
 */
private const val PLACEHOLDER_EDGE_PX = 384
