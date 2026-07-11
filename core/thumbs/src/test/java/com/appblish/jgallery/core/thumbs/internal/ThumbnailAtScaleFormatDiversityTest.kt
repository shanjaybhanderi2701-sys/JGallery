package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.ImageFormat
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.classifyImageFormat
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException

/**
 * W3-E16 final DoD item (spec §1.1, §8, §11): a **10,000+ mixed-format library exercised through the
 * perf core — cache-hit behaviour holds under format diversity**.
 *
 * The perf core is [ThumbnailPipeline]'s cache discipline plus the APP-391 R1 decode-once cold path
 * (modelled here by [coldPath], mirroring [ThumbnailFetcher]). The property this test pins is the one
 * the DoD headline rests on: **format diversity must not degrade the cache hit-rate.** The disk key is
 * bucketed size + version only — deliberately format-agnostic — so a GIF, a RAW embedded-JPEG, an SVG
 * raster, a HEIF and a plain JPEG that have all been seen once are *all* served from cache on the next
 * scroll, with zero thumbnail decodes. Corrupt items in the mix never poison the cache, and the rare
 * full-size fallback is served but never cached (so one odd large HEIC can't evict hundreds of tiles).
 *
 * The decode-once guarantee shows up directly: a well-formed tile decodes MediaStore's thumbnail once
 * and NEVER opens the full-size original; only an un-thumbnailable source falls back to a full stream.
 *
 * This is the JVM-authoritative half of the E16 at-scale story. The frame-time / "no stutter" half is
 * a physical-device macrobench concern owned by the perf spec (APP-369) and Wave-3 QA (APP-366); the
 * CI emulator cannot COLD-render 10k authoritatively.
 */
class ThumbnailAtScaleFormatDiversityTest {

    /** One representative row per §8 format family — the classifier confirms the mix is genuinely diverse. */
    private data class FormatRow(val name: String, val mime: String)

    private val formatFamilies = listOf(
        FormatRow("photo.jpg", "image/jpeg"),        // STANDARD
        FormatRow("screenshot.png", "image/png"),    // STANDARD
        FormatRow("portrait.heic", "image/heic"),    // HEIF
        FormatRow("sticker.webp", "image/webp"),     // WEBP
        FormatRow("scan.bmp", "image/bmp"),          // BMP
        FormatRow("meme.gif", "image/gif"),          // GIF
        FormatRow("logo.svg", "image/svg+xml"),      // SVG
        FormatRow("raw.nef", "image/x-nikon-nef"),   // RAW
    )

    private enum class SourceBehaviour { TileSized, Oversized, Corrupt }

    /**
     * Mirrors [ThumbnailFetcher]'s cold path against the shared [cache]:
     * disk hit → served (no decode); miss → decode the thumbnail ONCE and write it through, or (if
     * un-thumbnailable) fall back to the full-size stream (served, never cached). Records the two
     * source costs the DoD cares about.
     */
    private class ColdPathHarness(private val cache: ThumbnailByteCache) {
        val pipeline = ThumbnailPipeline(cache)
        val behaviours = HashMap<MediaId, SourceBehaviour>()
        var thumbnailDecodes = 0
        var fullOpens = 0

        /** Returns true when served from the cache (a warm hit). */
        fun load(request: ThumbnailRequest): Boolean {
            pipeline.cached(request, requestedEdgePx = 250)?.let { return true }
            thumbnailDecodes++
            val decoded = decodeThumbnail(request.id)
            if (decoded != null) {
                pipeline.writeThrough(request, requestedEdgePx = 250, decoded)
                return false
            }
            // Un-thumbnailable → graceful full-size fallback (still a single Coil decode in prod).
            fullOpens++
            openFull(request.id) // may throw for a corrupt source
            return false
        }

        /** MediaStore's downsized thumbnail: real bytes for a tile-sized source, null otherwise. */
        private fun decodeThumbnail(id: MediaId): ByteArray? =
            when (behaviours[id] ?: SourceBehaviour.TileSized) {
                SourceBehaviour.TileSized -> ByteArray(4 * 1024) // well-formed downsized JPEG
                SourceBehaviour.Oversized -> null                 // boundary couldn't downsize
                SourceBehaviour.Corrupt -> null                   // unreadable
            }

