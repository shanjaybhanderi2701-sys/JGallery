package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.bestEffortKind
import com.appblish.jgallery.core.thumbs.fullImageRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.format.MediaDecodeState
import com.appblish.jgallery.core.ui.format.MediaFormatSupport
import com.appblish.jgallery.core.ui.format.ViewerUnsupportedCard

/**
 * One photo page: the E4 thumbnail renders instantly underneath (it is what the grid just showed —
 * requested at the default grid's cache bucket so it comes straight from the LRU/disk cache) while
 * the full-quality decode streams in over it through the §1.6 boundary, off the main thread, on
 * Coil's fetcher dispatcher (spec §1 rule 3). Full-size decode happens here ONLY (spec §1 rule 2);
 * Coil still subsamples it to the viewport, never a needless full-resolution bitmap.
 *
 * Graceful degradation (spec §8, APP-364): the page shares the *same* typed [MediaDecodeState] the
 * grid tile uses. A format we can't render (up-front classification) or a file whose decode throws
 * (runtime Coil error) shows the shared [ViewerUnsupportedCard] instead of the image — never a crash,
 * never a blank canvas. The bottom action bar stays live above this so the file is still operable.
 */
@Composable
internal fun ImagePage(
    item: MediaItem,
    onToggleChrome: () -> Unit,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val extension = remember(item.displayName) { MediaFormatSupport.extensionOf(item.displayName) }
    val preState = remember(item.id) {
        MediaFormatSupport.preClassify(item.displayName, item.mimeType, item.sizeBytes)
    }
    // Runtime decode failure of a would-be-renderable image. Keyed to the item so a recycled page
    // never inherits a stale error from the previous photo.
    var decodeFailed by remember(item.id) { mutableStateOf(false) }
    val state: MediaDecodeState = preState
        ?: if (decodeFailed) MediaDecodeState.Corrupt(extension) else MediaDecodeState.Rendered

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("viewer_image_page"),
    ) {
        if (state.isPlaceholder) {
            val corrupt = state is MediaDecodeState.Corrupt
            ViewerUnsupportedCard(
                state = state,
                fileName = item.displayName,
                formatLabel = extension.uppercase(),
                sizeLabel = formatFileSize(item.sizeBytes),
                // Corrupt → Delete is the useful primary (clean it up); unsupported → Open with (spec §8).
                primaryLabel = if (corrupt) "Delete" else "Open with",
                onPrimary = if (corrupt) onDelete else onOpenWith,
                onInfo = onInfo,
            )
        } else {
            RenderablePhoto(item = item, onToggleChrome = onToggleChrome, onDecodeError = { decodeFailed = true })
        }
    }
}

@Composable
private fun RenderablePhoto(
    item: MediaItem,
    onToggleChrome: () -> Unit,
    onDecodeError: () -> Unit,
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
            .viewerZoomGestures(zoomState, scope, onTap = onToggleChrome),
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
            // Coil surfaces a failed full-size decode as Error rather than throwing — promote it to the
            // shared Corrupt card (spec §8). The placeholder underlay may still be a valid cache hit,
            // but a full decode that fails means the file itself can't be trusted to open.
            onState = { s -> if (s is AsyncImagePainter.State.Error) onDecodeError() },
        )
        // Best-effort formats (RAW embedded JPEG, SVG preview) surface an honest amber banner
        // (W3-04). Graceful degradation — the render is real, we're just naming its limit.
        item.bestEffortKind?.let { kind ->
            BestEffortBanner(
                kind = kind,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}

internal fun MediaItem.aspectRatioOrZero(): Float =
    if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 0f

/**
 * The default 3-column grid on a ~1080px screen asks the thumbnail pipeline for ≤384px tiles, so
 * requesting the same bucket here makes the placeholder a pure cache hit, not a third decode.
 */
private const val PLACEHOLDER_EDGE_PX = 384
