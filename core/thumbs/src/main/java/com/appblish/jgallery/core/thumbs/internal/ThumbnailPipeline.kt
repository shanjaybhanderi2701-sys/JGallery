package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.thumbs.ThumbnailRequest

/** A loaded thumbnail payload. [fromCache] distinguishes the instant path for tests/diagnostics. */
internal data class ThumbnailBytes(val bytes: ByteArray, val fromCache: Boolean)

/**
 * The core of the cached thumbnail pipeline (spec §1 rule 2), kept free of Coil/Android types so
 * the cache discipline is JVM-testable:
 *
 * 1. **Disk hit** — return the downsized bytes as-is. No source open, no full decode; with the
 *    in-memory LRU above this, the scroll-critical path costs one small-file read at worst.
 * 2. **Miss** — ask the §1.6 boundary for a source AT THE BUCKETED TILE SIZE
 *    ([DecodeTarget.Thumbnail]), never the original, then write through for next time.
 *
 * Write-through is best-effort: a full disk, racing writer, or IO error must never fail a load that
 * already has its bytes. Oversized payloads (the boundary's rare full-size fallback for sources it
 * cannot thumbnail) are served but NOT cached — one odd 12 MB HEIC must not evict hundreds of tiles.
 */
internal class ThumbnailPipeline(
    private val storage: StorageAccess,
    private val cache: ThumbnailByteCache,
) {

    suspend fun load(request: ThumbnailRequest, requestedEdgePx: Int): ThumbnailBytes {
        val edgePx = selectThumbnailBucket(requestedEdgePx)
        val key = thumbnailDiskKey(request, edgePx)

        runCatching { cache.read(key) }.getOrNull()?.let {
            return ThumbnailBytes(it, fromCache = true)
        }

        val bytes = storage.openStream(request.id, DecodeTarget.Thumbnail(edgePx))
            .use { it.readBytes() }

        if (bytes.size <= MAX_CACHEABLE_BYTES) {
            runCatching { cache.write(key, bytes) }
        }
        return ThumbnailBytes(bytes, fromCache = false)
    }

    private companion object {
        // A well-formed ≤768px JPEG is well under this; only the full-size fallback path exceeds it.
        const val MAX_CACHEABLE_BYTES = 1024 * 1024
    }
}
