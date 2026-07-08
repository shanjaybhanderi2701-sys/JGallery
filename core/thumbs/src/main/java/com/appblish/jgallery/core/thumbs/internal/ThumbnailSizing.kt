package com.appblish.jgallery.core.thumbs.internal

/**
 * Discrete edge sizes for cached thumbnails. Requests are rounded UP to the nearest bucket so a
 * pinch-zoom column change (2–6 cols ≈ a handful of distinct tile sizes) re-uses the same disk
 * entries instead of re-fetching per-pixel variants, and so a cached tile is never *smaller* than
 * what the grid asked for (upscaled tiles look soft).
 */
internal val THUMBNAIL_EDGE_BUCKETS = intArrayOf(96, 128, 192, 256, 384, 512, 768)

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
