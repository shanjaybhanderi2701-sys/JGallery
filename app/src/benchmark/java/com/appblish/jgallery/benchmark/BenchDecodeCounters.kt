package com.appblish.jgallery.benchmark

import android.content.Context
import android.util.Log
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.atomic.AtomicLong

/**
 * Harness-only decode / cache-hit instrumentation (APP-699, real-pipeline mode).
 *
 * ## What it proves
 * The scroll-perf sprint's whole premise is "thumbnails decode once, then re-scroll is free" — a
 * two-tier (memory LRU + disk) cache that guarantees **zero re-decode** when you fling back over
 * tiles you already saw. Frame timing alone can't prove that; a warm frame that skipped decode and a
 * warm frame that silently re-decoded a cached tile can look identical on the CPU percentile. This
 * counts the actual Coil outcomes so the claim is measured, not asserted.
 *
 * ## How (no production code touched)
 * Coil resolves the app-wide loader lazily via [SingletonImageLoader.get] (see `PhotosScreen`, which
 * calls it for both the visible `AsyncImage` tiles and the prefetch path). Before the benchmark grid
 * composes, [install] takes the **real** Hilt-built [ImageLoader] (same memory + disk cache), attaches
 * a counting [EventListener] via `newBuilder()`, and registers the wrapped loader through
 * [SingletonImageLoader.setSafe]. Because the wrapped loader shares the real caches, cache-hit
 * semantics are identical to production — we only observe them.
 *
 * A [ImageRequest] that resolves from the in-memory LRU reports [DataSource.MEMORY_CACHE] and performs
 * NO decode; anything else ([DataSource.DISK] / [DataSource.MEMORY] / network — network is structurally
 * impossible here, see `ThumbnailModule`) did real decode work. So on the FIRST scroll pass decodes
 * climb; on a re-scroll over the same tiles `decodeCount` must plateau while `cacheHit` climbs — the
 * grep-able proof of zero re-decode.
 *
 * Benchmark build variant ONLY — never compiled into a shipped debug/release APK.
 */
object BenchDecodeCounters {

    const val TAG = BenchmarkCorpusSeeder.TAG

    private val successTotal = AtomicLong(0)
    private val memoryCacheHit = AtomicLong(0)

    /** Successful loads served straight from the in-memory LRU — no decode happened. */
    fun cacheHits(): Long = memoryCacheHit.get()

    /** Loads that did real decode work (disk/source), i.e. everything that was NOT a memory-cache hit. */
    fun decodes(): Long = successTotal.get() - memoryCacheHit.get()

    fun reset() {
        successTotal.set(0)
        memoryCacheHit.set(0)
    }

    /** Compact, grep-able snapshot line body (paired with a `JGALLERY_BENCH_DECODE_COUNTS` tag). */
    fun snapshot(): String =
        "decodeCount=${decodes()} cacheHit=${cacheHits()} success=${successTotal.get()}"

    /**
     * Wrap the real Hilt [ImageLoader] with a counting listener and install it as the process
     * singleton. Idempotent and must be called BEFORE the grid first composes (i.e. before any image
     * request) — [SingletonImageLoader.setSafe] is a no-op once the loader has been resolved, so a late
     * call would silently leave the un-instrumented loader in place. Returns true if a fresh counting
     * loader was installed.
     */
    fun install(context: Context): Boolean {
        reset()
        val real = EntryPointAccessors
            .fromApplication(context.applicationContext, BenchImageLoaderEntryPoint::class.java)
            .imageLoader()
        val counting = real.newBuilder()
            .eventListener(CountingEventListener)
            .build()
        SingletonImageLoader.setSafe { counting }
        Log.i(TAG, "JGALLERY_BENCH_DECODE_COUNTS installed=true ${snapshot()}")
        return true
    }

    private object CountingEventListener : EventListener() {
        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
            successTotal.incrementAndGet()
            if (result.dataSource == DataSource.MEMORY_CACHE) {
                memoryCacheHit.incrementAndGet()
            }
        }
    }
}

/**
 * Hilt seam to fetch the REAL app-wide [ImageLoader] (the cached thumbnail pipeline from
 * `:core:thumbs`) so the harness can wrap — not replace — it. Benchmark variant only.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BenchImageLoaderEntryPoint {
    fun imageLoader(): ImageLoader
}
