package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import com.appblish.jgallery.core.model.TrashPolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * JVM coverage for the Recycle-Bin *policy* (spec §7.5): retention-metadata bookkeeping, restore,
 * permanent delete, empty-bin, the 30-day auto-purge, progress + "X done, Y failed" summaries, and
 * per-item failure isolation. The engine talks only to fake [TrashOps] / [TrashMetadataStore], so
 * every branch runs without a device — the MediaStore mechanics are validated by the instrumented test.
 */
class TrashEngineTest {

    private val dispatcher = StandardTestDispatcher()

    private fun engine(ops: FakeTrashOps, store: FakeTrashStore, now: () -> Long = { NOW }) =
        TrashEngine(ops, store, dispatcher, now)

    // --- move to trash ---

    @Test
    fun `moveToTrash trashes the item and records its origin path plus a trashed-at timestamp`() =
        runTest(dispatcher) {
            val ops = FakeTrashOps().apply {
                put("1", "a.jpg", bucketId = "b1", bucketName = "Camera", path = "DCIM/Camera/")
            }
            val store = FakeTrashStore()

            val summary = summaryOf(engine(ops, store).moveToTrash(listOf(MediaId("1"))).toList())

            assertThat(summary).isEqualTo(OperationResult(succeeded = 1, failed = 0))
            assertThat(ops.isTrashed("1")).isTrue()
            val entry = store.current().single()
            assertThat(entry.id).isEqualTo(MediaId("1"))
            assertThat(entry.originalBucketId).isEqualTo("b1")
            assertThat(entry.originalBucketName).isEqualTo("Camera")
            assertThat(entry.originalRelativePath).isEqualTo("DCIM/Camera/")
            assertThat(entry.trashedAtMillis).isEqualTo(NOW)
        }

    @Test
    fun `moveToTrash of a vanished item is isolated as a failure and records no metadata`() =
        runTest(dispatcher) {
            val ops = FakeTrashOps().apply { put("1", "ok.jpg") }
            val store = FakeTrashStore()

            val summary = summaryOf(
                engine(ops, store).moveToTrash(listOf(MediaId("missing"), MediaId("1"))).toList(),
            )

            assertThat(summary.succeeded).isEqualTo(1)
            assertThat(summary.failed).isEqualTo(1)
            assertThat(store.current().map { it.id.value }).containsExactly("1")
        }

    // --- restore ---

    @Test
    fun `restore un-trashes the item and drops it from the bin`() = runTest(dispatcher) {
        val ops = FakeTrashOps().apply { put("1", "a.jpg", trashed = true) }
        val store = FakeTrashStore().apply { seed(entryFor("1")) }

        val summary = summaryOf(engine(ops, store).restore(listOf(MediaId("1"))).toList())

        assertThat(summary).isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(ops.isTrashed("1")).isFalse()
        assertThat(store.current()).isEmpty()
    }

    @Test
    fun `a failed restore keeps the item in the bin so it can be retried`() = runTest(dispatcher) {
        val ops = FakeTrashOps().apply { put("1", "a.jpg", trashed = true); failRestore("1") }
        val store = FakeTrashStore().apply { seed(entryFor("1")) }

        val summary = summaryOf(engine(ops, store).restore(listOf(MediaId("1"))).toList())

        assertThat(summary.failed).isEqualTo(1)
        assertThat(store.current().map { it.id.value }).containsExactly("1") // still there
    }

    // --- permanent delete / empty ---

    @Test
    fun `deletePermanently removes the bytes and the metadata`() = runTest(dispatcher) {
        val ops = FakeTrashOps().apply { put("1", "a.jpg", trashed = true) }
        val store = FakeTrashStore().apply { seed(entryFor("1")) }

        val summary = summaryOf(engine(ops, store).deletePermanently(listOf(MediaId("1"))).toList())

        assertThat(summary).isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(ops.exists("1")).isFalse()
        assertThat(store.current()).isEmpty()
    }

    @Test
    fun `emptyTrash permanently deletes every recorded item and empties the bin`() =
        runTest(dispatcher) {
            val ops = FakeTrashOps().apply {
                put("1", "a.jpg", trashed = true)
                put("2", "b.jpg", trashed = true)
            }
            val store = FakeTrashStore().apply { seed(entryFor("1"), entryFor("2")) }

            val summary = summaryOf(engine(ops, store).emptyTrash().toList())

            assertThat(summary.succeeded).isEqualTo(2)
            assertThat(store.current()).isEmpty()
            assertThat(ops.exists("1")).isFalse()
            assertThat(ops.exists("2")).isFalse()
        }

    // --- auto-purge ---

