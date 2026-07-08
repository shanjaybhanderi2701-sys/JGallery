package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.MediaQuery
import com.appblish.jgallery.core.storage.MediaSignature
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StorageBackend
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.InputStream

class MediaIndexSynchronizerTest {

    @Test
    fun `initial sync fully populates an empty cache`() = runTest {
        val storage = FakeStorageAccess(mutableListOf(item("1"), item("2")))
        val store = FakeStore()
        val sync = MediaIndexSynchronizer(storage, store, UnconfinedTestDispatcher(testScheduler))

        val delta = sync.sync()

        assertThat(store.count()).isEqualTo(2)
        assertThat(delta.changedIds).containsExactly(MediaId("1"), MediaId("2"))
    }

    @Test
    fun `incremental sync re-reads only the changed row`() = runTest {
        val storage = FakeStorageAccess(mutableListOf(item("1", modified = 1L), item("2", modified = 1L)))
        val store = FakeStore()
        val sync = MediaIndexSynchronizer(storage, store, UnconfinedTestDispatcher(testScheduler))
        sync.sync() // prime
        storage.fullReadIds.clear()

        // Item 2 is edited on the device; item 1 is untouched.
        storage.device[1] = item("2", modified = 2L, name = "edited")
        val delta = sync.sync()

        assertThat(delta.changedIds).containsExactly(MediaId("2"))
        assertThat(delta.deletedIds).isEmpty()
        // The whole point of "incremental": the unchanged row is never re-read from the provider.
        assertThat(storage.fullReadIds).containsExactly(MediaId("2"))
        assertThat(store.snapshot().first { it.id == MediaId("2") }.displayName).isEqualTo("edited")
    }

    @Test
    fun `rows deleted on the device are dropped from the cache`() = runTest {
        val storage = FakeStorageAccess(mutableListOf(item("1"), item("2"), item("3")))
        val store = FakeStore()
        val sync = MediaIndexSynchronizer(storage, store, UnconfinedTestDispatcher(testScheduler))
        sync.sync() // prime with 3

        storage.device.removeAll { it.id == MediaId("2") || it.id == MediaId("3") }
        val delta = sync.sync()

        assertThat(delta.deletedIds).containsExactly(MediaId("2"), MediaId("3"))
        assertThat(store.snapshot().map { it.id }).containsExactly(MediaId("1"))
    }

    @Test
    fun `no-op sync writes nothing when the library is unchanged`() = runTest {
        val storage = FakeStorageAccess(mutableListOf(item("1"), item("2")))
        val store = FakeStore()
        val sync = MediaIndexSynchronizer(storage, store, UnconfinedTestDispatcher(testScheduler))
        sync.sync()
        storage.fullReadIds.clear()

        val delta = sync.sync()

        assertThat(delta.isEmpty).isTrue()
        assertThat(storage.fullReadIds).isEmpty()
    }
}

private fun item(id: String, modified: Long = 1L, size: Long = 10L, name: String = "item-$id") =
    MediaItem(
        id = MediaId(id),
        displayName = name,
        type = MediaType.IMAGE,
        bucketId = "bucket",
        bucketName = "Bucket",
        dateTakenMillis = id.toLong(),
        dateModifiedMillis = modified,
        sizeBytes = size,
        width = 0,
        height = 0,
        durationMillis = 0L,
        mimeType = "image/jpeg",
    )

/** In-memory [StorageAccess] standing in for MediaStore; records which ids get a full-column read. */
private class FakeStorageAccess(val device: MutableList<MediaItem>) : StorageAccess {

    val fullReadIds = mutableListOf<MediaId>()

    override val backend = StorageBackend.ALL_FILES_ACCESS
    override suspend fun hasMediaAccess() = true

    override suspend fun queryMedia(query: MediaQuery): List<MediaItem> {
        val ids = query.ids
        val rows = if (ids == null) device.toList() else device.filter { it.id in ids }
        fullReadIds += rows.map { it.id }
        return rows
    }

    override suspend fun queryMediaSignatures(query: MediaQuery): List<MediaSignature> =
        device.map { MediaSignature(it.id, it.dateModifiedMillis, it.sizeBytes) }

    override fun observeMediaChanges(): Flow<Unit> = emptyFlow()

    override suspend fun queryAlbums(): List<Album> = error("unused")
    override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream = error("unused")
    override suspend fun rename(id: MediaId, newDisplayName: String) = error("unused")
    override suspend fun createAlbum(name: String) = error("unused")
    override fun copy(ids: List<MediaId>, destinationBucketId: String) = error("unused")
    override fun move(ids: List<MediaId>, destinationBucketId: String) = error("unused")
    override fun moveToTrash(ids: List<MediaId>) = error("unused")
    override fun deletePermanently(ids: List<MediaId>) = error("unused")
}

/** In-memory [MediaIndexStore] for verifying persistence effects. */
private class FakeStore : MediaIndexStore {

    private val rows = LinkedHashMap<MediaId, MediaItem>()
    private val media = MutableStateFlow<List<MediaItem>>(emptyList())

    fun snapshot(): List<MediaItem> = rows.values.toList()

    override fun observeMedia(): Flow<List<MediaItem>> = media
    override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())

    override suspend fun persistedSignatures(): List<IndexSignature> =
        rows.values.map { IndexSignature(it.id, it.dateModifiedMillis, it.sizeBytes) }

    override suspend fun upsert(items: List<MediaItem>) {
        items.forEach { rows[it.id] = it }
        media.value = snapshot()
    }

    override suspend fun delete(ids: Collection<MediaId>) {
        ids.forEach { rows.remove(it) }
        media.value = snapshot()
    }

    override suspend fun count(): Int = rows.size
}
