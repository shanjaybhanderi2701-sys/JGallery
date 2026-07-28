package com.appblish.jgallery.core.thumbs

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Observability for the bounded thumbnail write-back path (APP-712 — the P0 thumbnail-loading
 * ceiling).
 *
 * ## What it proves
 * The ceiling ("thumbnails stop loading after ~300-400 of 5000") was an **unbounded disk-cache
 * write-back queue that pinned every cold thumbnail bitmap on the Java heap**: on an all-miss cold
 * fling the 4/8/12-wide fetcher launched write-through jobs ~6-10× faster than the 2 IO workers
 * drained them, and every queued job held a strong reference to its ~0.5-0.9 MB bitmap. Coil's
 * 25%-heap LRU could not evict those (live-referenced, not cache-held) → heap filled → GC thrash →
 * the next decode OOMed (swallowed) → new tiles painted the flat placeholder.
 *
 * The fix (see [internal.WriteBackGate]) caps the number of in-flight write-backs to a small
 * constant, so the memory they pin is O(constant) regardless of how far the user scrolls. This gauge
 * makes that bound *measurable*: [inFlight] / [peakInFlight] are the "queue depth" the APP-699 bench
 * asserts stays bounded, while [dropped] counts write-throughs skipped because the cap was full (a
 * dropped write is harmless — that thumbnail simply re-decodes from MediaStore on the next cold
 * launch; the disk cache is an optimization, not correctness).
 *
 * A pure JVM accumulator (cheap atomics, no Coil/Android types) so it is always compiled — like
 * [RequestedEdgeHistogram] — and can back both the benchmark logcat proof and a future debug overlay
 * without touching the hot path. Fed exclusively from [internal.WriteBackGate].
 */
object WriteBackGauge {

    private val inFlight = AtomicInteger(0)
    private val peakInFlight = AtomicInteger(0)
    private val enqueued = AtomicLong(0)
    private val dropped = AtomicLong(0)

    /** A write-back was accepted onto the bounded scope (a permit was acquired). */
    internal fun onEnqueue() {
        enqueued.incrementAndGet()
        val now = inFlight.incrementAndGet()
        // Best-effort peak tracking — CAS-retry so a concurrent enqueue can't clobber a higher peak.
        var peak = peakInFlight.get()
        while (now > peak && !peakInFlight.compareAndSet(peak, now)) {
            peak = peakInFlight.get()
        }
    }

    /** A previously enqueued write-back finished (its permit was released, its bitmap ref dropped). */
    internal fun onComplete() {
        inFlight.decrementAndGet()
    }

    /** A write-back was skipped because the in-flight cap was full — the thumbnail re-decodes later. */
    internal fun onDropped() {
        dropped.incrementAndGet()
    }

    /** Write-backs currently pinning a bitmap on the heap. The invariant: this never exceeds the cap. */
    fun inFlight(): Int = inFlight.get()

    /** High-water mark of [inFlight] since the last [reset] — the headline boundedness number. */
    fun peakInFlight(): Int = peakInFlight.get()

    /** Total write-backs ever accepted. */
    fun enqueued(): Long = enqueued.get()

    /** Total write-backs skipped because the cap was full (harmless — re-decodes on next cold launch). */
    fun dropped(): Long = dropped.get()

    /** Reset all counters — the benchmark calls this before an instrumented run. */
    fun reset() {
        inFlight.set(0)
        peakInFlight.set(0)
        enqueued.set(0)
        dropped.set(0)
    }

    /** Compact, grep-able snapshot body (paired with a `JGALLERY_BENCH_WRITEBACK` tag in the bench). */
    fun snapshot(): String =
        "writeBackInFlight=${inFlight()} writeBackPeak=${peakInFlight()} " +
            "writeBackEnqueued=${enqueued()} writeBackDropped=${dropped()}"
}
