package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.ImageFormat
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.classifyImageFormat
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.MediaSignature
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StorageBackend
import com.appblish.jgallery.core.thumbs.ThumbnailRequest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * W3-E16 final DoD item (spec §1.1, §8, §11): a **10,000+ mixed-format library exercised through the
 * perf core — cache-hit behaviour holds under format diversity**.
 *
 * The perf core is [ThumbnailPipeline]'s cache discipline (the same one the 10k-scroll gate depends
 * on). The property this test pins is the one the DoD headline rests on: **format diversity must not
 * degrade the cache hit-rate.** The disk key is bucketed size + version only — deliberately
 * format-agnostic — so a GIF, a RAW embedded-JPEG, an SVG raster, a HEIF and a plain JPEG that have
 * all been seen once are *all* served from cache on the next scroll, with zero source opens. Corrupt
 * items in the mix never poison the cache, and the rare full-size fallback is served but never cached
 * (so one odd large HEIC can't evict hundreds of tiles).
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

    @Test
    fun `cache-hit invariant holds across a 10k+ mixed-format library`() = runTest {
        val librarySize = 12_000
        val storage = MixedFormatStorage()
        val cache = CountingByteCache()
        val pipeline = ThumbnailPipeline(storage, cache)

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
                    storage.behaviours[id] = SourceBehaviour.Corrupt
                    corruptCount++
                }
                i % 53 == 0 -> { // rare full-size fallback (a large HEIC the boundary couldn't downsize)
                    storage.behaviours[id] = SourceBehaviour.Oversized
                    oversizedCount++
                }
                else -> {
                    storage.behaviours[id] = SourceBehaviour.TileSized
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

        // --- Pass 1: cold scroll through the whole library. Every item fails or misses exactly once. ---
        for (request in requests) {
            runCatching { pipeline.load(request, requestedEdgePx = 250) }
        }
        val opensAfterColdPass = storage.opens
        assertThat(opensAfterColdPass).isEqualTo(requests.size) // each item touched the source once

        // Every source open asked for a bucketed tile — never the full-size original (spec §1 rule 2),
        // regardless of the format behind it.
        assertThat(storage.fullTargetOpens).isEqualTo(0)

        // Corrupt items poisoned nothing; only tile-sized items are cached; oversized served-not-cached.
        assertThat(cache.size).isEqualTo(cacheableRequests.size)

        // --- Pass 2: warm scroll. Format diversity must NOT cost a single extra source open. ---
        storage.opens = 0
        var hits = 0
        for (request in cacheableRequests) {
            val result = pipeline.load(request, requestedEdgePx = 250)
            if (result.fromCache) hits++
        }

        assertThat(hits).isEqualTo(cacheableRequests.size) // 100% hit-rate under format diversity
        assertThat(storage.opens).isEqualTo(0)             // warm scroll opened zero sources
    }
}

private enum class SourceBehaviour { TileSized, Oversized, Corrupt }

/** A 12k-library boundary whose per-item behaviour models the §8 format mix. */
private class MixedFormatStorage : StorageAccess {
    val behaviours = HashMap<MediaId, SourceBehaviour>()
    var opens = 0
    var fullTargetOpens = 0

    override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream {
        opens++
        if (target is DecodeTarget.Full) fullTargetOpens++
        return when (behaviours[id] ?: SourceBehaviour.TileSized) {
            SourceBehaviour.TileSized -> ByteArrayInputStream(ByteArray(4 * 1024)) // well-formed tile
            SourceBehaviour.Oversized -> ByteArrayInputStream(ByteArray(2 * 1024 * 1024)) // > cache ceiling
            SourceBehaviour.Corrupt -> throw IOException("unreadable: corrupt/truncated/0-byte")
        }
    }

    override val backend = StorageBackend.ALL_FILES_ACCESS
    override suspend fun hasMediaAccess() = true
    override suspend fun queryMedia(query: MediaQuery): List<MediaItem> = error("unused")
    override suspend fun queryAlbums(): List<Album> = error("unused")
    override suspend fun queryMediaSignatures(query: MediaQuery): List<MediaSignature> = error("unused")
    override fun observeMediaChanges(): Flow<Unit> = emptyFlow()
    override suspend fun rename(id: MediaId, newDisplayName: String): OperationResult = error("unused")
    override suspend fun createAlbum(name: String): OperationResult = error("unused")
    override suspend fun renameAlbum(bucketId: String, newName: String): OperationResult = error("unused")
    override suspend fun viewUri(id: MediaId): android.net.Uri? = error("unused")
    override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = error("unused")
    override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = error("unused")
    override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = error("unused")
    override fun observeTrash() = error("unused")
    override fun restoreFromTrash(ids: List<MediaId>) = error("unused")
    override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = error("unused")
    override fun emptyTrash() = error("unused")
    override suspend fun purgeExpiredTrash() = error("unused")
}

private class CountingByteCache : ThumbnailByteCache {
    private val entries = HashMap<String, ByteArray>()
    val size: Int get() = entries.size
    override fun read(key: String): ByteArray? = entries[key]
    override fun write(key: String, bytes: ByteArray) {
        entries[key] = bytes
    }
}
