package com.appblish.jgallery.core.ui.format

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * The central graceful-degradation hook (APP-364, spec §8) shared by E13 (images) and E14 (video
 * posters): decode a media [model] to a real preview, or fall back to a placeholder that **never
 * crashes**. There is exactly one place that decides "render vs degrade" so every current and future
 * format degrades uniformly — a new unknown type slots in without touching this code.
 *
 * How it stays honest *and* fast:
 * - [MediaFormatSupport.preClassify] runs first on cheap index metadata. A file we already know is
 *   unrenderable (document container, zero-byte) skips the decode entirely and shows its placeholder
 *   directly — no wasted IO, no main-thread work (spec §1).
 * - Otherwise the happy path is a plain [AsyncImage] (no subcomposition overhead in the scrolling
 *   grid). Coil already catches decode failures and surfaces [AsyncImagePainter.State.Error] instead
 *   of throwing; we observe that and swap in the [MediaDecodeState.Corrupt] placeholder.
 *
 * The [placeholder] slot lets the caller render context-appropriate chrome for the same typed state:
 * a grid tile passes [MediaDecodeTilePlaceholder]; the viewer passes [ViewerUnsupportedCard]. Either
 * way the state that drives the placeholder is the same value reported through [onDecodeState], so
 * tile, viewer and Info can never disagree.
 *
 * @param model the Coil model for the real decode (e.g. a `ThumbnailRequest`/`FullImageRequest`).
 * @param displayName file name — its extension drives classification and the placeholder label.
 * @param mimeType index mime (may be empty); helps classify non-media containers up front.
 * @param sizeBytes index size; `0` is treated as an unreadable/corrupt file.
 * @param onDecodeState invoked whenever the resolved [MediaDecodeState] changes (for Info/analytics).
 *   `null` (the grid default) drops the per-tile reporting `LaunchedEffect` entirely — a scrolling
 *   grid recomposes/recycles thousands of tiles and never consumes the callback, so launching an
 *   effect per tile is pure fling overhead (APP-391 R1 fix 2). The viewer/Info path passes a real
 *   callback to keep the state observable there.
 * @param crossfade whether a newly decoded image fades in. `true` keeps the loader-level fade (the
 *   viewer). The grid passes `false` (APP-391 R1 fix 3): a per-tile crossfade forces an extra
 *   alpha-animated draw pass per tile during a fling, and a popped-in tile reads as *faster*. The
 *   loader default is left on so the viewer's fade is untouched.
 * @param loadingColor the flat fill drawn *under* the decode while the thumbnail is still loading
 *   (Coil's Loading state paints nothing). The hook owns this so a plain neutral surface is
 *   guaranteed at every call site — never a transparent/checkered gap that shows through to whatever
 *   is behind (APP-457, JD device-test finding 3). Defaults to the design-system neutral
 *   [JGalleryColors.TilePlaceholder]; callers that letterbox on a dark cell (panoramas) pass
 *   [Color.Black] so the fill matches their frame rather than the neutral.
 */
@Composable
fun MediaDecodeBox(
    model: Any?,
    displayName: String,
    mimeType: String,
    sizeBytes: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    onDecodeState: ((MediaDecodeState) -> Unit)? = null,
    crossfade: Boolean = true,
    loadingColor: Color = JGalleryColors.TilePlaceholder,
    placeholder: @Composable (MediaDecodeState) -> Unit,
) {
    val extension = remember(displayName) { MediaFormatSupport.extensionOf(displayName) }
    val preState = remember(displayName, mimeType, sizeBytes) {
        MediaFormatSupport.preClassify(displayName, mimeType, sizeBytes)
    }
    // Runtime decode failure for an otherwise-renderable file. Reset when the model changes so a
    // recycled composable in the grid doesn't inherit a stale error from the previous item.
    var decodeFailed by remember(model) { mutableStateOf(false) }

    val state: MediaDecodeState = preState
        ?: if (decodeFailed) MediaDecodeState.Corrupt(extension) else MediaDecodeState.Rendered

    // Only wire the reporting effect when a consumer actually exists (viewer/Info). The grid passes
    // null, so a fling never pays for a per-tile coroutine launch (APP-391 R1 fix 2). The branch is
    // stable per call site (a caller consistently passes null or a callback), so this is a legal
    // conditional effect — it never appears then disappears across recompositions of the same tile.
    if (onDecodeState != null) {
        LaunchedEffect(state) { onDecodeState(state) }
    }

    // Opt this call site out of the loader-level crossfade by wrapping the model in a request that
    // sets `crossfade(false)`; the default keeps whatever the loader configured. The Coil fetcher +
    // keyer still resolve off the request's `data` (the ThumbnailRequest), so cache keys are
    // identical — only the fade differs. Left unwrapped when `crossfade` is true to avoid allocating
    // a request per tile needlessly.
    val platformContext = LocalPlatformContext.current
    val resolvedModel: Any? = if (crossfade || model == null) {
        model
    } else {
        remember(model, platformContext) {
            ImageRequest.Builder(platformContext).data(model).crossfade(false).build()
        }
    }

    Box(modifier) {
        if (state.isPlaceholder) {
            placeholder(state)
        } else {
            AsyncImage(
                model = resolvedModel,
                contentDescription = contentDescription,
                contentScale = contentScale,
                // Flat neutral fill under the decode: while Coil is loading it paints nothing, so this
                // is the placeholder the user sees — a plain design-system surface, never a gap that
                // shows through to a checkered/transparent backdrop (APP-457).
                modifier = Modifier.fillMaxSize().background(loadingColor),
                onState = { s -> if (s is AsyncImagePainter.State.Error) decodeFailed = true },
            )
        }
    }
}
