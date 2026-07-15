package com.appblish.jgallery.core.ui.grid

import java.text.NumberFormat
import java.util.Locale
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

    /**
     * True inverse of [thumbFraction]: the *first-visible* index a drag to [fraction] should scroll to
     * (design §3). Maps over the same scrollable range `[0, totalItems - visibleItems]` that
     * [thumbFraction] maps *out* of, so that after `scrollToItem(target)` the thumb settles back at the
     * exact fraction the finger released at — dragging to 50 % lands on 50 % (item 7). Mapping over
     * `(totalItems - 1)` instead (the old bug) put the top item under the finger, so the thumb snapped
     * to a higher fraction on release. [visibleItems] falls back to a full-range map when the layout is
     * momentarily empty (0), and the result is clamped to a real item index.
     */
    fun targetIndex(fraction: Float, totalItems: Int, visibleItems: Int): Int {
        if (totalItems <= 0) return 0
        val lastFirstIndex = (totalItems - visibleItems.coerceAtLeast(1)).coerceAtLeast(0)
        return (fraction.coerceIn(0f, 1f) * lastFirstIndex).roundToInt().coerceIn(0, totalItems - 1)
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

    /**
     * Absolute-position suffix for the fast-scroll bubble at scale (design W3-09): `"item 8,412 of
     * 61,908"`. [ordinal] is the 1-based item position (headers excluded) and [total] the item count;
     * both come straight from the cached index, so dragging a very large single folder never triggers
     * a rescan. Grouping separators are locale-aware. Returns `null` when there is nothing to count
     * (empty grid) so the caller can fall back to the month label alone.
     */
    fun formatItemPosition(ordinal: Int, total: Int, locale: Locale = Locale.getDefault()): String? {
        if (total <= 0) return null
        val nf = NumberFormat.getIntegerInstance(locale)
        val clamped = ordinal.coerceIn(1, total)
        return "item ${nf.format(clamped)} of ${nf.format(total)}"
    }
}
