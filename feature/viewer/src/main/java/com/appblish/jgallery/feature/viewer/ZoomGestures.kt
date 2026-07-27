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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Which action a gesture cycle has been *claimed* for. Owned by the arbiter, latched once per cycle
 * (arbiter design APP-693 §2). Because the mode is decided from `isZoomed` (sampled at the first down)
 * and, at 1×, the drag axis (sampled once at the slop crossing) and is **never re-decided mid-gesture**,
 * there is no state in which a drag is half-dismiss/half-pan — the §4 conflict test passes by construction.
 */
private enum class DragMode { UNDECIDED, PAGING, DISMISSING, TRANSFORMING }

/**
 * The viewer's gesture arbiter (arbiter design APP-693 §2–§6, motion-spec APP-692 §3 seam). One
 * `awaitEachGesture` cycle = first pointer down → last pointer up. The one rule that removes the
 * dismiss-vs-pan conflict: **zoom state is sampled once, at the moment a gesture is claimed, and the
 * claimed mode is latched until every pointer lifts.**
 *
 *  - **Zoomed at down (or any 2-pointer gesture) → TRANSFORMING:** consume every event → focal zoom /
 *    pan within bounds; the pager stays frozen. A mid-gesture pinch-back to 1× does NOT convert to
 *    dismiss (latch rule 1) — pan re-clamps as scale drops and the *next* gesture sees `!isZoomed`.
 *  - **1×, single finger, axis undecided → UNDECIDED:** consume nothing (the pager still receives the
 *    pre-slop drag start, so horizontal paging is never starved — §4 caveat). At the slop crossing the
 *    axis locks once: |dx| ≥ |dy| → PAGING (release to the pager), |dy| > |dx| → DISMISSING.
 *  - **DISMISSING:** consume → drive the hoisted swipe-down dismiss 1:1 ([onDismissDrag]); the pager is
 *    frozen. On release the threshold/velocity decides dismiss vs. spring snap-back ([onDismissRelease]).
 *  - **PAGING:** never consume — the `HorizontalPager` owns the swipe (today's behaviour, made explicit).
 *
 * §5(A) centroid-jump guard (required): when a finger lifts mid-pinch and the user keeps dragging, the
 * centroid teleports from the two-finger midpoint to the one finger → a spurious `pan` on that frame.
 * We skip applying `transform` on any frame where the pressed-pointer count changed, killing the jump.
 *
 * Tap / double-tap stay in the proven [detectTapGestures] block (the APP-667 both-ways fix — see the
 * `rememberUpdatedState` note below), the accepted lower-risk path from arbiter §3. The drag arbiter
 * consumes real drags, so `detectTapGestures` only ever fires on a genuine tap; the two hand off cleanly.
 */
@Composable
internal fun Modifier.viewerZoomGestures(
    state: ZoomState,
    scope: CoroutineScope,
    onTap: () -> Unit,
    /** Dismiss is suppressed while a slideshow runs (arbiter §7); a vertical drag then stays inert. */
    dismissEnabled: Boolean = true,
    onDismissDrag: (Offset) -> Unit = {},
    onDismissRelease: (Velocity) -> Unit = {},
    onDismissCancel: () -> Unit = {},
): Modifier {
    // [pointerInput] is keyed on [state] alone (a per-item [ZoomState] that never changes), so the tap
    // detector block runs exactly once and would otherwise permanently capture the FIRST [onTap] lambda —
    // i.e. the toggle would fire against a stale `chromeVisible`, hiding the chrome but never restoring it
    // (APP-667). We route the live callback through [rememberUpdatedState] and invoke `currentOnTap()`, so
    // the single long-lived detector always calls the latest lambda without re-keying (and thus re-arming)
    // the gesture arbitration on every toggle.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val currentOnDismissDrag by rememberUpdatedState(onDismissDrag)
    val currentOnDismissRelease by rememberUpdatedState(onDismissRelease)
    val currentOnDismissCancel by rememberUpdatedState(onDismissCancel)
    return onSizeChanged { state.containerSize = it.toSize() }
        .pointerInput(state) {
            detectTapGestures(
                onTap = { currentOnTap() },
                onDoubleTap = { tap -> scope.launch { state.animateDoubleTap(tap) } },
            )
        }
        .pointerInput(state) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Sample zoom ONCE, at down (latch rule 1). Multi-touch always transforms.
                var mode = if (state.isZoomed) DragMode.TRANSFORMING else DragMode.UNDECIDED
                var previousPressedCount = 1
                var totalDrag = Offset.Zero
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                do {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }

                    // A 2nd pointer in UNDECIDED/DISMISSING escalates to a pinch (arbiter §2 rule 3):
                    // cancel any in-flight dismiss first so the page doesn't teleport, then hand to zoom.
                    if (pressedCount > 1 && mode != DragMode.TRANSFORMING && mode != DragMode.PAGING) {
                        if (mode == DragMode.DISMISSING) currentOnDismissCancel()
                        mode = DragMode.TRANSFORMING
                    }

                    when (mode) {
                        DragMode.TRANSFORMING -> {
                            val centroid = event.calculateCentroid()
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            // §5(A): only apply the increment when the pointer count is stable across
                            // frames; the re-baseline frame (a finger lifted/joined) is skipped, no jump.
                            if (pressedCount == previousPressedCount &&
                                centroid.isSpecified && (pan != Offset.Zero || zoom != 1f)
                            ) {
                                state.transform(centroid, pan, zoom)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }

                        DragMode.UNDECIDED -> {
                            val change = event.trackedOrFirst(down.id)
                            if (change != null) {
                                totalDrag += change.positionChange()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            }
                            if (totalDrag.getDistance() > touchSlop) {
                                // Axis locks once, from the accumulated drag (arbiter §2 rule 2).
                                mode = if (abs(totalDrag.x) >= abs(totalDrag.y)) {
                                    DragMode.PAGING // horizontal-dominant → pager owns it; never consume
                                } else if (currentDismissEnabled) {
                                    DragMode.DISMISSING // vertical-dominant → claim for dismiss
                                } else {
                                    DragMode.PAGING // dismiss suppressed (slideshow): stay inert, don't consume
                                }
                                // On entering DISMISSING, replay the accumulated drag so the follow is 1:1
                                // from the finger's true position, and consume this event to freeze the pager.
                                if (mode == DragMode.DISMISSING) {
                                    currentOnDismissDrag(totalDrag)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                            // Pre-slop / PAGING: do NOT consume — the pager needs the drag start (§4 caveat).
                        }

                        DragMode.DISMISSING -> {
                            val change = event.trackedOrFirst(down.id)
                            if (change != null) {
                                currentOnDismissDrag(change.positionChange()) // 1:1 follow
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }

                        DragMode.PAGING -> Unit // pager owns the stream; the arbiter idles, consumes nothing
                    }
                    previousPressedCount = pressedCount
                } while (event.changes.any { it.pressed })

                if (mode == DragMode.DISMISSING) {
                    val v = velocityTracker.calculateVelocity()
                    currentOnDismissRelease(Velocity(v.x, v.y))
                }
                // UNDECIDED with no slop crossing = a tap → owned by detectTapGestures. TRANSFORMING/PAGING
                // settle on their own (pan is already clamped each event; the pager flings itself).
            }
        }
}

/** The tracked (first-down) pointer if still present, else any remaining change (arbiter re-baseline). */
private fun PointerEvent.trackedOrFirst(id: PointerId): PointerInputChange? =
    changes.firstOrNull { it.id == id } ?: changes.firstOrNull { it.pressed }
