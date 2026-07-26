package com.appblish.jgallery.feature.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.util.lerp

/**
 * Zoom/pan state for one viewer page, applied via `graphicsLayer` (center pivot, parent-space
 * translation). All the gesture-priority rules from the signed-off design (§3 "must not fight")
 * reduce to two pure predicates on this state:
 *
 *  - at 1×: horizontal drags are LEFT UNCONSUMED → the pager swipes; taps toggle chrome;
 *    double-tap zooms to [doubleTapScale] anchored at the tap point.
 *  - above 1× (or any 2-pointer gesture): every position change is CONSUMED here → drags pan
 *    (clamped to the fitted content), pinches zoom 1–[maxScale], the pager never moves;
 *    double-tap returns to 1×. The pager re-engages only once scale is back at 1×.
 *
 * The math is kept Android-free so it is unit-testable on the JVM.
 */
@Stable
internal class ZoomState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    private val maxScale: Float = MAX_SCALE,
    private val doubleTapScale: Float = DOUBLE_TAP_SCALE,
) {
    var scale by mutableFloatStateOf(initialScale)
        private set
    var offset by mutableStateOf(initialOffset)
        private set

    /** Viewport size, fed from `onSizeChanged`. */
    var containerSize by mutableStateOf(Size.Zero)

    /** Content width/height ratio (from the index's dimensions); 0 = unknown → assume viewport. */
    var contentAspectRatio by mutableFloatStateOf(0f)

    val isZoomed: Boolean get() = scale > 1f + SCALE_EPSILON

    /** Design §3: consume the gesture iff it is a pinch or the page is already zoomed. */
    fun shouldConsume(pointerCount: Int): Boolean = pointerCount > 1 || isZoomed

    /**
     * Apply one gesture increment: zoom by [zoom] keeping the content under [centroid] fixed, then
     * pan by [pan]; scale clamps to 1..[maxScale] and the offset to the fitted-content bounds.
     */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(1f, maxScale)
        val center = containerSize.center()
        val anchored =
            if (newScale == scale) offset + pan
            else centroid - center - (centroid - center - offset) * (newScale / scale) + pan
        scale = newScale
        offset = clampOffset(anchored, newScale)
    }

    /** Double-tap toggles: zoomed → back to 1×; at 1× → [doubleTapScale] anchored at [tap]. */
    fun doubleTapTarget(tap: Offset): Pair<Float, Offset> =
        if (isZoomed) 1f to Offset.Zero
        else doubleTapScale to clampOffset((containerSize.center() - tap) * (doubleTapScale - 1f), doubleTapScale)

    suspend fun animateDoubleTap(tap: Offset) {
        val (targetScale, targetOffset) = doubleTapTarget(tap)
        val startScale = scale
        val startOffset = offset
        Animatable(0f).animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) {
            scale = lerp(startScale, targetScale, value)
            offset = lerp(startOffset, targetOffset, value)
        }
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Keep the fitted content covering the viewport; letterbox axes stay centered. */
    private fun clampOffset(proposed: Offset, atScale: Float): Offset {
        val fitted = fittedContentSize()
        val maxX = ((fitted.width * atScale - containerSize.width) / 2f).coerceAtLeast(0f)
        val maxY = ((fitted.height * atScale - containerSize.height) / 2f).coerceAtLeast(0f)
        return Offset(proposed.x.coerceIn(-maxX, maxX), proposed.y.coerceIn(-maxY, maxY))
    }

    /** ContentScale.Fit rectangle of the content inside the viewport at 1×. */
    private fun fittedContentSize(): Size {
        if (containerSize == Size.Zero) return Size.Zero
        if (contentAspectRatio <= 0f) return containerSize
        val containerAspect = containerSize.width / containerSize.height
        return if (contentAspectRatio >= containerAspect) {
            Size(containerSize.width, containerSize.width / contentAspectRatio)
        } else {
            Size(containerSize.height * contentAspectRatio, containerSize.height)
        }
    }

    private fun Size.center() = Offset(width / 2f, height / 2f)

    companion object {
        const val MAX_SCALE = 8f // design §3: pinch range 1–8×
        const val DOUBLE_TAP_SCALE = 2.5f // design §3: double-tap zooms to 2.5× at the tap point
        private const val SCALE_EPSILON = 0.01f

        /**
         * Persist zoom/pan across configuration change (rotate/fold) and process death (APP-655 §7.3,
         * architect-verified gap APP-648). Mirrors `GridZoomState.Saver`: only the user-driven state —
         * [scale] and [offset] — is saved; [containerSize] is re-fed by the next `onSizeChanged` and
         * [contentAspectRatio] by the item, so both restore themselves on the first frame after
         * recreation. The clamps in [transform]/[clampOffset] re-apply once the container is measured,
         * so a saved offset can never end up out of bounds.
         */
        fun Saver(): Saver<ZoomState, List<Float>> = Saver(
            save = { listOf(it.scale, it.offset.x, it.offset.y) },
            restore = { ZoomState(initialScale = it[0], initialOffset = Offset(it[1], it[2])) },
        )
    }
}
