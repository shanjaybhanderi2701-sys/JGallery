package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.model.ColumnCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Nearest column count for an accumulated pinch [zoom], relative to the count when the gesture
 * STARTED. Spreading (zoom > 1) grows tiles → fewer columns; pinching in → more. The gesture reads
 * this each frame (see [gridPinchColumns]) to drive the live column count; there is no continuous
 * content-scaling, only a change of column count that the grid reflows to.
 */
fun columnsForPinch(startColumns: ColumnCount, zoom: Float): ColumnCount =
    ColumnCount.clamp((startColumns.value / zoom).roundToInt())

/**
 * Clean, non-bouncy settle timing for the reflow (APP-519, simplified in APP-537). Every
 * pinch-zoomable grid passes [GridReflowPlacementSpec] as the `placementSpec` of
 * `Modifier.animateItem`, so when [gridPinchColumns] changes the column count the tiles **slide** to
 * their new slots over this spring instead of snapping there. It is critically damped
 * ([Spring.DampingRatioNoBouncy]) so the reflow settles cleanly with no visible overshoot/bounce —
 * the board's key APP-537 requirement ("grid should adjust with animation … without bouncy layout").
 */
internal const val REFLOW_DAMPING = Spring.DampingRatioNoBouncy

/**
 * Position spec for the reflow (APP-519). Every pinch-zoomable grid passes this as the `placementSpec`
 * of `Modifier.animateItem` on its tile content, so a column-count change animates each tile to its
 * new slot over a clean, critically-damped spring — a real layout transition, no whole-grid scale and
 * no overshoot.
 */
val GridReflowPlacementSpec: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = REFLOW_DAMPING,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

/**
 * Simple pinch-to-zoom **column count** control (APP-537 — replaces the graphicsLayer scale + tanh
 * rubber-band of APP-495/APP-519/APP-521, which the board found "very buggy and bouncy"). Two fingers
 * change the number of columns and nothing else: the grid itself never visually zooms or shrinks.
 *
 * - **Gesture → column count.** The accumulated pinch zoom is mapped to a target column count each
 *   frame ([columnsForPinch], relative to the count when the two-finger gesture started). Spreading
 *   the fingers apart → fewer, bigger columns; pinching in → more, smaller columns.
 * - **Reflow only, no scale.** When the target count crosses a threshold the new count is committed
 *   immediately via [onColumnsChange]; because every grid tags its tiles with
 *   `Modifier.animateItem(placementSpec = `[GridReflowPlacementSpec]`)`, the tiles **slide/resize** to
 *   the new grid over a clean critically-damped spring. There is no `graphicsLayer` transform on the
 *   viewport, so the grid never bounces or overshoots.
 * - **Column-count indicator.** While pinching, a lightweight centered pill shows the target column
 *   count (fades in on gesture start, fades out shortly after release) so the change is legible.
 *
 * Single-finger events are left completely untouched, so grid scrolling and taps are unaffected; moves
 * are consumed only once a second pointer is down (the grid must not scroll mid-pinch).
 *
 * @param currentColumns the grid's live column count (its own persisted preference, or a
 *   [GridZoomState]); read at gesture start to anchor the relative mapping.
 * @param onColumnsChange invoked whenever the pinch crosses into a new column count.
 */
fun Modifier.gridPinchColumns(
    currentColumns: () -> ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    // The count shown in the overlay, and its fade alpha. The gesture loop writes the count and kicks
    // the fade in/out through this normal coroutine scope (the gesture runs in a restricted suspend
    // scope that can only await pointer events).
    var indicatorColumns by remember { mutableStateOf<ColumnCount?>(null) }
    var indicatorAlpha by remember { mutableFloatStateOf(0f) }

    this
        .pointerInput(Unit) {
            var indicatorJob: Job? = null
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var zoom = 1f
                var startColumns: ColumnCount? = null
                var committed: ColumnCount? = null
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.none { it.pressed }) break
                    if (event.changes.count { it.pressed } < 2) continue

                    val start = startColumns ?: run {
                        // First two-finger frame: anchor the relative mapping to the live count and
                        // fade the indicator in (one launch for the whole gesture, from wherever the
                        // alpha currently sits so a quick re-pinch stays continuous).
                        indicatorJob?.cancel()
                        val live = currentColumns()
                        indicatorColumns = live
                        indicatorJob = scope.launch {
                            animate(indicatorAlpha, 1f, animationSpec = tween(INDICATOR_FADE_IN_MS)) { v, _ ->
                                indicatorAlpha = v
                            }
                        }
                        startColumns = live
                        committed = live
                        live
                    }
                    zoom *= event.calculateZoom()
                    val target = columnsForPinch(start, zoom)
                    indicatorColumns = target
                    // Commit on threshold crossing: the grid reflows to the new column count via the
                    // shared animateItem spring — a clean size/position transition, no whole-grid scale.
                    if (target != committed) {
                        committed = target
                        onColumnsChange(target)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }

                // Release: hold the indicator briefly, then fade it out. No scale to settle — the
                // column count is already committed and the reflow spring owns the layout transition.
                if (startColumns != null) {
                    indicatorJob?.cancel()
                    indicatorJob = scope.launch {
                        delay(INDICATOR_HOLD_MS)
                        animate(indicatorAlpha, 0f, animationSpec = tween(INDICATOR_FADE_OUT_MS)) { v, _ ->
                            indicatorAlpha = v
                        }
                    }
                }
            }
        }
        .drawWithContent {
            drawContent()
            val alpha = indicatorAlpha
            val cols = indicatorColumns
            if (alpha > 0.01f && cols != null) {
                val layout = textMeasurer.measure(
                    text = cols.value.toString(),
                    style = INDICATOR_TEXT_STYLE,
                )
                val padH = 24.dp.toPx()
                val padV = 14.dp.toPx()
                val pillW = layout.size.width + padH * 2f
                val pillH = layout.size.height + padV * 2f
                val left = (size.width - pillW) / 2f
                val top = (size.height - pillH) / 2f
                drawRoundRect(
                    color = INDICATOR_SCRIM,
                    topLeft = Offset(left, top),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
                    alpha = alpha,
                )
                drawText(
                    textLayoutResult = layout,
                    color = Color.White,
                    topLeft = Offset(left + padH, top + padV),
                    alpha = alpha,
                )
            }
        }
}

/** Timing/appearance of the column-count indicator pill. */
private const val INDICATOR_FADE_IN_MS = 120
private const val INDICATOR_FADE_OUT_MS = 220
private const val INDICATOR_HOLD_MS = 350L
private val INDICATOR_SCRIM = Color.Black.copy(alpha = 0.62f)
private val INDICATOR_TEXT_STYLE = TextStyle(
    color = Color.White,
    fontSize = 34.sp,
    fontWeight = FontWeight.SemiBold,
)

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
