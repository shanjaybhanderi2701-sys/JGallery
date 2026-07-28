package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.thumbs.WriteBackGauge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Semaphore

/**
 * The bounded, drop-oldest-style admission gate for the off-path JPEG write-through (APP-712 fix for
 * the P0 thumbnail-loading ceiling).
 *
 * ## Why this exists
 * A cold grid miss decodes MediaStore's downsized thumbnail once, hands the bitmap to Coil
 * (`asImage(shareable = true)`), AND schedules a JPEG re-encode + disk write so the next cold launch
 * is a hit (see [ThumbnailFetcher.fetch]). That scheduled job **captures a live reference to the
 * bitmap**. Before this gate the job was launched unconditionally onto an unbounded scope drained by
 * only two IO workers, so on an all-miss fling jobs piled up ~6-10× faster than they drained and each
 * pending job pinned its bitmap on the heap — bitmaps Coil's LRU could not evict because they were
 * strongly referenced, not merely cached. Heap filled with hundreds of pinned bitmaps → GC thrash →
 * the next decode OOMed (swallowed) → tiles stopped loading (the "~400 item" ceiling).
 *
 * ## The invariant
 * At most [maxInFlight] write-backs run at once, so the bitmaps pinned by pending write-backs are
 * **O([maxInFlight]) — a small constant, independent of scroll distance.** A [submit] that arrives
 * while the cap is full is **dropped**, not queued: it never captures the bitmap at all, so nothing
 * accumulates. Dropping is safe — the disk cache is a cold-start optimization, so a skipped write just
 * means that one thumbnail re-decodes from MediaStore next cold launch (correctness is unaffected).
 *
 * Kept deliberately tiny and free of Coil types so it is JVM-unit-testable ([inFlight] exposes the
 * live permit count) — the APP-710 regression guard drives a simulated N-miss burst against it and
 * asserts the depth never exceeds the cap.
 */
internal class WriteBackGate(
    private val scope: CoroutineScope,
    private val maxInFlight: Int,
) {

    // A plain JUC semaphore: tryAcquire()/release() are non-suspending and all we need. Fairness is
    // irrelevant — a dropped write-through is a no-op, not a starved caller.
    private val permits = Semaphore(maxInFlight)

    /**
     * Try to run [write] on the bounded write-back [scope]. Returns true if it was accepted, false if
     * the in-flight cap was full and the write was dropped. The permit is held for the whole lifetime
     * of the job — including while it sits queued behind the scope's IO workers — which is exactly
     * what bounds the number of simultaneously-pinned bitmaps.
     */
    fun submit(write: suspend () -> Unit): Boolean {
        if (!permits.tryAcquire()) {
            WriteBackGauge.onDropped()
            return false
        }
        WriteBackGauge.onEnqueue()
        scope.launch {
            try {
                write()
            } finally {
                permits.release()
                WriteBackGauge.onComplete()
            }
        }
        return true
    }

    /** Write-backs currently holding a permit (running or queued) — i.e. the bounded queue depth. */
    fun inFlight(): Int = maxInFlight - permits.availablePermits()
}
