package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

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
            // act if it grows into a long press. A plain tap (up before the long-press timeout) makes
            // awaitLongPressOrCancellation return null, so we consume nothing and the tile's own click
            // handler fires normally.
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            // Item 5: consume the long press so the child tile's `clickable`/`combinedClickable` does
            // not also register a tap on release and toggle the just-selected item straight back off.
            longPress.consume()
            gridState.itemIndexAt(longPress.position)?.let(onSelectStart)
            // Range-extend as the finger sweeps; consuming each move keeps the tile from reading it as a
            // tap and stops the grid from scrolling mid-select.
            drag(longPress.id) { change ->
                gridState.itemIndexAt(change.position)?.let(onDragOverIndex)
                change.consume()
            }
        }
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
