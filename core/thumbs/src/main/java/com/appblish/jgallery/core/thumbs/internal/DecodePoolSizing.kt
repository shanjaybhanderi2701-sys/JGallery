package com.appblish.jgallery.core.thumbs.internal

/**
 * Device-tier-aware sizing for the bounded thumbnail decode pool (APP-701, APP-697 finding #5).
 *
 * The old sizing was purely `availableProcessors().coerceIn(2, 4)` — it ignored how much heap the
 * device actually has. Every concurrent bitmap decode holds a full-resolution intermediate on the
 * Java heap, so on a low-RAM / small-heap phone too many decodes in parallel cause GC thrash (and
 * OOM risk) during a fling — the exact device class where scroll must stay smooth. A high-end device
 * with a large heap and many cores can afford more slots, which cuts decode latency and lets landing
 * tiles fill faster. We therefore pick the pool from BOTH the core count and the memory tier
 * (`ActivityManager.isLowRamDevice` + `memoryClass`), not cores alone.
 *
 * Kept pure (no Android types) so the tiering is JVM-unit-testable; the caller supplies the device
 * signals. Constant tuning is confirmed against the APP-699 decode-count harness on real hardware.
 */
internal object DecodePoolSizing {
    /** Floor — even a 1-core / low-heap device keeps two slots so a fling is never single-threaded. */
    const val MIN_PARALLELISM = 2
    const val LOW_TIER = 2
    const val MID_TIER = 4
    const val HIGH_TIER = 6

    /** `memoryClass` (MB) at/under which the device is treated as small-heap regardless of cores. */
    const val LOW_HEAP_MB = 96
    /** `memoryClass` (MB) at/over which the widest pool is unlocked (given enough cores). */
    const val HIGH_HEAP_MB = 256

    /**
     * Parallelism for the decode dispatcher.
     *
     * @param cores `Runtime.availableProcessors()`.
     * @param isLowRam `ActivityManager.isLowRamDevice` — the OS's own low-memory classification.
     * @param memoryClassMb `ActivityManager.memoryClass` — the app's nominal heap budget in MB.
     */
    fun parallelism(cores: Int, isLowRam: Boolean, memoryClassMb: Int): Int {
        val tier = when {
            isLowRam || memoryClassMb <= LOW_HEAP_MB -> LOW_TIER
            memoryClassMb >= HIGH_HEAP_MB -> HIGH_TIER
            else -> MID_TIER
        }
        // Never ask for more slots than there are cores, never fall below the fling floor.
        return tier.coerceAtMost(cores.coerceAtLeast(1)).coerceAtLeast(MIN_PARALLELISM)
    }

    // ---- Fetcher pool (APP-709) --------------------------------------------------------------
    // The pool above bounds the *decoder* stage, whose heap risk is real: each concurrent
    // subsample-decode of a streamed original holds a large full-resolution intermediate on the Java
    // heap, so too many at once = GC thrash / OOM during a fling. But a *cold* grid tile's dominant
    // cost is NOT in the decoder — it is `ThumbnailBitmapSource.loadThumbnailBitmap`
    // (`ContentResolver.loadThumbnail`) in the FETCHER stage, which returns an already-downsized
    // bitmap (<=768px, ~1-2 MB) and hands it straight to Coil with no decoder involvement
    // (`MediaFetchers.ThumbnailFetcher` returns an `ImageFetchResult`). That call is IO-bound — it
    // blocks on MediaStore's thumbnail service, not on CPU/heap — so throttling it to the same
    // narrow 2-6 slots as the heap-heavy decoder needlessly serialises cold visible-tile fill: a
    // screen of ~24 cold tiles at 50k drains in 4-12 sequential waves instead of ~2. Giving the
    // fetcher its own, wider pool fills the just-landed visible tiles far sooner (the board's
    // "thumbnails load slow" at 50k) WITHOUT loosening the decoder bound that keeps the fling smooth
    // (APP-700/701 guardrail). The ceiling is still memory-tiered so the transient set of in-flight
    // thumbnail bitmaps can't spike heap on a small-heap device; the fling gate still suspends
    // *lookahead* prefetch regardless of pool width, so a wider fetcher never re-floods a hard fling.
    const val FETCH_LOW_TIER = 4
    const val FETCH_MID_TIER = 8
    const val FETCH_HIGH_TIER = 12

    /**
     * Parallelism for the fetcher dispatcher — the IO-bound cold-thumbnail path. Wider than the
     * decoder pool because `loadThumbnail` blocks on IO (not CPU) and its downsized bitmaps are
     * cheap on the heap. Unlike [parallelism] this is NOT capped to the core count: IO-bound slots
     * spend most of their life parked waiting on the thumbnail service, so more slots than cores
     * still cut wall-clock fill time. The tier still tracks the heap budget so the concurrent
     * in-flight thumbnail set stays bounded on a small-heap device.
     *
     * @param isLowRam `ActivityManager.isLowRamDevice`.
     * @param memoryClassMb `ActivityManager.memoryClass` — the app's nominal heap budget in MB.
     */
    fun fetchParallelism(isLowRam: Boolean, memoryClassMb: Int): Int =
        when {
            isLowRam || memoryClassMb <= LOW_HEAP_MB -> FETCH_LOW_TIER
            memoryClassMb >= HIGH_HEAP_MB -> FETCH_HIGH_TIER
            else -> FETCH_MID_TIER
        }.coerceAtLeast(MIN_PARALLELISM)
}
