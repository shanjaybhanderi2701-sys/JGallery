package com.appblish.jgallery.core.thumbs

import com.appblish.jgallery.core.thumbs.internal.MAX_THUMBNAIL_EDGE_PX
import com.appblish.jgallery.core.thumbs.internal.THUMBNAIL_EDGE_BUCKETS
import com.appblish.jgallery.core.thumbs.internal.selectThumbnailBucket

/**
 * Public, read-only view of the internal thumbnail bucket ladder (`internal/ThumbnailSizing.kt`), so
 * tooling can bucket a requested tile edge EXACTLY as the fetcher does without re-declaring the ladder
 * or reaching into `internal`. Two consumers today:
 * - the APP-699 benchmark [RequestedEdgeHistogram], which answers APP-709's grounded question — "at
 *   50k on a phone, which buckets actually fire?" — from the same source of truth the fetcher uses;
 * - [coarsePreviewEdgePx], the low-res first pass of the APP-709 progressive two-pass decode.
 *
 * The single source of truth stays the `internal` ladder; this only re-exports it.
 */
object ThumbnailSizes {

    /** The bucket ladder, ascending. Requests round UP to the nearest rung (see [bucketFor]). */
    val edgeBuckets: List<Int> = THUMBNAIL_EDGE_BUCKETS.toList()

    /** Largest bucket — a grid tile never draws from a source above this (viewer territory). */
    val maxEdgePx: Int = MAX_THUMBNAIL_EDGE_PX

    /**
     * The low-res first-pass edge for the progressive two-pass decode (APP-709): a small, cheap
     * bucket that a warm entry can paint INSTANTLY as a placeholder while the crisp display-size
     * decode lands. Because the in-memory key is size-agnostic, a coarse entry decoded at this edge
     * shares the crisp tile's cache key — Coil serves it as an under-sized placeholder, then the crisp
     * result replaces it in the LRU. Kept to the 2nd rung: big enough to read as the image, small
     * enough that its decode is a fraction of the crisp one (guardrail: cheaper, not more expensive).
     */
    val coarsePreviewEdgePx: Int = THUMBNAIL_EDGE_BUCKETS[1] // 128

    /** Smallest bucket that covers [requestedEdgePx] — the exact selection the fetcher makes. */
    fun bucketFor(requestedEdgePx: Int): Int = selectThumbnailBucket(requestedEdgePx)
}
