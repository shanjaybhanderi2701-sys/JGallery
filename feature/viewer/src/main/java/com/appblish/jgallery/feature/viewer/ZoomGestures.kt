package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
 *
 * [pointerInput] is keyed on [state] alone (a per-item [ZoomState] that never changes), so the tap
 * detector block runs exactly once and would otherwise permanently capture the FIRST [onTap] lambda —
 * i.e. the toggle would fire against a stale `chromeVisible`, hiding the chrome but never restoring it
 * (APP-667). We route the live callback through [rememberUpdatedState] and invoke `currentOnTap()`, so
 * the single long-lived detector always calls the latest lambda without re-keying (and thus re-arming)
 * the gesture arbitration on every toggle — see the video path's identical fix in `VideoPlayerSurface`.
 */
@Composable
internal fun Modifier.viewerZoomGestures(
    state: ZoomState,
    scope: CoroutineScope,
    onTap: () -> Unit,
): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)
    return onSizeChanged { state.containerSize = it.toSize() }
        .pointerInput(state) {
            detectTapGestures(
                onTap = { currentOnTap() },
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
}
