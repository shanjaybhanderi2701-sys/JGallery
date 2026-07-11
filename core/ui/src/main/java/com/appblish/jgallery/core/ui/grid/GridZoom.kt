package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.appblish.jgallery.core.model.ColumnCount
import kotlin.math.roundToInt

/**
 * Column count for an accumulated pinch [zoom], relative to the count when the gesture STARTED.
 * Spreading (zoom > 1) grows tiles → fewer columns; pinching in → more. Rounding against the
 * gesture-start count gives natural hysteresis: crossing each ±half-column boundary morphs live,
 * and the value on finger-up already IS the nearest snap (design §3: live morph, snap on release).
 */
fun columnsForPinch(startColumns: ColumnCount, zoom: Float): ColumnCount =
    ColumnCount.clamp((startColumns.value / zoom).roundToInt())

/**
 * Pinch-anywhere-in-the-grid column morphing (spec §4/§6, design W1-06). Single-finger events are
 * left untouched so LazyVerticalGrid scrolling and taps work exactly as before; once a second
 * pointer lands, moves are consumed (the grid must not scroll mid-pinch) and every half-column
 * threshold crossing calls [onColumnsChange] immediately — the morph is live, not on-release.
 *
 * Rescaling, not re-decoding: tile size changes only re-LAYOUT existing frames — the E4 memory-cache
 * key is size-agnostic, so morphing never triggers new decodes for already-loaded thumbnails.
 */
fun Modifier.gridPinchColumns(
    currentColumns: () -> ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var startColumns: ColumnCount? = null
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } < 2) continue

            val start = startColumns ?: currentColumns().also { startColumns = it }
            zoom *= event.calculateZoom()
            val target = columnsForPinch(start, zoom)
            if (target != currentColumns()) onColumnsChange(target)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

/**
 * Self-contained state for a pinch-zoomable grid: the live [columns] count plus the [gridState] the
 * grid scrolls with. Bundling them is what makes the pinch primitive reusable on *any* grid without
 * per-screen plumbing — and it is also what preserves the scroll anchor: the SAME [LazyGridState]
 * instance is handed to the grid across every column-count change, so LazyGrid keeps
 * `firstVisibleItemIndex` (the top item stays the top item) instead of snapping to the start. The
 * bug this rules out is recreating the grid/state when the column count changes.
 *
 * [columns] is snapshot-backed (recomposes the grid) and rememberSaveable-persisted (a pinch survives
 * rotation / process recreation). Screens that already own a persisted column preference — Photos,
 * Albums — drive [gridPinchColumns] with their own value instead of this holder.
 */
@Stable
class GridZoomState(
    initialColumns: ColumnCount,
    val gridState: LazyGridState,
) {
    var columns: ColumnCount by mutableStateOf(initialColumns)
        private set

    /** Apply a new column count (from a pinch). No-op when unchanged so no needless recomposition. */
    fun updateColumns(next: ColumnCount) {
        if (next != columns) columns = next
    }

    companion object {
        /** Persists just the column int; the grid state is restored/re-remembered by the caller. */
        fun Saver(gridState: LazyGridState): Saver<GridZoomState, Int> = Saver(
            save = { it.columns.value },
            restore = { GridZoomState(ColumnCount.clamp(it), gridState) },
        )
    }
}

/**
 * Remember a [GridZoomState] for a grid that has no external column preference (folder grids, the
 * Recycle Bin, pickers). Pair it with [gridPinchColumns] and read [GridZoomState.columns] for the
 * `GridCells.Fixed` count and [GridZoomState.gridState] for the grid's `state`.
 */
@Composable
fun rememberGridZoomState(
    initialColumns: ColumnCount = ColumnCount.DEFAULT,
    gridState: LazyGridState = rememberLazyGridState(),
): GridZoomState = rememberSaveable(gridState, saver = GridZoomState.Saver(gridState)) {
    GridZoomState(initialColumns, gridState)
}

/** Wire pinch-to-zoom column morphing straight from a [GridZoomState]. */
fun Modifier.gridPinchColumns(state: GridZoomState): Modifier =
    gridPinchColumns(currentColumns = { state.columns }, onColumnsChange = state::updateColumns)
