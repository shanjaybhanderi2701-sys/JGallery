package com.appblish.jgallery.feature.viewer

/**
 * Pure, UI-free slideshow advance rules (APP-544, G2 · auto-play). Kept out of the composable so the
 * "where does the next slide go" decision is a plain JVM unit — the [ViewerPager] driver only owns the
 * timer + the scroll call. Slideshow order is implicitly the pager's order, which is the launch scope's
 * filter/sort ordering (spec: "respects current filter/sort ordering"), so there is nothing to re-sort.
 */
internal object Slideshow {

    /** Default auto-advance dwell per slide. Surfaced/overridable via Settings later (coordinate child). */
    const val DEFAULT_INTERVAL_MS: Long = 3_000L

    /**
     * The next page the slideshow should land on from [current], or `null` when it should stop.
     *
     * - Fewer than two items → nothing to advance to (`null`).
     * - Not yet at the last item → the next item.
     * - At the last item and [loop] → wrap to the first item.
     * - At the last item and not [loop] → stop (`null`).
     */
    fun nextPage(current: Int, count: Int, loop: Boolean): Int? = when {
        count <= 1 -> null
        current < count - 1 -> current + 1
        loop -> 0
        else -> null
    }
}
