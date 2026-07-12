package com.appblish.jgallery.feature.photos

/**
 * Pure grid-index math behind the cold-cache first-load prefetch (APP-456, from JD device finding 2).
 * Kept free of Compose/Coil types so the direction, bound and priority-ordering rules are
 * JVM-unit-testable without an emulator (device timing is verified separately).
 *
 * The two windows split the work by scroll phase, which is how we honour "visible tiles first,
 * off-screen after" on a bounded decode dispatcher that has no per-request priority:
 * - [ahead] runs DURING a scroll: a bounded, nearest-first lookahead in the scroll direction. It stays
 *   modest so the tiles actually entering view keep winning decode slots; the caller cancels the prior
 *   batch on every new window, so work for tiles flung past is dropped.
 * - [idleWarm] runs once the scroll SETTLES: a wider symmetric window in both directions. Aggressive is
 *   safe here because no visible tile is competing for slots; the caller cancels it the instant
 *   scrolling resumes.
 */
internal object PrefetchPlanner {

    /**
     * Tile indices to warm AHEAD of an in-progress scroll, NEAREST-FIRST. Direction-aware and bounded:
     * at most [aheadCount] indices past the leading viewport edge, clamped into `0 until itemCount`.
     * Nearest-first ordering approximates visible-first priority — the row about to enter view is
     * enqueued before the deeper lookahead.
     */
    fun ahead(
        firstVisible: Int,
        lastVisible: Int,
        goingDown: Boolean,
        aheadCount: Int,
        itemCount: Int,
    ): List<Int> {
        if (aheadCount <= 0 || itemCount <= 0 || lastVisible < 0) return emptyList()
        val result = ArrayList<Int>(aheadCount)
        if (goingDown) {
            var i = lastVisible + 1
            while (i <= lastVisible + aheadCount && i < itemCount) {
                result.add(i)
                i++
            }
        } else {
            var i = firstVisible - 1
            while (i >= firstVisible - aheadCount && i >= 0) {
                result.add(i)
                i--
            }
        }
        return result
    }

    /**
     * Symmetric warm window around a settled viewport for IDLE background warming, interleaved
     * nearest-first (below, above, below, above …) so a subsequent slow scroll in either direction is
     * already warm. Excludes the visible range itself (those tiles are already loaded). Bounded by
     * [radius] and clamped into `0 until itemCount`.
     */
    fun idleWarm(
        firstVisible: Int,
        lastVisible: Int,
        radius: Int,
        itemCount: Int,
    ): List<Int> {
        if (radius <= 0 || itemCount <= 0 || lastVisible < 0) return emptyList()
        val result = ArrayList<Int>(radius * 2)
        for (d in 1..radius) {
            val below = lastVisible + d
            if (below < itemCount) result.add(below)
            val above = firstVisible - d
            if (above >= 0) result.add(above)
        }
        return result
    }
}
