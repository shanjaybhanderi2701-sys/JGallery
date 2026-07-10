package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
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
import java.io.IOException
import java.io.InputStream

/**
 * W3-E16 at-scale robustness (spec §1 rules, §8, §11 DoD). A 10,000+ mixed-format library will
 * contain corrupt, truncated and 0-byte files; the thumbnail path must fail each one *predictably*
 * so the Coil fetcher above it can render the W3-06/W3-01 placeholder — without ever poisoning the
 * shared disk cache (which would then serve garbage to a healthy neighbour) or holding a full-size
 * decode in memory. These are the regression guards behind "corrupt files never crash the thumbnail
 * path" and "cache-hit behaviour holds under format diversity".
 */
class ThumbnailPipelineRobustnessTest {

    private val request = ThumbnailRequest(MediaId("42"), versionMillis = 1_000L)

    @Test
    fun `a source that cannot be opened surfaces the failure and writes nothing to the cache`() = runTest {
        val storage = FailingStorage { throw IOException("open failed: corrupt/0-byte file") }
        val cache = RecordingByteCache()

        val thrown = runCatching { ThumbnailPipeline(storage, cache).load(request, 250) }.exceptionOrNull()

        // The exception is the fetcher's signal to render the placeholder; the important guarantee is
        // that a broken source never leaves a cache entry a healthy tile could later be served.
        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `a stream that truncates mid-read never caches a partial payload`() = runTest {
        val storage = FailingStorage { TruncatedStream(bytesBeforeFailure = 8) }
        val cache = RecordingByteCache()

        runCatching { ThumbnailPipeline(storage, cache).load(request, 250) }

        // A half-read truncated file must not be written through — the next load re-attempts cleanly
        // rather than inheriting a corrupt cached tile.
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `one corrupt tile does not disturb the cached bytes of a healthy neighbour`() = runTest {
        val cache = RecordingByteCache()
        val healthy = ThumbnailRequest(MediaId("healthy"), versionMillis = 1_000L)

        // Healthy neighbour caches normally.
        ThumbnailPipeline(FailingStorage { OkStream(byteArrayOf(1, 2, 3)) }, cache).load(healthy, 250)
        val healthyKey = thumbnailDiskKey(healthy, 256)
        assertThat(cache.entries).containsKey(healthyKey)

        // A corrupt item processed against the SAME cache must not evict or overwrite it.
        runCatching { ThumbnailPipeline(FailingStorage { throw IOException("boom") }, cache).load(request, 250) }

        assertThat(cache.entries[healthyKey]).isEqualTo(byteArrayOf(1, 2, 3))
    }
}

/** Reads exactly [bytesBeforeFailure] bytes, then throws — models a truncated/damaged file. */
private class TruncatedStream(private var bytesBeforeFailure: Int) : InputStream() {
    override fun read(): Int {
        if (bytesBeforeFailure-- <= 0) throw IOException("unexpected end of stream")
        return 0
    }
}

private class OkStream(bytes: ByteArray) : InputStream() {
    private val delegate = bytes.inputStream()
    override fun read(): Int = delegate.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
}

private class RecordingByteCache : ThumbnailByteCache {
    val entries = mutableMapOf<String, ByteArray>()
    override fun read(key: String): ByteArray? = entries[key]
    override fun write(key: String, bytes: ByteArray) {
        entries[key] = bytes
    }
}

/** Opens whatever [source] produces (or throws), recording nothing else the pipeline needs. */
private class FailingStorage(private val source: () -> InputStream) : StorageAccess {
    override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream = source()

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