    @Test
    fun `purgeExpired removes only items past the 30-day window and reports the count`() =
        runTest(dispatcher) {
            val ops = FakeTrashOps().apply {
                put("old", "old.jpg", trashed = true)
                put("fresh", "fresh.jpg", trashed = true)
            }
            val expiredAt = NOW - TrashPolicy.RETENTION_MILLIS - 1
            val store = FakeTrashStore().apply {
                seed(entryFor("old", trashedAt = expiredAt), entryFor("fresh", trashedAt = NOW))
            }

            val purged = engine(ops, store).purgeExpired()

            assertThat(purged).isEqualTo(1)
            assertThat(store.current().map { it.id.value }).containsExactly("fresh")
            assertThat(ops.exists("old")).isFalse()
            assertThat(ops.exists("fresh")).isTrue()
        }

    // --- reporting ---

    @Test
    fun `a bulk op emits per-item progress then exactly one terminal summary`() = runTest(dispatcher) {
        val ops = FakeTrashOps().apply { put("1", "a.jpg"); put("2", "b.jpg") }
        val store = FakeTrashStore()

        val events = engine(ops, store).moveToTrash(listOf(MediaId("1"), MediaId("2"))).toList()

        val progress = events.filterIsInstance<FileOperationEvent.InProgress>().map { it.progress }
        assertThat(progress.map { it.completed }).containsExactly(1, 2).inOrder()
        assertThat(progress.map { it.total }).containsExactly(2, 2)
        assertThat(events.count { it is FileOperationEvent.Completed }).isEqualTo(1)
        assertThat(events.last()).isInstanceOf(FileOperationEvent.Completed::class.java)
    }

    // --- helpers ---

    private fun summaryOf(events: List<FileOperationEvent>): OperationResult =
        (events.last() as FileOperationEvent.Completed).summary

    private fun entryFor(id: String, trashedAt: Long = NOW) = TrashEntry(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = MediaType.IMAGE,
        mimeType = "image/jpeg",
        originalBucketId = "b-$id",
        originalBucketName = "Camera",
        originalRelativePath = "DCIM/Camera/",
        trashedAtMillis = trashedAt,
        sizeBytes = 10L,
        width = 4,
        height = 3,
        durationMillis = 0L,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }

    /** In-memory [TrashOps]: an id -> row map with a trashed flag and per-op failure injection. */
    private class FakeTrashOps : TrashOps {
        private class Row(
            val name: String,
            val type: MediaType,
            val bucketId: String,
            val bucketName: String,
            val path: String,
            var trashed: Boolean,
        )

        private val rows = LinkedHashMap<String, Row>()
        private val restoreFailures = mutableSetOf<String>()

        fun put(
            id: String,
            name: String,
            type: MediaType = MediaType.IMAGE,
            bucketId: String = "b-$id",
            bucketName: String = "Camera",
            path: String = "DCIM/Camera/",
            trashed: Boolean = false,
        ) {
            rows[id] = Row(name, type, bucketId, bucketName, path, trashed)
        }

        fun failRestore(id: String) { restoreFailures += id }
        fun isTrashed(id: String) = rows[id]?.trashed == true
        fun exists(id: String) = rows.containsKey(id)

        override suspend fun describe(id: MediaId): TrashItemSnapshot? = rows[id.value]?.let { r ->
            TrashItemSnapshot(
                displayName = r.name,
                type = r.type,
                mimeType = if (r.type == MediaType.VIDEO) "video/mp4" else "image/jpeg",
                bucketId = r.bucketId,
                bucketName = r.bucketName,
                relativePath = r.path,
                sizeBytes = 10L,
                width = 4,
                height = 3,
                durationMillis = 0L,
            )
        }

        override suspend fun trash(id: MediaId): Boolean {
            val r = rows[id.value] ?: return false
            if (r.trashed) return false
            r.trashed = true
            return true
        }

        override suspend fun restore(id: MediaId): Boolean {
            if (id.value in restoreFailures) error("restore failed")
            val r = rows[id.value] ?: return false
            if (!r.trashed) return false
            r.trashed = false
            return true
        }

        override suspend fun delete(id: MediaId): Boolean = rows.remove(id.value) != null
    }

    /** In-memory [TrashMetadataStore] backed by a StateFlow, matching the DataStore impl's semantics. */
    private class FakeTrashStore : TrashMetadataStore {
        private val entries = MutableStateFlow<List<TrashEntry>>(emptyList())

        fun seed(vararg e: TrashEntry) { entries.value = e.toList() }

        override fun observe(): Flow<List<TrashEntry>> = entries.asStateFlow()
        override suspend fun current(): List<TrashEntry> = entries.value

        override suspend fun put(entry: TrashEntry) {
            entries.value = entries.value.filterNot { it.id == entry.id } + entry
        }

        override suspend fun remove(ids: Collection<MediaId>) {
            val drop = ids.toHashSet()
            entries.value = entries.value.filterNot { it.id in drop }
        }

        override suspend fun clear() { entries.value = emptyList() }
    }
}
