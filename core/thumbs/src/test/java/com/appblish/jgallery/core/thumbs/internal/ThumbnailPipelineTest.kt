package com.appblish.jgallery.core.thumbs.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.model.MediaQuery
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
import java.io.InputStream

/**
 * The cache discipline that makes the 10k-scroll gate achievable: hits never touch the source,
 * misses only ever ask the boundary for tile-sized bytes, and versions invalidate structurally.
 */
class ThumbnailPipelineTest {

    private val request = ThumbnailRequest(MediaId("7"), versionMillis = 1_000L)

    @Test
    fun `disk hit returns cached bytes without opening the source`() = runTest {
        val storage = FakeStorage(payload = null) // any source open would throw
        val cache = FakeByteCache()
        cache.write(thumbnailDiskKey(request, 256), byteArrayOf(1, 2, 3))

        val result = ThumbnailPipeline(storage, cache).load(request, requestedEdgePx = 250)

        assertThat(result.fromCache).isTrue()
        assertThat(result.bytes).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(storage.openedTargets).isEmpty()
    }

    @Test
    fun `miss requests a bucketed thumbnail target — never the full-size source`() = runTest {
        val storage = FakeStorage(payload = byteArrayOf(9))
        val pipeline = ThumbnailPipeline(storage, FakeByteCache())

        val result = pipeline.load(request, requestedEdgePx = 250)

        assertThat(result.fromCache).isFalse()
        assertThat(storage.openedTargets).containsExactly(DecodeTarget.Thumbnail(256))
    }

    @Test
    fun `miss writes through, so the next load is a hit`() = runTest {
        val storage = FakeStorage(payload = byteArrayOf(9))
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(storage, cache)

        pipeline.load(request, requestedEdgePx = 250)
        val second = pipeline.load(request, requestedEdgePx = 250)

        assertThat(second.fromCache).isTrue()
        assertThat(storage.openedTargets).hasSize(1) // only the first load touched the source
    }

    @Test
    fun `a version bump misses the old entry — stale thumbnails are never served`() = runTest {
        val storage = FakeStorage(payload = byteArrayOf(9))
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(storage, cache)
        pipeline.load(request, requestedEdgePx = 250)

        val modified = pipeline.load(request.copy(versionMillis = 2_000L), requestedEdgePx = 250)

        assertThat(modified.fromCache).isFalse()
        assertThat(storage.openedTargets).hasSize(2)
    }

    @Test
    fun `oversized payloads (full-size fallback) are served but not cached`() = runTest {
        val big = ByteArray(2 * 1024 * 1024)
        val storage = FakeStorage(payload = big)
        val cache = FakeByteCache()
        val pipeline = ThumbnailPipeline(storage, cache)

        val result = pipeline.load(request, requestedEdgePx = 250)

        assertThat(result.bytes).hasLength(big.size)
        assertThat(cache.entries).isEmpty()
    }

    @Test
    fun `cache failures never fail a load that has its bytes`() = runTest {
        val storage = FakeStorage(payload = byteArrayOf(9))
        val pipeline = ThumbnailPipeline(storage, ThrowingByteCache())

        val result = pipeline.load(request, requestedEdgePx = 250)

        assertThat(result.bytes).isEqualTo(byteArrayOf(9))
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

/** Serves [payload] from openStream and records every requested [DecodeTarget]; null payload = must not be opened. */
private class FakeStorage(private val payload: ByteArray?) : StorageAccess {

    val openedTargets = mutableListOf<DecodeTarget>()

    override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream {
        openedTargets += target
        return ByteArrayInputStream(payload ?: error("this load must not open the source"))
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
