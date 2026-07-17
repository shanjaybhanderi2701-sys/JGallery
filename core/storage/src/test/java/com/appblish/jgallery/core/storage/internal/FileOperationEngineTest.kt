package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * JVM unit coverage for the *policy* half of the file-operation core (spec §7): collision handling,
 * chunked streaming, cooperative cancellation, and the "X done, Y failed" summary. The engine talks
 * only to a fake [StorageOps], so every branch is exercised without a device — the platform
 * ([MediaStoreStorageOps]) is validated separately by the module's instrumented test.
 */
class FileOperationEngineTest {

    // --- copy ---

    @Test
    fun `copy leaves originals in place, writes exact bytes, and reports a full-success summary`() =
        runTest {
            val ops = FakeStorageOps().apply {
                put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 10), bucket = "src")
                put("2", "b.jpg", "image/jpeg", bytesOf(2, size = 20), bucket = "src")
            }
            val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler), bufferSize = 4)

            val events = engine.copy(listOf(MediaId("1"), MediaId("2")), "dst").toList()

            // Originals remain in source.
            assertThat(ops.namesInBucket("src")).containsExactly("a.jpg", "b.jpg")
            // Copies exist in destination with byte-for-byte identical content.
            assertThat(ops.bytesInBucket("dst", "a.jpg")).isEqualTo(bytesOf(1, size = 10))
            assertThat(ops.bytesInBucket("dst", "b.jpg")).isEqualTo(bytesOf(2, size = 20))
            assertThat(summaryOf(events)).isEqualTo(OperationResult(succeeded = 2, failed = 0))
        }

    @Test
    fun `copy resolves a destination collision by suffixing, preserving the extension`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "photo.jpg", "image/jpeg", bytesOf(9, size = 8), bucket = "src")
            put("blocker", "photo.jpg", "image/jpeg", bytesOf(0, size = 1), bucket = "dst")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        engine.copy(listOf(MediaId("1")), "dst").toList()

        assertThat(ops.namesInBucket("dst")).containsExactly("photo.jpg", "photo (1).jpg")
    }

    @Test
    fun `copying two same-named sources into one bucket reserves distinct names within the batch`() =
        runTest {
            val ops = FakeStorageOps().apply {
                put("1", "dup.png", "image/png", bytesOf(1, size = 4), bucket = "src")
                put("2", "dup.png", "image/png", bytesOf(2, size = 4), bucket = "other")
            }
            val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

            engine.copy(listOf(MediaId("1"), MediaId("2")), "dst").toList()

            assertThat(ops.namesInBucket("dst")).containsExactly("dup.png", "dup (1).png")
        }

    @Test
    fun `copy streams a large file through a tiny buffer without corrupting the bytes`() = runTest {
        val big = bytesOf(seed = 7, size = 1_000_003) // not a multiple of the buffer size
        val ops = FakeStorageOps().apply { put("1", "movie.mp4", "video/mp4", big, bucket = "src") }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler), bufferSize = 64)

        engine.copy(listOf(MediaId("1")), "dst").toList()

        assertThat(ops.bytesInBucket("dst", "movie.mp4")).isEqualTo(big)
    }

    @Test
    fun `copy emits per-item progress then exactly one terminal summary`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 4), bucket = "src")
            put("2", "b.jpg", "image/jpeg", bytesOf(2, size = 4), bucket = "src")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val events = engine.copy(listOf(MediaId("1"), MediaId("2")), "dst").toList()

        val progress = events.filterIsInstance<FileOperationEvent.InProgress>().map { it.progress }
        assertThat(progress.map { it.completed }).containsExactly(1, 2).inOrder()
        assertThat(progress.map { it.total }).containsExactly(2, 2)
        assertThat(events.count { it is FileOperationEvent.Completed }).isEqualTo(1)
        assertThat(events.last()).isInstanceOf(FileOperationEvent.Completed::class.java)
    }

    @Test
    fun `a missing source is isolated as a failure and does not abort the rest of the batch`() =
        runTest {
            val ops = FakeStorageOps().apply {
                put("1", "ok.jpg", "image/jpeg", bytesOf(1, size = 4), bucket = "src")
                // id "2" intentionally absent -> displayName returns null
            }
            val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

            val events = engine.copy(listOf(MediaId("2"), MediaId("1")), "dst").toList()

            val summary = summaryOf(events)
            assertThat(summary.succeeded).isEqualTo(1)
            assertThat(summary.failed).isEqualTo(1)
            assertThat(summary.failures.single().id).isEqualTo(MediaId("2"))
            assertThat(ops.namesInBucket("dst")).containsExactly("ok.jpg")
        }

    // --- exportCopy ("Save a copy" into a user-picked SAF tree — G2 · APP-549) ---

    @Test
    fun `exportCopy streams exact bytes into the tree, leaves originals, and reports full success`() =
        runTest {
            val tree = "content://tree/downloads"
            val ops = FakeStorageOps().apply {
                put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 10), bucket = "src")
                put("2", "b.mp4", "video/mp4", bytesOf(2, size = 1_000_003), bucket = "src")
            }
            val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler), bufferSize = 64)

            val events = engine.exportCopy(listOf(MediaId("1"), MediaId("2")), tree).toList()

            // Originals untouched (export copies, never moves).
            assertThat(ops.namesInBucket("src")).containsExactly("a.jpg", "b.mp4")
            // Exact bytes landed in the picked tree, even for a large file through a tiny buffer.
            assertThat(ops.bytesInBucket(tree, "a.jpg")).isEqualTo(bytesOf(1, size = 10))
            assertThat(ops.bytesInBucket(tree, "b.mp4")).isEqualTo(bytesOf(2, size = 1_000_003))
            assertThat(summaryOf(events)).isEqualTo(OperationResult(succeeded = 2, failed = 0))
        }

    @Test
    fun `exportCopy suffixes a name that already exists in the destination tree`() = runTest {
        val tree = "content://tree/downloads"
        val ops = FakeStorageOps().apply {
            put("1", "photo.jpg", "image/jpeg", bytesOf(9, size = 8), bucket = "src")
            put("blocker", "photo.jpg", "image/jpeg", bytesOf(0, size = 1), bucket = tree)
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        engine.exportCopy(listOf(MediaId("1")), tree).toList()

        // The user's existing file is never clobbered — the export lands beside it.
        assertThat(ops.namesInBucket(tree)).containsExactly("photo.jpg", "photo (1).jpg")
    }

    @Test
    fun `exportCopy isolates a missing source as a failure without aborting the batch`() = runTest {
        val tree = "content://tree/downloads"
        val ops = FakeStorageOps().apply {
            put("1", "ok.jpg", "image/jpeg", bytesOf(1, size = 4), bucket = "src")
            // id "2" absent -> reported failed, the rest still exports.
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val events = engine.exportCopy(listOf(MediaId("2"), MediaId("1")), tree).toList()

        val summary = summaryOf(events)
        assertThat(summary.succeeded).isEqualTo(1)
        assertThat(summary.failed).isEqualTo(1)
        assertThat(summary.failures.single().id).isEqualTo(MediaId("2"))
        assertThat(ops.namesInBucket(tree)).containsExactly("ok.jpg")
    }

    // --- move ---

    @Test
    fun `move copies to the destination and removes the source`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "clip.mp4", "video/mp4", bytesOf(3, size = 12), bucket = "src")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler), bufferSize = 5)

        val events = engine.move(listOf(MediaId("1")), "dst").toList()

        assertThat(ops.namesInBucket("src")).isEmpty()
        assertThat(ops.bytesInBucket("dst", "clip.mp4")).isEqualTo(bytesOf(3, size = 12))
        assertThat(summaryOf(events)).isEqualTo(OperationResult(succeeded = 1, failed = 0))
    }

    // --- trash / delete ---

    @Test
    fun `moveToTrash and deletePermanently both remove items and summarise the batch`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 2), bucket = "src")
            put("2", "b.jpg", "image/jpeg", bytesOf(2, size = 2), bucket = "src")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val trashed = summaryOf(engine.moveToTrash(listOf(MediaId("1"))).toList())
        val deleted = summaryOf(engine.deletePermanently(listOf(MediaId("2"))).toList())

        assertThat(trashed).isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(deleted).isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(ops.namesInBucket("src")).isEmpty()
    }

    // --- rename ---

    @Test
    fun `rename succeeds, and rejects a blank name or a vanished source`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "old.jpg", "image/jpeg", bytesOf(1, size = 2), bucket = "src")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        assertThat(engine.rename(MediaId("1"), "new.jpg"))
            .isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(ops.namesInBucket("src")).containsExactly("new.jpg")

        assertThat(engine.rename(MediaId("1"), "   ").failed).isEqualTo(1)
        assertThat(engine.rename(MediaId("missing"), "x.jpg").failed).isEqualTo(1)
    }

    // --- rename album (entity) ---

    @Test
    fun `renameAlbum moves every member into the renamed folder, preserving the parent`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 2), bucket = "trip")
            put("2", "b.jpg", "image/jpeg", bytesOf(2, size = 2), bucket = "trip")
        }
        // Default relativePath is "Pictures/<bucket>/" → parent "Pictures".
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val result = engine.renameAlbum("trip", "Holiday")

        assertThat(result).isEqualTo(OperationResult(succeeded = 2, failed = 0))
        assertThat(ops.relativePathOf("1")).isEqualTo("Pictures/Holiday/")
        assertThat(ops.relativePathOf("2")).isEqualTo("Pictures/Holiday/")
    }

    @Test
    fun `renameAlbum is a no-op success when the name is unchanged`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 2), bucket = "trip")
        }
        // Folder leaf equals the new name → nothing to move.
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val result = engine.renameAlbum("trip", "trip")

        assertThat(result).isEqualTo(OperationResult(succeeded = 1, failed = 0))
        assertThat(ops.relativePathOf("1")).isEqualTo("Pictures/trip/") // untouched
    }

    @Test
    fun `renameAlbum rejects an invalid name without touching any row`() = runTest {
        val ops = FakeStorageOps().apply {
            put("1", "a.jpg", "image/jpeg", bytesOf(1, size = 2), bucket = "trip")
        }
        val engine = FileOperationEngine(ops, StandardTestDispatcher(testScheduler))

        val result = engine.renameAlbum("trip", "bad/name")

        assertThat(result.failed).isEqualTo(1)
        assertThat(result.succeeded).isEqualTo(0)
        assertThat(ops.relativePathOf("1")).isEqualTo("Pictures/trip/") // untouched
    }

    @Test
    fun `renameAlbum fails when the album has no rows`() = runTest {
        val engine = FileOperationEngine(FakeStorageOps(), StandardTestDispatcher(testScheduler))

        assertThat(engine.renameAlbum("ghost", "New").failed).isEqualTo(1)
    }

    // --- cancellation ---

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // advanceUntilIdle
    @Test
    fun `cancelling collection aborts the in-flight copy and emits no terminal summary`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ops = FakeStorageOps().apply {
            put("1", "big.jpg", "image/jpeg", bytesOf(1, size = 4096), bucket = "src")
        }
        val jobHolder = arrayOfNulls<Job>(1)
        // Cancel the collector the moment the first chunk is read; the engine's next ensureActive()
        // between chunks must then throw and roll back the partial destination.
        ops.hookFirstRead("1") { jobHolder[0]?.cancel() }
        val engine = FileOperationEngine(ops, dispatcher, bufferSize = 8)

        val events = mutableListOf<FileOperationEvent>()
        val job = launch(dispatcher, start = CoroutineStart.LAZY) {
            engine.copy(listOf(MediaId("1")), "dst").collect { events += it }
        }
        jobHolder[0] = job
        job.start()
        advanceUntilIdle()

        assertThat(events).isEmpty()          // no InProgress and no Completed on cancellation
        assertThat(ops.abortedSinks).isEqualTo(1)
        assertThat(ops.committedSinks).isEqualTo(0)
        assertThat(ops.namesInBucket("dst")).isEmpty() // partial copy never published
    }

    // --- helpers ---

    private fun summaryOf(events: List<FileOperationEvent>): OperationResult =
        (events.last() as FileOperationEvent.Completed).summary

    private fun bytesOf(seed: Int, size: Int): ByteArray =
        ByteArray(size) { i -> ((seed + i) % 251).toByte() }

    /** In-memory [StorageOps]: a bucket -> files map, so the engine's policy is fully observable. */
    private class FakeStorageOps : StorageOps {
        private class Entry(
            var name: String,
            val mime: String,
            val bytes: ByteArray,
            val bucketId: String,
            var relativePath: String = "Pictures/$bucketId/",
        )

        private val items = LinkedHashMap<String, Entry>()
        private val firstReadHooks = mutableMapOf<String, () -> Unit>()
        private var idSeq = 1000
        var committedSinks = 0
            private set
        var abortedSinks = 0
            private set

        fun put(id: String, name: String, mime: String, bytes: ByteArray, bucket: String) {
            items[id] = Entry(name, mime, bytes, bucket)
        }

        fun relativePathOf(id: String): String? = items[id]?.relativePath

        fun hookFirstRead(id: String, action: () -> Unit) {
            firstReadHooks[id] = action
        }

        fun bytesInBucket(bucketId: String, name: String): ByteArray? =
            items.values.firstOrNull { it.bucketId == bucketId && it.name == name }?.bytes

        override suspend fun displayName(id: MediaId): String? = items[id.value]?.name

        override suspend fun mimeType(id: MediaId): String =
            items[id.value]?.mime ?: "application/octet-stream"

        override suspend fun namesInBucket(destinationBucketId: String): Set<String> =
            items.values.filter { it.bucketId == destinationBucketId }.map { it.name }.toSet()

        override suspend fun openInput(id: MediaId): InputStream {
            val entry = items[id.value] ?: error("no source for $id")
            val hook = firstReadHooks[id.value]
            return if (hook != null) HookInputStream(entry.bytes, hook) else ByteArrayInputStream(entry.bytes)
        }

        override suspend fun createSink(destinationBucketId: String, name: String, mimeType: String): Sink =
            FakeSink(destinationBucketId, name, mimeType)

        // Export (APP-549) treats the SAF tree uri as just another destination key, so the fake can reuse
        // the same item map: names under a tree = items whose bucket equals the tree uri; a tree sink is
        // a FakeSink keyed by the tree uri. This lets the pure engine test exercise export end-to-end.
        override suspend fun namesInTree(treeUri: String): Set<String> =
            items.values.filter { it.bucketId == treeUri }.map { it.name }.toSet()

        override suspend fun createTreeSink(treeUri: String, name: String, mimeType: String): Sink =
            FakeSink(treeUri, name, mimeType)

        override suspend fun rename(id: MediaId, newName: String): Boolean {
            val entry = items[id.value] ?: return false
            entry.name = newName
            return true
        }

        override suspend fun trash(id: MediaId): Boolean = items.remove(id.value) != null

        override suspend fun delete(id: MediaId): Boolean = items.remove(id.value) != null

        override suspend fun albumRelativePath(bucketId: String): String? =
            items.values.firstOrNull { it.bucketId == bucketId }?.relativePath

        override suspend fun idsInBucket(bucketId: String): List<MediaId> =
            items.filterValues { it.bucketId == bucketId }.keys.map { MediaId(it) }

        override suspend fun moveToFolder(id: MediaId, relativePath: String): Boolean {
            val entry = items[id.value] ?: return false
            entry.relativePath = relativePath
            return true
        }

        private inner class FakeSink(
            private val bucketId: String,
            private val name: String,
            private val mime: String,
        ) : Sink {
            private val buffer = ByteArrayOutputStream()
            override val output: java.io.OutputStream = buffer

            override suspend fun commit() {
                items[(idSeq++).toString()] = Entry(name, mime, buffer.toByteArray(), bucketId)
                committedSinks++
            }

            override suspend fun abort() {
                abortedSinks++
            }
        }
    }

    /** A stream that fires [onFirstRead] before delivering its first chunk (drives cancellation). */
    private class HookInputStream(data: ByteArray, private val onFirstRead: () -> Unit) : InputStream() {
        private val delegate = ByteArrayInputStream(data)
        private var fired = false

        override fun read(): Int = delegate.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!fired) {
                fired = true
                onFirstRead()
            }
            return delegate.read(b, off, len)
        }
    }
}
