package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The disk-cache discipline that makes the 10k-scroll gate achievable, now expressed on the
 * post-APP-391 [ThumbnailPipeline] surface (a pure `cached`/`writeThrough` port — the single decode
 * and source-open moved to [ThumbnailFetcher]). Keys are bucketed + versioned, writes are best-effort
 * and never cache oversized payloads, and cache errors never propagate.
 */
class ThumbnailPipelineTest {

    private val request = ThumbnailRequest(MediaId("7"), versionMillis = 1_000L)

    @Test
    fun `write-through then read is a hit at the bucketed key`() {
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(cache)

        pipeline.writeThrough(request, requestedEdgePx = 250, byteArrayOf(1, 2, 3))

        assertThat(pipeline.cached(request, requestedEdgePx = 250)).isEqualTo(byteArrayOf(1, 2, 3))
        // 250 rounds up to the 256 bucket — the entry is keyed there.
        assertThat(cache.entries).containsKey(thumbnailDiskKey(request, 256))
    }

    @Test
    fun `a miss returns null`() {
        assertThat(ThumbnailPipeline(FakeByteCache()).cached(request, requestedEdgePx = 250)).isNull()
    }

    @Test
    fun `requests within the same bucket share a single cache entry`() {
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(cache)

        pipeline.writeThrough(request, requestedEdgePx = 200, byteArrayOf(7)) // 200 → 256 bucket

        // A neighbouring request in the same bucket (e.g. after a small pinch) hits the same entry —
        // no per-pixel disk variants.
        assertThat(pipeline.cached(request, requestedEdgePx = 256)).isEqualTo(byteArrayOf(7))
        assertThat(cache.entries).hasSize(1)
    }

    @Test
    fun `a version bump misses the old entry — stale thumbnails are never served`() {
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(cache)
        pipeline.writeThrough(request, requestedEdgePx = 250, byteArrayOf(9))

        val modified = pipeline.cached(request.copy(versionMillis = 2_000L), requestedEdgePx = 250)

        assertThat(modified).isNull()
    }

    @Test
    fun `oversized payloads (full-size fallback) are never cached`() {
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(cache)

        pipeline.writeThrough(request, requestedEdgePx = 250, ByteArray(2 * 1024 * 1024))

        assertThat(pipeline.cached(request, requestedEdgePx = 250)).isNull()
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `a cache read failure never propagates`() {
        assertThat(ThumbnailPipeline(ThrowingByteCache()).cached(request, requestedEdgePx = 250)).isNull()
    }

    @Test
    fun `a cache write failure never propagates`() {
        // Must not throw — a caller that already has its bytes cannot be failed by a broken cache.
        ThumbnailPipeline(ThrowingByteCache()).writeThrough(request, requestedEdgePx = 250, byteArrayOf(9))
    }
}

private class FakeByteCache : ThumbnailByteCache {
    val entries = mutableMapOf<String, ByteArray>()
    override fun read(key: String): ByteArray? = entries[key]
    override fun write(key: String, bytes: ByteArray) {
        entries[key] = bytes
    }
}

private class ThrowingByteCache : ThumbnailByteCache {
    override fun read(key: String): ByteArray? = error("disk unavailable")
    override fun write(key: String, bytes: ByteArray) = error("disk unavailable")
}
