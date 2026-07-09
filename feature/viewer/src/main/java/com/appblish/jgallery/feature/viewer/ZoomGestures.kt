package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The viewer's gesture arbitration (design §3). Child pointer input runs before the pager's drag
 * detection, so consumption here is what decides ownership: pinches and zoomed drags are consumed
 * (pan/zoom, pager frozen); 1× single-finger drags pass through untouched (pager swipes). Taps
 * toggle chrome via [onTap]; double-taps animate the zoom toggle.
 */
internal fun Modifier.viewerZoomGestures(
    state: ZoomState,
    scope: CoroutineScope,
    onTap: () -> Unit,
): Modifier =
    onSizeChanged { state.containerSize = it.toSize() }
        .pointerInput(state) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { tap -> scope.launch { state.animateDoubleTap(tap) } },
            )
        }
        .pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (state.shouldConsume(pressedCount)) {
                        val centroid = event.calculateCentroid()
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        if (centroid.isSpecified && (pan != Offset.Zero || zoom != 1f)) {
                            state.transform(centroid, pan, zoom)
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
