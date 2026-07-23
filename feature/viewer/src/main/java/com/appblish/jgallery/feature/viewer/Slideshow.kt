package com.appblish.jgallery.feature.viewer

/**
 * Pure, UI-free slideshow advance rules (APP-544, G2 · auto-play; trigger + configured interval APP-594).
 * Kept out of the composable so the "where does the next slide go" and "how long do we dwell" decisions
 * are plain JVM units — the [ViewerPager] driver only owns the timer + the scroll call. Slideshow order
 * is implicitly the pager's order, which is the launch scope's filter/sort ordering (spec: "respects
 * current filter/sort ordering"), so there is nothing to re-sort.
 */
internal object Slideshow {

    /**
     * Hard ceiling on how long lean-back auto-play will dwell on a single video before advancing
     * (APP-548). Bounds the fallback so a long movie or a looping clip can never pin the slideshow
     * indefinitely — the earlier "skip advancing while a video is current" rule silently halted the
     * whole run on the first video it reached.
     */
    const val MAX_VIDEO_DWELL_MS: Long = 60_000L

    /**
     * How long auto-play should dwell on a video page before advancing (APP-548).
     *
     * We let the clip play through — dwelling for its [durationMillis] rather than cutting it off at
     * the configured image [intervalMs] — but clamp both ends so the slideshow always makes progress:
     * - never below [intervalMs], so a zero/unknown-duration video still dwells at least as long as an
     *   image would before moving on, and
     * - never above [MAX_VIDEO_DWELL_MS] (or [intervalMs] if that is somehow larger), so a long or
     *   looping video can't stall the run forever.
     *
     * Pure so it stays a plain JVM unit; the composable driver owns the actual timer. This is the
     * bounded interim fallback — wiring the player's completion seam is the preferred future upgrade.
     */
    fun videoDwellMs(durationMillis: Long, intervalMs: Long): Long =
        durationMillis.coerceIn(intervalMs, maxOf(intervalMs, MAX_VIDEO_DWELL_MS))

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
