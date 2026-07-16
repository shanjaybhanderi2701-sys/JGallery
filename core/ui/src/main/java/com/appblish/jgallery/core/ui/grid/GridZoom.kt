package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.appblish.jgallery.core.model.ColumnCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Nearest column count for an accumulated pinch [zoom], relative to the count when the gesture
 * STARTED. Spreading (zoom > 1) grows tiles → fewer columns; pinching in → more. This is the SNAP
 * target the grid settles to on finger-up — the gesture itself scales continuously (see
 * [gridPinchColumns]) and this rounds the final zoom to the column count it lands on.
 */
fun columnsForPinch(startColumns: ColumnCount, zoom: Float): ColumnCount =
    ColumnCount.clamp((startColumns.value / zoom).roundToInt())

/**
 * The `graphicsLayer` scale at which a grid still showing [startColumns] looks pixel-identical to one
 * showing [targetColumns]. Settling the continuous zoom to THIS value before swapping the column
 * count is what makes the release seamless: at the swap instant the tiles are already exactly
 * `targetColumns`-sized, so resetting the scale to 1f in the same frame produces no visible jump.
 *
 * e.g. going 4→2 columns doubles tile size, so the content must be scaled to 2f; 3→6 halves it (0.5f).
 */
fun settleScaleFor(startColumns: ColumnCount, targetColumns: ColumnCount): Float =
    startColumns.value.toFloat() / targetColumns.value.toFloat()

/**
 * How far the live pinch scale may travel while still on [startColumns]: from the scale that reaches
 * the most columns ([ColumnCount.MAX]) up to the fewest ([ColumnCount.MIN]). A little [OVERSHOOT]
 * headroom past each end lets the fingers stretch slightly beyond the range so the spring settle
 * bounces back — the "rubber-band" edge feel ported from CalcVault.
 */
internal fun pinchScaleBounds(startColumns: ColumnCount): ClosedFloatingPointRange<Float> {
    val tightest = startColumns.value.toFloat() / ColumnCount.MAX // most columns → smallest scale
    val loosest = startColumns.value.toFloat() / ColumnCount.MIN  // fewest columns → largest scale
    return (tightest / OVERSHOOT)..(loosest * OVERSHOOT)
}

private const val OVERSHOOT = 1.12f

/**
 * Continuous pinch-to-zoom column morphing, ported from CalcVault's grid (APP-495, replacing the
 * discrete step-snapping of APP-403). Two fingers rescale the whole grid **smoothly and
 * continuously** via a `graphicsLayer` scale anchored at the pinch centroid — no hard column jumps
 * mid-gesture. On finger-up the scale springs (bouncily) to the nearest column count's tile size and,
 * in the same frame, the column count is committed and the scale reset to 1f, so the settle is
 * seamless. Single-finger events are left completely untouched, so grid scrolling and taps are
 * unaffected; moves are consumed only once a second pointer is down (the grid must not scroll
 * mid-pinch).
 *
 * Rescaling, not re-decoding: tile size changes are pure layout/`graphicsLayer` transforms — the
 * memory-cache key is size-agnostic, so morphing never triggers new decodes for loaded thumbnails.
 *
 * @param currentColumns the grid's live column count (its own persisted preference, or a
 *   [GridZoomState]); read at gesture start to anchor the relative scaling.
 * @param onColumnsChange invoked ONCE per gesture, on release, with the settled column count.
 */
fun Modifier.gridPinchColumns(
    currentColumns: () -> ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
): Modifier = composed {
    // The live continuous scale AND the release spring both ride this one Animatable. The gesture
    // loop runs in a *restricted* suspend scope (it can only await pointer events), so its animation
    // writes are dispatched through this normal coroutine scope.
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            transformOrigin = origin
        }
        .pointerInput(Unit) {
            var settleJob: Job? = null
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zoom = 1f
                var startColumns: ColumnCount? = null
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    if (event.changes.count { it.pressed } < 2) continue

                    val start = startColumns ?: run {
                        // A fresh pinch interrupts any in-flight settle so its column commit + reset
                        // never fire under us. Then anchor the relative scaling to the live count.
                        settleJob?.cancel()
                        currentColumns().also { startColumns = it }
                    }
                    // Anchor the zoom on the point BETWEEN the fingers, as a fraction of the node.
                    val centroid = event.calculateCentroid(useCurrent = true)
                    if (centroid.isSpecified) {
                        origin = TransformOrigin(
                            (centroid.x / size.width).coerceIn(0f, 1f),
                            (centroid.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                    zoom *= event.calculateZoom()
                    val live = zoom.coerceIn(pinchScaleBounds(start))
                    scope.launch { scale.snapTo(live) }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }

                // Release: settle to the nearest column count. Spring first (bouncy overshoot), then
                // swap columns + reset scale together so there is no visible jump at the swap frame.
                val start = startColumns
                if (start != null) {
                    val finalZoom = zoom
                    settleJob = scope.launch {
                        val target = columnsForPinch(start, finalZoom)
                        scale.animateTo(
                            targetValue = settleScaleFor(start, target),
                            animationSpec = spring(
                                dampingRatio = SETTLE_DAMPING,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                        if (target != currentColumns()) onColumnsChange(target)
                        scale.snapTo(1f)
                    }
                }
            }
        }
}

/** Bouncy-but-controlled settle: below 1.0 so the tiles overshoot the target size and spring back. */
private const val SETTLE_DAMPING = 0.62f

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
