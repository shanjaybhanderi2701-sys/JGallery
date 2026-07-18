package com.appblish.jgallery.core.storage.internal

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * On-device guard for "Save a copy" — export into a user-picked SAF folder (G2 · APP-549), the
 * permanent deterministic replacement for the flaky DocumentsUI folder-pick QA could not drive
 * (APP-566 / APP-571). Every assertion runs the REAL `DocumentFile` + `ContentResolver` write path
 * of [MediaStoreStorageOps.createTreeSink] / `DocumentFileSink` / [MediaStoreStorageOps.namesInTree]
 * against a genuine [StubDocumentsProvider] tree — not a Fake `StorageOps`, so it cannot false-green
 * on the provider mechanics (createDocument / openDocument / deleteDocument / queryChildDocuments).
 *
 * Sources are app-owned MediaStore rows (created here, torn down in [tearDown]); the export
 * destination is the provider's on-device tree, cleared before and after each test. It needs no All
 * Files Access — an app may always contribute and read its own new entries under scoped storage.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreExportTest {

    private lateinit var context: Context
    private lateinit var storage: MediaStoreStorageAccess
    private lateinit var ops: MediaStoreStorageOps
    private lateinit var treeUri: Uri
    private lateinit var treeDir: File
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = MediaStoreStorageAccess(context, context.contentResolver, Dispatchers.IO, InMemoryTrashStore())
        ops = MediaStoreStorageOps(context, context.contentResolver, Dispatchers.IO)
        treeUri = StubDocumentsProvider.treeUri(context)
        treeDir = StubDocumentsProvider.rootDir(context).apply {
            deleteRecursively() // a prior (crashed/retried) run must not seed collisions
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        val resolver = context.contentResolver
        createdUris.forEach { runCatching { resolver.delete(it, null, null) } }
        runCatching { treeDir.deleteRecursively() }
    }

    /** 1 — round-trip: a photo AND a video land in the tree with byte-identical contents. */
    @Test
    fun exportCopiesPhotoAndVideoWithIdenticalBytes() = runBlocking {
        val imageBytes = ByteArray(2_000_003) { (it % 251).toByte() } // > 64 KiB buffer, non-aligned
        val videoBytes = ByteArray(1_500_001) { ((it * 7) % 253).toByte() }
        val imageName = uniqueName("roundtrip", "jpg")
        val videoName = uniqueName("roundtrip", "mp4")
        val imageId = seedImage(imageName, imageBytes)
        val videoId = seedVideo(videoName, videoBytes)

        val events = storage.exportCopy(listOf(imageId, videoId), treeUri).toList()

        assertEquals(OperationResult(succeeded = 2, failed = 0), summaryOf(events))
        assertArrayEquals("exported photo bytes must equal source", imageBytes, File(treeDir, imageName).readBytes())
        assertArrayEquals("exported video bytes must equal source", videoBytes, File(treeDir, videoName).readBytes())
    }

    /**
     * 2 — collision auto-suffix via the ENGINE reservation: exporting the same selection twice yields
     * `name (1).ext` with the first copy left untouched. Exercises `namesInTree` + `resolveCollision`
     * feeding a non-colliding name to `createTreeSink`.
     */
    @Test
    fun reExportingSameSelectionAutoSuffixesWithoutClobber() = runBlocking {
        val bytes = ByteArray(120_000) { (it % 97).toByte() }
        val name = uniqueName("engcol", "jpg")
        val id = seedImage(name, bytes)

        assertEquals(OperationResult(1, 0), summaryOf(storage.exportCopy(listOf(id), treeUri).toList()))
        assertEquals(OperationResult(1, 0), summaryOf(storage.exportCopy(listOf(id), treeUri).toList()))

        val base = name.removeSuffix(".jpg")
        assertArrayEquals("first copy must be untouched", bytes, File(treeDir, name).readBytes())
        assertArrayEquals("second copy is auto-suffixed, no clobber", bytes, File(treeDir, "$base (1).jpg").readBytes())
    }

    /**
     * 2 — collision auto-suffix, PROVIDER half: create the same name twice *directly* through
     * `createTreeSink` (bypassing the engine's reservation) → the DocumentFile provider itself must
     * auto-suffix rather than overwrite, so a copy can never clobber a user's existing file.
     */
    @Test
    fun createTreeSinkTwiceForSameNameNeverClobbers() = runBlocking {
        val name = uniqueName("provcol", "jpg")
        val first = ByteArray(64) { 1 }
        val second = ByteArray(128) { 2 }

        writeAndCommit(ops.createTreeSink(treeUri.toString(), name, "image/jpeg"), first)
        writeAndCommit(ops.createTreeSink(treeUri.toString(), name, "image/jpeg"), second)

        val base = name.removeSuffix(".jpg")
        assertArrayEquals("provider must keep the first document intact", first, File(treeDir, name).readBytes())
        assertArrayEquals("provider must auto-suffix the second", second, File(treeDir, "$base (1).jpg").readBytes())
    }

    /**
     * 3 — abort deletes the partial: a source that fails MID-STREAM has its partially written tree
     * document removed (`DocumentFileSink.abort → doc.delete()`), while the other item's copy remains.
     */
    @Test
    fun midStreamFailureDeletesPartialDocumentAndKeepsGoodItem() = runBlocking {
        val goodBytes = ByteArray(300_000) { (it % 251).toByte() }
        val goodName = uniqueName("keep", "jpg")
        val failName = uniqueName("failmid", "jpg")
        val goodId = seedImage(goodName, goodBytes)
        val failId = seedImage(failName, ByteArray(500_000) { 9 }) // real row; the source READ is what fails

        val engine = FileOperationEngine(
            FailingSourceOps(ops, failingId = failId, bytesBeforeFail = 48_000),
            Dispatchers.IO,
        )
        val events = engine.exportCopy(listOf(goodId, failId), treeUri.toString()).toList()

        val summary = summaryOf(events)
        assertEquals("good item saved, failed item counted", 1, summary.succeeded)
        assertEquals(1, summary.failed)
        assertArrayEquals("good item's bytes must be intact", goodBytes, File(treeDir, goodName).readBytes())
        assertFalse("partial document of the failed item must be deleted", File(treeDir, failName).exists())
    }

    /**
     * 4 — per-item isolation: one unreadable source in the batch is reported as failed while the other
     * items still land. The failed item's freshly created (empty) document is cleaned up by `abort()`.
     */
    @Test
    fun unreadableSourceIsIsolatedAndGoodItemsStillLand() = runBlocking {
        val aBytes = ByteArray(80_000) { (it % 131).toByte() }
        val bBytes = ByteArray(90_000) { (it % 137).toByte() }
        val aName = uniqueName("iso_a", "jpg")
        val bName = uniqueName("iso_b", "jpg")
        val badName = uniqueName("iso_bad", "jpg")
        val aId = seedImage(aName, aBytes)
        val bId = seedImage(bName, bBytes)
        val badId = seedImage(badName, ByteArray(16) { 5 })

        val engine = FileOperationEngine(
            FailingSourceOps(ops, failingId = badId, bytesBeforeFail = -1), // -1 → unreadable: openInput throws
            Dispatchers.IO,
        )
        val events = engine.exportCopy(listOf(aId, badId, bId), treeUri.toString()).toList()

        val summary = summaryOf(events)
        assertEquals("both readable items saved", 2, summary.succeeded)
        assertEquals(1, summary.failed)
        assertEquals(badId, summary.failures.single().id)
        assertArrayEquals(aBytes, File(treeDir, aName).readBytes())
        assertArrayEquals(bBytes, File(treeDir, bName).readBytes())
        assertFalse("failed item leaves no document", File(treeDir, badName).exists())
    }

    /**
     * 3 (cleanup path) — the `NonCancellable` guarantee: `abort()` runs AFTER the coroutine is already
     * cancelled, so its `doc.delete()` must still execute. A plain `withContext(io)` would fail its
     * up-front `ensureActive()` and orphan a partially written tree document. Deterministic — no
     * stream-timing race (mirrors the MediaStore pending-row regression in `MediaStoreFileOperationsTest`).
     */
    @Test
    fun abortAfterCancellationDeletesPartialTreeDocument() = runBlocking {
        val name = uniqueName("cancelabort", "jpg")
        val ready = arrayOfNulls<Sink>(1)

        val job = launch(Dispatchers.IO) {
            val sink = ops.createTreeSink(treeUri.toString(), name, "image/jpeg")
            ready[0] = sink
            sink.output.write(ByteArray(64)) // a real partial document now exists in the tree
            sink.output.flush()
            try {
                awaitCancellation() // suspend until cancelled, exactly like a mid-stream export
            } catch (t: Throwable) {
                sink.abort() // cleanup under cancellation — must still delete the partial document
                throw t
            }
        }
        while (ready[0] == null) delay(5)
        job.cancelAndJoin()

        assertFalse("cancelled export must leave no partial tree document", File(treeDir, name).exists())
    }

    // --- helpers ---

    private fun summaryOf(events: List<FileOperationEvent>): OperationResult =
        (events.last() as FileOperationEvent.Completed).summary

    private suspend fun writeAndCommit(sink: Sink, bytes: ByteArray) {
        sink.output.use { it.write(bytes) }
        sink.commit()
    }

    private fun seedImage(name: String, bytes: ByteArray): MediaId =
        seed(name, bytes, "image/jpeg", MediaStore.Images.Media.getContentUri(VOLUME), Environment.DIRECTORY_PICTURES)

    private fun seedVideo(name: String, bytes: ByteArray): MediaId =
        seed(name, bytes, "video/mp4", MediaStore.Video.Media.getContentUri(VOLUME), Environment.DIRECTORY_MOVIES)

    private fun seed(name: String, bytes: ByteArray, mime: String, collection: Uri, dir: String): MediaId {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/$SOURCE_FOLDER/")
        }
        val uri = requireNotNull(resolver.insert(collection, values)) { "seed insert failed for $name" }
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        createdUris += uri
        return MediaId(ContentUris.parseId(uri).toString())
    }

    private fun uniqueName(tag: String, ext: String): String = "jgallery_export_${tag}_${RUN_SALT}_${nextSeq()}.$ext"

    /**
     * A real [StorageOps] whose only change is that [failingId]'s source read fails: [bytesBeforeFail]
     * `< 0` makes `openInput` throw (unreadable source); `>= 0` returns a stream that yields that many
     * bytes then throws mid-stream. Everything else — `createTreeSink`, `namesInTree`, `mimeType`,
     * `displayName` — delegates to the genuine [MediaStoreStorageOps], so the destination sink is a
     * real `DocumentFileSink` over the provider.
     */
    private class FailingSourceOps(
        private val delegate: StorageOps,
        private val failingId: MediaId,
        private val bytesBeforeFail: Int,
    ) : StorageOps by delegate {
        override suspend fun openInput(id: MediaId): InputStream {
            if (id != failingId) return delegate.openInput(id)
            if (bytesBeforeFail < 0) throw IOException("source $id is unreadable")
            return FailingInputStream(bytesBeforeFail)
        }
    }

    /** Yields [limit] zero bytes then throws, forcing a deterministic mid-stream copy failure. */
    private class FailingInputStream(private val limit: Int) : InputStream() {
        private var served = 0
        override fun read(): Int {
            if (served >= limit) throw IOException("stream failed mid-copy")
            served++
            return 0
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served >= limit) throw IOException("stream failed mid-copy")
            val n = minOf(len, limit - served)
            for (i in 0 until n) b[off + i] = 0
            served += n
            return n
        }
    }

    /** In-memory [TrashMetadataStore] — export never trashes, so no persistence is needed. */
    private class InMemoryTrashStore : TrashMetadataStore {
        private val entries = MutableStateFlow<List<TrashEntry>>(emptyList())
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

    private companion object {
        const val VOLUME = "external"
        const val SOURCE_FOLDER = "JGalleryExportTestSrc"
        // Per-process salt: the orchestrator runs each method in a fresh process, so a bare counter
        // restarts at the same values and could collide with rows a prior attempt left on MediaStore.
        private val RUN_SALT: String = System.nanoTime().toString(36)
        private var seq = 0
        fun nextSeq(): Int = ++seq
    }
}
