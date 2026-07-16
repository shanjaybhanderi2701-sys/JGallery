package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeout

/**
 * Long-press-then-drag range-select for a [LazyVerticalGrid] (spec §7.6). Attach to the grid
 * container (not each tile) so a single continuous gesture can sweep across many items:
 *
 * - **long-press** on a tile enters selection and fixes the drag anchor ([onSelectStart]);
 * - **dragging** over adjacent tiles extends the selection to the item under the finger
 *   ([onDragOverIndex]); dragging back shrinks it (the caller unions against a pre-drag snapshot).
 *
 * Item resolution is a pure hit-test against [LazyGridState.layoutInfo], so headers/spacers simply
 * resolve to no index and are skipped. Plain taps are handled per-tile by the tile's own click
 * handler — this modifier only owns the long-press+drag gesture.
 *
 * ### Why a hand-rolled long-press (APP-493)
 * The obvious primitives — `awaitLongPressOrCancellation` and `detectDragGesturesAfterLongPress`,
 * which wraps it — both *cancel the long press the moment any pointer change reports `isConsumed`*.
 * On a real device the container is the parent of the scrollable [LazyVerticalGrid] and the tiles'
 * `clickable`/pinch handlers, all of which are descendants that see the pointer first on the Main
 * pass and consume it. The result: on-device the standalone detector cancels before the timeout and
 * long-press-to-select silently does nothing (it only "worked" in isolated `:core:ui` tests, where
 * no scrollable competes). [awaitLongPressAllowingConsumed] instead times the hold itself and only
 * bails on a real lift or a past-slop drag — never on mere consumption — so it fires reliably on
 * device. Once it fires we consume every subsequent change (moves *and* the terminal up) so the
 * tile's click handler can't read a spurious tap and toggle the just-selected item back off (item 5).
 *
 * @param enabled when false the gesture is inert (e.g. while a bulk op runs).
 * @param onSelectStart index of the tile under the long-press point.
 * @param onDragOverIndex index of the tile currently under the finger during a drag.
 */
fun Modifier.selectableGridDrag(
    gridState: LazyGridState,
    enabled: Boolean = true,
    onSelectStart: (index: Int) -> Unit,
    onDragOverIndex: (index: Int) -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(gridState) {
        awaitEachGesture {
            // Don't require the down unconsumed — the grid's scroll/pinch handlers see it too; we only
            // act if it grows into a long press.
            val down = awaitFirstDown(requireUnconsumed = false)
            // Fires on a genuine long press even when a descendant has consumed the pointer; returns
            // null for a plain tap (early up) or a drag (past-slop move) so the tile's own click
            // handler keeps working for those.
            val longPress = awaitLongPressAllowingConsumed(down) ?: return@awaitEachGesture
            gridState.itemIndexAt(longPress.position)?.let(onSelectStart)
            // From here we own the pointer: consume each change so (a) the tile's clickable never reads
            // a tap on release and toggles the just-selected item back off (item 5), and (b) the grid
            // doesn't scroll mid-select. Range-extend as the finger sweeps (item 6).
            var change: PointerInputChange = longPress
            change.consume()
            while (change.pressed) {
                val event = awaitPointerEvent()
                change = event.changes.firstOrNull { it.id == down.id } ?: break
                gridState.itemIndexAt(change.position)?.let(onDragOverIndex)
                change.consume()
            }
        }
    }
}

/**
 * Suspends until [down] has been held still (within touch slop) for the long-press timeout, returning
 * the still-pressed change; returns null if the finger lifts or travels past slop first. Unlike
 * `awaitLongPressOrCancellation` this does **not** cancel when a descendant consumes the pointer, which
 * is exactly the case on a scrollable grid — see [selectableGridDrag] (APP-493).
 */
private suspend fun AwaitPointerEventScope.awaitLongPressAllowingConsumed(
    down: PointerInputChange,
): PointerInputChange? {
    val slop = viewConfiguration.touchSlop
    return try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeout null
                if (!change.pressed) return@withTimeout null // lifted before the timeout → a plain tap
                if ((change.position - down.position).getDistance() > slop) {
                    return@withTimeout null // travelled past slop → a scroll/drag, not a long press
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        // Held still past the timeout with no lift and no past-slop travel → a genuine long press.
        down
    }
}

/**
 * The adapter index of the grid item whose bounds contain [point] (viewport coordinates), or null if
 * the point is over a gap/header or outside every laid-out item. Pure geometry over the current
 * [LazyGridLayoutInfo] — no allocation beyond the visible-items scan.
 */
internal fun LazyGridState.itemIndexAt(point: Offset): Int? {
    val info = layoutInfo
    return info.visibleItemsInfo.firstOrNull { item ->
        val x = point.x
        val y = point.y
        x >= item.offset.x && x < item.offset.x + item.size.width &&
            y >= item.offset.y && y < item.offset.y + item.size.height
    }?.index
}