        private fun openFull(id: MediaId) {
            when (behaviours[id] ?: SourceBehaviour.TileSized) {
                SourceBehaviour.Oversized -> Unit // a 2 MB original streamed to Coil, never cached
                SourceBehaviour.Corrupt -> throw IOException("unreadable: corrupt/truncated/0-byte")
                SourceBehaviour.TileSized -> error("a tile-sized source never falls back to full")
            }
        }
    }

    @Test
    fun `cache-hit invariant holds across a 10k+ mixed-format library`() {
        val librarySize = 12_000
        val cache = CountingByteCache()
        val harness = ColdPathHarness(cache)

        // Build the library: assign each item a §8 format family; sprinkle corrupt and full-size
        // fallbacks the way a real 12k device library would contain them.
        val requests = ArrayList<ThumbnailRequest>(librarySize)
        val cacheableRequests = ArrayList<ThumbnailRequest>(librarySize)
        val seenFormats = HashSet<ImageFormat>()
        var corruptCount = 0
        var oversizedCount = 0

        for (i in 0 until librarySize) {
            val row = formatFamilies[i % formatFamilies.size]
            seenFormats += classifyImageFormat(row.mime, row.name)
            val id = MediaId("item-$i-${row.name}")
            val request = ThumbnailRequest(id, versionMillis = 1_000L + i)
            requests += request

            when {
                i % 37 == 0 -> { // corrupt / truncated / 0-byte — unreadable source
                    harness.behaviours[id] = SourceBehaviour.Corrupt
                    corruptCount++
                }
                i % 53 == 0 -> { // rare full-size fallback (a large HEIC the boundary couldn't downsize)
                    harness.behaviours[id] = SourceBehaviour.Oversized
                    oversizedCount++
                }
                else -> {
                    harness.behaviours[id] = SourceBehaviour.TileSized
                    cacheableRequests += request
                }
            }
        }

        // The library is genuinely format-diverse (the whole point of the DoD item).
        assertThat(seenFormats).containsAtLeast(
            ImageFormat.STANDARD, ImageFormat.HEIF, ImageFormat.WEBP,
            ImageFormat.BMP, ImageFormat.GIF, ImageFormat.SVG, ImageFormat.RAW,
        )
        assertThat(corruptCount).isGreaterThan(0)
        assertThat(oversizedCount).isGreaterThan(0)

        // --- Pass 1: cold scroll through the whole library. Every item decodes/fails exactly once. ---
        for (request in requests) {
            runCatching { harness.load(request) }
        }
        assertThat(harness.thumbnailDecodes).isEqualTo(requests.size) // each item decoded a thumbnail once
        // Decode-once win: well-formed tiles NEVER open the full-size original — only the
        // un-thumbnailable minority falls back (spec §1 rule 2).
        assertThat(harness.fullOpens).isEqualTo(corruptCount + oversizedCount)

        // Corrupt items poisoned nothing; only tile-sized items are cached; oversized served-not-cached.
        assertThat(cache.size).isEqualTo(cacheableRequests.size)

        // --- Pass 2: warm scroll. Format diversity must NOT cost a single extra decode. ---
        harness.thumbnailDecodes = 0
        harness.fullOpens = 0
        var hits = 0
        for (request in cacheableRequests) {
            if (harness.load(request)) hits++
        }

        assertThat(hits).isEqualTo(cacheableRequests.size) // 100% hit-rate under format diversity
        assertThat(harness.thumbnailDecodes).isEqualTo(0)  // warm scroll decoded nothing
        assertThat(harness.fullOpens).isEqualTo(0)
    }
}

private class CountingByteCache : ThumbnailByteCache {
    private val entries = HashMap<String, ByteArray>()
    val size: Int get() = entries.size
    override fun read(key: String): ByteArray? = entries[key]
    override fun write(key: String, bytes: ByteArray) {
        entries[key] = bytes
    }
}
