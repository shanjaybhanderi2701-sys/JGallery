package com.appblish.jgallery.core.thumbs.internal

/**
 * Discrete edge sizes for cached thumbnails. Requests are rounded UP to the nearest bucket so a
 * pinch-zoom column change (2–6 cols ≈ a handful of distinct tile sizes) re-uses the same disk
 * entries instead of re-fetching per-pixel variants, and so a cached tile is never *smaller* than
 * what the grid asked for (upscaled tiles look soft).
 *
 * The ladder covers every reachable grid tile edge so the top bucket never upscales (APP-702,
 * finding #6). A tile edge in px is `≈ (contentWidthPx − (cols−1)·gutter) / cols`, minimised at the
 * 2-column floor ([ColumnCount.MIN]). On phones (content width ≤ ~1440 px) a 2-col tile is ≤ ~720 px,
 * so the 768 rung already covers them and round-up keeps their decode cost unchanged. The upper rungs
 * (1024, 1536) only ever activate above ~768 px — i.e. large screens: tablets / unfolded foldables
 * (2-col ≈ 800–1100 px) and tablet-landscape (2-col up to ~1280 px). 1536 covers a 2-col grid up to a
 * ~3072 px wide window, past any real device, so no reachable configuration draws from an upscaled
 * source. Because selection rounds up, these rungs never enlarge a decode that 768 already covered —
 * the fix bites only where measurement shows blur, adding no wasted decode on phones.
 */
internal val THUMBNAIL_EDGE_BUCKETS = intArrayOf(96, 128, 192, 256, 384, 512, 768, 1024, 1536)

/** Hard cap for grid-tile sources — anything above this is viewer territory, not a thumbnail. */
internal val MAX_THUMBNAIL_EDGE_PX = THUMBNAIL_EDGE_BUCKETS.last()

/**
 * Smallest bucket that covers [requestedEdgePx], capped at [MAX_THUMBNAIL_EDGE_PX]. Non-positive
 * requests (measurement not settled yet) get the smallest bucket — a cheap placeholder-grade load.
 */
internal fun selectThumbnailBucket(requestedEdgePx: Int): Int {
    if (requestedEdgePx <= 0) return THUMBNAIL_EDGE_BUCKETS.first()
    return THUMBNAIL_EDGE_BUCKETS.firstOrNull { it >= requestedEdgePx } ?: MAX_THUMBNAIL_EDGE_PX
}
