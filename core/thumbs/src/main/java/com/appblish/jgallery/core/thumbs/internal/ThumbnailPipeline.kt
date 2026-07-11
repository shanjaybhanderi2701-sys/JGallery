package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.thumbs.ThumbnailRequest

/**
 * The disk-cache discipline behind the cached thumbnail pipeline (spec §1 rule 2), kept free of
 * Coil/Android types so it stays JVM-testable. After the APP-391 R1 decode-once refactor this owns
 * ONLY the on-disk byte cache — source opening + the single decode now live in [ThumbnailFetcher],
 * which calls [cached] on the fling-critical path and [writeThrough] off it.
 *
 * Its two guarantees are the ones the 10k-scroll gate depends on:
 * - **Bucketed, versioned keys.** Reads/writes use [selectThumbnailBucket] + [thumbnailDiskKey], so a
 *   pinch-zoom column change reuses entries and a changed file (new `versionMillis`) misses
 *   structurally — no manual eviction.
 * - **Best-effort, never-poisoning writes.** A full disk / racing writer / IO error must never throw
 *   into a load that already has its bytes, and an oversized payload (the rare full-size fallback) is
 *   never cached — one odd 12 MB HEIC must not evict hundreds of tiles.
 */
internal class ThumbnailPipeline(
    private val cache: ThumbnailByteCache,
) {

    /** Disk read at the bucketed+versioned key. Null on miss (or a swallowed read error). */
    fun cached(request: ThumbnailRequest, requestedEdgePx: Int): ByteArray? =
        runCatching { cache.read(diskKey(request, requestedEdgePx)) }.getOrNull()

    /**
     * Best-effort write-through of already-produced [bytes]. Silently skips oversized payloads (the
     * full-size fallback path) and swallows any cache error — a caller that already has its bytes must
     * never fail because the cache could not persist them.
     */
    fun writeThrough(request: ThumbnailRequest, requestedEdgePx: Int, bytes: ByteArray) {
        if (bytes.size > MAX_CACHEABLE_BYTES) return
        runCatching { cache.write(diskKey(request, requestedEdgePx), bytes) }
    }

    private fun diskKey(request: ThumbnailRequest, requestedEdgePx: Int): String =
        thumbnailDiskKey(request, selectThumbnailBucket(requestedEdgePx))

    private companion object {
        // A well-formed ≤768px JPEG is well under this; only the full-size fallback path exceeds it.
        const val MAX_CACHEABLE_BYTES = 1024 * 1024
    }
}
