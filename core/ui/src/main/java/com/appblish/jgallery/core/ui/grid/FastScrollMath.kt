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
     * Finger position → thumb fraction (0..1), re-derived from CalcVault's working `FastScrollbar`
     * (APP-496 item 3). The prior map (`y / trackHeight`) ignored the thumb, so the finger drove the
     * thumb's *top* edge while the thumb was rendered from its top over the shorter travel range —
     * the thumb visibly lagged/drifted under the finger and released at the wrong fraction. This maps
     * the finger to the thumb's **centre** over the exact travel range `[0, track - thumb]` the render
     * offset uses (`fraction * (track - thumb)`), so the two are precise inverses: the thumb centre
     * sits under the finger the whole drag and settles exactly where released. Degenerate sizes
     * (track ≤ thumb, i.e. nowhere to travel) collapse to 0.
     */
    fun thumbTravelFraction(y: Float, trackHeightPx: Float, thumbHeightPx: Float): Float {
        val travel = trackHeightPx - thumbHeightPx
        if (travel <= 0f) return 0f
        return ((y - thumbHeightPx / 2f) / travel).coerceIn(0f, 1f)
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

    /** Binary size units for [formatByteSize]; index into by power of 1024. */
    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    /**
     * Human-readable file size for the size-sorted fast-scroll bubble (APP-496 item 7): `"4.2 MB"`,
     * `"512 KB"`, `"0 B"`. Uses binary (1024) steps to match how a file manager reports size, one
     * decimal place from KB up (bytes stay whole), locale-aware decimal separator. Negative inputs
     * clamp to 0.
     */
    fun formatByteSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes <= 0L) return "0 B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < SIZE_UNITS.size - 1) {
            value /= 1024.0
            unit++
        }
        return if (unit == 0) {
            "${bytes} B"
        } else {
            String.format(locale, "%.1f %s", value, SIZE_UNITS[unit])
        }
    }
}
