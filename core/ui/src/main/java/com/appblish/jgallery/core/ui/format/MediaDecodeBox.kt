package com.appblish.jgallery.core.ui.format

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

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
    onDecodeState: (MediaDecodeState) -> Unit = {},
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

    LaunchedEffect(state) { onDecodeState(state) }

    Box(modifier) {
        if (state.isPlaceholder) {
            placeholder(state)
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onState = { s -> if (s is AsyncImagePainter.State.Error) decodeFailed = true },
            )
        }
    }
}
