package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
