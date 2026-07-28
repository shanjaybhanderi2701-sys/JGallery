package com.appblish.jgallery.core.thumbs

/**
 * Thread-safe tally of the *requested* tile edge (px) the grid asked Coil for, bucketed onto the
 * thumbnail ladder ([ThumbnailSizes]). It answers the single question APP-709's grounded root cause
 * hinges on — "at 50k on a phone, which buckets actually fire, and does 1024/1536 ever fire?" — with
 * data instead of a hypothesis.
 *
 * A pure accumulator (no Android / Coil types) so it unit-tests on the JVM and can be fed from either
 * the APP-699 benchmark `EventListener` (which observes the real resolved request size, zero
 * production code touched) or a future debug overlay.
 *
 * Beyond the bucket spread it flags the two "real bug" signals the issue calls out — either would be
 * the *actual* over-decode, not the phone happy path:
 * - [Snapshot.oversized] — a request whose edge exceeded [ThumbnailSizes.maxEdgePx] (would draw from
 *   an upscaled / over-decoded source);
 * - [Snapshot.unbounded] — a gridable request with no concrete pixel size (a `fullStreamResult`
 *   fallback, e.g. an unbounded height letting the decode pick a large dimension).
 */
class RequestedEdgeHistogram {

    private val lock = Any()
    private val bucketCounts = HashMap<Int, Long>()
    private var total = 0L
    private var oversized = 0L
    private var unbounded = 0L

    /** Record one grid-tile request whose largest resolved pixel edge is [requestedEdgePx]. */
    fun record(requestedEdgePx: Int) {
        if (requestedEdgePx <= 0) {
            recordUnbounded()
            return
        }
        val bucket = ThumbnailSizes.bucketFor(requestedEdgePx)
        synchronized(lock) {
            total++
            bucketCounts[bucket] = (bucketCounts[bucket] ?: 0L) + 1L
            if (requestedEdgePx > ThumbnailSizes.maxEdgePx) oversized++
        }
    }

    /** Record a request with no concrete pixel size (the un-bucketable full-stream fallback). */
    fun recordUnbounded() {
        synchronized(lock) {
            total++
            unbounded++
        }
    }

    fun reset() {
        synchronized(lock) {
            bucketCounts.clear()
            total = 0L
            oversized = 0L
            unbounded = 0L
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            total = total,
            byBucket = ThumbnailSizes.edgeBuckets.associateWith { bucketCounts[it] ?: 0L },
            oversized = oversized,
            unbounded = unbounded,
        )
    }

    data class Snapshot(
        val total: Long,
        /** Count per ladder rung, in ascending edge order; rungs that never fired report 0. */
        val byBucket: Map<Int, Long>,
        val oversized: Long,
        val unbounded: Long,
    ) {
        /**
         * Grep-able one-liner, e.g. `total=48210 384=31022 512=12904 768=4284 oversized=0 unbounded=0`.
         * Only non-zero rungs are printed so the phone-path spread reads at a glance; `oversized` and
         * `unbounded` are always printed (a non-zero value there is the bug to chase).
         */
        fun format(): String = buildString {
            append("total=").append(total)
            byBucket.forEach { (edge, count) ->
                if (count > 0L) append(' ').append(edge).append('=').append(count)
            }
            append(" oversized=").append(oversized)
            append(" unbounded=").append(unbounded)
        }
    }
}
