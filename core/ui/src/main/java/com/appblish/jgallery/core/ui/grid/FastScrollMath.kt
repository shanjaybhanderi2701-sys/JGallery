package com.appblish.jgallery.core.ui.grid

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure geometry for the fast-scroll thumb + date bubble (design §3 "Fast scroll", W1-07). Kept free
 * of Compose so every mapping rule is JVM-unit-testable; the composable in [GridFastScroller] is a
 * thin shell over these functions.
 */
object FastScrollMath {

    /** The thumb only exists once content is deeper than this many viewports (design §3). */
    const val MIN_VIEWPORTS = 4

    /** Drag speed (fraction of the full track per millisecond) past which the bubble collapses to "YYYY". */
    const val COLLAPSE_FRACTION_PER_MS = 0.0025f

    /** True when [totalItems] is deep enough for the thumb: more than [MIN_VIEWPORTS] screens of content. */
    fun deepEnough(totalItems: Int, visibleItems: Int): Boolean =
        visibleItems > 0 && totalItems > visibleItems * MIN_VIEWPORTS

    /**
     * Thumb position (0..1) for the current scroll state. Uses the first visible index against the
     * scrollable range so the thumb reaches 1.0 exactly when the last screenful is shown.
     */
    fun thumbFraction(firstVisibleIndex: Int, visibleItems: Int, totalItems: Int): Float {
        val range = (totalItems - visibleItems).coerceAtLeast(1)
        return (firstVisibleIndex.toFloat() / range).coerceIn(0f, 1f)
    }

    /** Inverse of [thumbFraction]: the item index a drag to [fraction] should land on (linear map, design §3). */
    fun targetIndex(fraction: Float, totalItems: Int): Int {
        if (totalItems <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * (totalItems - 1)).roundToInt()
    }

    /**
     * Nearest section start to [index] — where the thumb snaps on release (design §3: "release snaps
     * to nearest section start"). Empty [sectionStarts] returns [index] unchanged (albums-style grids).
     */
    fun nearestSectionStart(index: Int, sectionStarts: List<Int>): Int {
        if (sectionStarts.isEmpty()) return index
        return sectionStarts.minBy { abs(it - index) }
    }

    /** True when the bubble should collapse ("Month YYYY" → "YYYY") at this drag speed (design §3, a14). */
    fun bubbleCollapsed(fractionPerMs: Float): Boolean = abs(fractionPerMs) > COLLAPSE_FRACTION_PER_MS
}
