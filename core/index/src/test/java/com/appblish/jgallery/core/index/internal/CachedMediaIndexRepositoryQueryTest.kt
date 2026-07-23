package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.storage.MediaSignature
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StorageBackend
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.InputStream

/**
 * Repository-level regression for the [MediaQuery.ids] contract on the cached observe path (APP-543).
 *
 * The Favorites smart view queries `observeMedia(MediaQuery(ids = starredSet))` and relies on the bound
 * repository — [CachedMediaIndexRepository] — to restrict rows to those ids. The VM-fake tests only prove
 * the VM *builds* such a query; they can't catch a repository that drops the field. These exercise the
 * real filter: `ids = {a, c}` returns only a & c, and an empty `ids` matches nothing (→ Empty state),
 * while `ids = null` leaves folder/Photos views unrestricted.
 */
class CachedMediaIndexRepositoryQueryTest {

    @Test
    fun `ids restricts the result to exactly those ids`() = runTest {
        val repo = repository(item("a"), item("b"), item("c"))

        val result = repo.observeMedia(MediaQuery(ids = setOf(MediaId("a"), MediaId("c")))).first()

        assertThat(result.map { it.id.value }).containsExactly("a", "c")
    }

    @Test
    fun `an empty ids set matches nothing`() = runTest {
        val repo = repository(item("a"), item("b"))

        val result = repo.observeMedia(MediaQuery(ids = emptySet())).first()

        assertThat(result).isEmpty()
    }

    @Test
    fun `a null ids leaves the view unrestricted`() = runTest {
        val repo = repository(item("a"), item("b"))

        val result = repo.observeMedia(MediaQuery(ids = null)).first()

        assertThat(result.map { it.id.value }).containsExactly("a", "b")
    }

    // --- harness ---------------------------------------------------------------------------------

    private fun kotlinx.coroutines.test.TestScope.repository(vararg items: MediaItem): CachedMediaIndexRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = FakeStorage(items.toList())
        val store = FakeStore().apply { seed(items.toList()) }
        val synchronizer = MediaIndexSynchronizer(storage, store, dispatcher)
        return CachedMediaIndexRepository(
            store = store,
            synchronizer = synchronizer,
            storage = storage,
            syncScope = CoroutineScope(dispatcher),
            io = dispatcher,
        )
    }

    private fun item(id: String) = MediaItem(
        id = MediaId(id),
        displayName = "item-$id",
        type = MediaType.IMAGE,
        bucketId = "bucket",
        bucketName = "Bucket",
        dateTakenMillis = 1L,
        dateModifiedMillis = 1L,
        sizeBytes = 10L,
        width = 0,
        height = 0,
        durationMillis = 0L,
        mimeType = "image/jpeg",
    )

    /** Store pre-seeded with the given rows; its signatures match [FakeStorage] so sync is a no-op. */
    private class FakeStore : MediaIndexStore {
        private val rows = LinkedHashMap<MediaId, MediaItem>()
        private val media = MutableStateFlow<List<MediaItem>>(emptyList())

        fun seed(items: List<MediaItem>) {
            items.forEach { rows[it.id] = it }
            media.value = rows.values.toList()
        }

        override fun observeMedia(): Flow<List<MediaItem>> = media
        override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
        override suspend fun persistedSignatures(): List<IndexSignature> =
            rows.values.map { IndexSignature(it.id, it.dateModifiedMillis, it.sizeBytes, it.displayName) }
        override suspend fun upsert(items: List<MediaItem>) {
            items.forEach { rows[it.id] = it }; media.value = rows.values.toList()
        }
        override suspend fun delete(ids: Collection<MediaId>) {
            ids.forEach { rows.remove(it) }; media.value = rows.values.toList()
        }
        override suspend fun count(): Int = rows.size
    }

    /** Device mirror whose signatures match the seeded store, so the initial reconcile is a no-op. */
    private class FakeStorage(private val device: List<MediaItem>) : StorageAccess {
        override val backend = StorageBackend.ALL_FILES_ACCESS
        override suspend fun hasMediaAccess() = true
        override suspend fun queryMedia(query: MediaQuery): List<MediaItem> = device
        override suspend fun queryMediaSignatures(query: MediaQuery): List<MediaSignature> =
            device.map { MediaSignature(it.id, it.dateModifiedMillis, it.sizeBytes, it.displayName) }
        override fun observeMediaChanges(): Flow<Unit> = emptyFlow()
        override suspend fun queryAlbums(): List<Album> = error("unused")
        override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream = error("unused")
        override suspend fun rename(id: MediaId, newDisplayName: String) = error("unused")
        override suspend fun createAlbum(name: String) = error("unused")
        override suspend fun renameAlbum(bucketId: String, newName: String) = error("unused")
        override suspend fun viewUri(id: MediaId): android.net.Uri? = error("unused")
        override fun copy(ids: List<MediaId>, destinationBucketId: String) = error("unused")
        override fun move(ids: List<MediaId>, destinationBucketId: String) = error("unused")
        override fun exportCopy(ids: List<MediaId>, treeUri: android.net.Uri) = error("unused")
        override fun copyToNewAlbum(ids: List<MediaId>, name: String) = error("unused")
        override fun moveToNewAlbum(ids: List<MediaId>, name: String) = error("unused")
        override suspend fun beginCapture(albumName: String, kind: com.appblish.jgallery.core.model.CaptureKind) = error("unused")
        override suspend fun sweepOrphanedCaptures() = error("unused")
        override fun moveToTrash(ids: List<MediaId>) = error("unused")
        override fun observeTrash() = error("unused")
        override fun restoreFromTrash(ids: List<MediaId>) = error("unused")
        override fun deletePermanently(ids: List<MediaId>) = error("unused")
        override fun emptyTrash() = error("unused")
        override suspend fun purgeExpiredTrash() = error("unused")
    }
}
