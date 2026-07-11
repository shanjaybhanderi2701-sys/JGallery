package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/**
 * W3-E16 at-scale robustness (spec §1 rules, §8, §11 DoD). A 10,000+ mixed-format library will
 * contain corrupt, truncated and 0-byte files; the thumbnail path must fail each one *predictably*
 * without ever poisoning the shared disk cache (which would then serve garbage to a healthy
 * neighbour).
 *
 * After APP-391 R1 the source-open + single decode live in [ThumbnailFetcher]; the cache-poisoning
 * guarantee is now a contract between it and [ThumbnailPipeline.writeThrough]: **the fetcher only
 * ever writes through bytes it actually produced.** [coldPath] mirrors that exact decision so the
 * guarantee stays JVM-testable — a null bitmap (un-thumbnailable) or a decode/encode exception writes
 * nothing, and an oversized fallback is dropped by `writeThrough` itself.
 */
class ThumbnailPipelineRobustnessTest {

    private val request = ThumbnailRequest(MediaId("42"), versionMillis = 1_000L)

    /**
     * Mirrors [ThumbnailFetcher]'s cold path: on a miss it decodes the thumbnail ([produce]) and
     * writes through ONLY when real bytes come back. `null` = un-thumbnailable (full-stream fallback,
     * uncached); a throw = a corrupt/truncated source (surfaces the placeholder, uncached).
     */
    private fun coldPath(
        pipeline: ThumbnailPipeline,
        req: ThumbnailRequest,
        requestedEdgePx: Int,
        produce: () -> ByteArray?,
    ) {
        if (pipeline.cached(req, requestedEdgePx) != null) return
        val bytes = runCatching { produce() }.getOrNull() ?: return
        pipeline.writeThrough(req, requestedEdgePx, bytes)
    }

    @Test
    fun `an un-thumbnailable source writes nothing to the cache`() {
        val cache = RecordingByteCache()

        coldPath(ThumbnailPipeline(cache), request, 250) { null }

        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `a source that fails to decode surfaces nothing to the cache`() {
        val cache = RecordingByteCache()

        coldPath(ThumbnailPipeline(cache), request, 250) { throw IOException("corrupt/0-byte file") }

        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `one corrupt tile does not disturb the cached bytes of a healthy neighbour`() {
        val cache = RecordingByteCache()
        val pipeline = ThumbnailPipeline(cache)
        val healthy = ThumbnailRequest(MediaId("healthy"), versionMillis = 1_000L)

        // Healthy neighbour caches normally.
        coldPath(pipeline, healthy, 250) { byteArrayOf(1, 2, 3) }
        val healthyKey = thumbnailDiskKey(healthy, 256)
        assertThat(cache.entries).containsKey(healthyKey)

        // A corrupt item processed against the SAME cache must not evict or overwrite it.
        coldPath(pipeline, request, 250) { throw IOException("boom") }

        assertThat(cache.entries[healthyKey]).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `an oversized fallback never evicts a healthy neighbour`() {
        val cache = RecordingByteCache()
        val pipeline = ThumbnailPipeline(cache)
        val healthy = ThumbnailRequest(MediaId("healthy"), versionMillis = 1_000L)

        coldPath(pipeline, healthy, 250) { byteArrayOf(1, 2, 3) }
        // The rare full-size fallback (a large HEIC the boundary couldn't downsize) is > the cache
        // ceiling — writeThrough drops it, so it cannot evict hundreds of tiles.
        coldPath(pipeline, request, 250) { ByteArray(2 * 1024 * 1024) }

        assertThat(cache.entries[thumbnailDiskKey(healthy, 256)]).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(cache.entries).doesNotContainKey(thumbnailDiskKey(request, 256))
    }
}

private class RecordingByteCache : ThumbnailByteCache {
    val entries = mutableMapOf<String, ByteArray>()
    override fun read(key: String): ByteArray? = entries[key]
    override fun write(key: String, bytes: ByteArray) {
        entries[key] = bytes
    }
}
