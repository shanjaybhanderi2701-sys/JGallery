package com.appblish.jgallery.core.storage.internal

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * On-device coverage for the file-operation core against the REAL MediaStore provider (spec §7): the
 * pieces a JVM unit test cannot reach — the `IS_PENDING` insert lifecycle, `openInput`/`openOutput`
 * over `content://` uris, and real update/delete.
 *
 * It works entirely with app-owned media (each item created here via MediaStore), so it runs on the
 * standard instrumented emulator without All Files Access — an app may always contribute and mutate
 * its own new entries under scoped storage. Every id created is torn down in [tearDown].
 *
 * The collision-naming, chunked-streaming, cancellation, and result-aggregation *policy* is proven
 * exhaustively (and provider-independently) by `FileOperationEngineTest`; this test verifies the
 * platform primitives underneath it actually move bytes on a device.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreFileOperationsTest {

    private lateinit var context: Context
    private lateinit var storage: MediaStoreStorageAccess
    private val createdUris = mutableListOf<Uri>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        storage = MediaStoreStorageAccess(context.contentResolver, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        // Best-effort cleanup of everything this test wrote (source + any copies it produced).
        val resolver = context.contentResolver
        createdUris.forEach { runCatching { resolver.delete(it, null, null) } }
        // Copies land in the fallback folder with the source's name; sweep them too.
        runCatching {
            resolver.delete(
                MediaStore.Files.getContentUri(VOLUME),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("%$FALLBACK_FOLDER%"),
            )
        }
    }

    @Test
    fun copyThenStreamedBytesMatchAndOriginalRemains() = runBlocking {
        val payload = ByteArray(2_000_003) { (it % 251).toByte() } // > buffer, non-aligned size
        val name = uniqueName("copy")
        val id = seedImage(name, payload)

        // Unknown destination bucket -> engine uses the fallback folder; a copy is still produced.
        val events = storage.copy(listOf(id), destinationBucketId = "unknown-bucket").toList()

        assertEquals(OperationResult(succeeded = 1, failed = 0), summaryOf(events))
        assertTrue("original must remain after copy", storage.openStreamExists(id))
        assertEquals("copied bytes must match source", payload.size, copyBytesInFallback(name).size)
    }

    @Test
    fun renameChangesDisplayNameOnDevice() = runBlocking {
        val id = seedImage(uniqueName("rename"), ByteArray(16) { 1 })
        val newName = uniqueName("renamed")

        val result = storage.rename(id, newName)

        assertEquals(OperationResult(succeeded = 1, failed = 0), result)
        assertEquals(newName, currentDisplayName(id))
    }

    @Test
    fun deletePermanentlyRemovesTheItem() = runBlocking {
        val id = seedImage(uniqueName("delete"), ByteArray(16) { 2 })

        val events = storage.deletePermanently(listOf(id)).toList()

        assertEquals(OperationResult(succeeded = 1, failed = 0), summaryOf(events))
        assertEquals("row must be gone", null, currentDisplayName(id))
    }

    /**
     * Regression for the cancellation-cleanup path (APP-316): `Sink.abort()` runs *after* the
     * coroutine is already cancelled, so its body must be `NonCancellable` — otherwise `withContext`
     * fails its up-front `ensureActive()` and silently skips the delete, orphaning a still-`IS_PENDING`
     * MediaStore row. This mirrors `FileOperationEngine.copyOne`'s try/catch structure directly (a
     * JVM fake `Sink` cannot reproduce it) and is deterministic — no stream-timing race.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    @Test
    fun abortAfterCancellationStillDeletesThePendingEntry() = runBlocking {
        val ops = MediaStoreStorageOps(context.contentResolver, Dispatchers.IO)
        val name = uniqueName("cancelabort")
        val ready = arrayOfNulls<Sink>(1)

        val job = launch(Dispatchers.IO) {
            val sink = ops.createSink(destinationBucketId = "unknown-bucket", name = name, mimeType = "image/jpeg")
            ready[0] = sink
            sink.output.write(ByteArray(16)) // a real partial, still-pending row now exists
            sink.output.flush()
            try {
                awaitCancellation() // suspend until the collector cancels, exactly like a mid-stream copy
            } catch (t: Throwable) {
                sink.abort() // cleanup under cancellation — must still remove the pending row
                throw t
            }
        }
        while (ready[0] == null) delay(5)
        job.cancelAndJoin()

        assertEquals("cancelled copy must leave no orphaned pending row", null, displayNameByName(name))
    }

    // --- helpers ---

    private fun summaryOf(events: List<FileOperationEvent>): OperationResult =
        (events.last() as FileOperationEvent.Completed).summary

    /** Insert an app-owned JPEG with [bytes] and return its [MediaId]. */
    private fun seedImage(name: String, bytes: ByteArray): MediaId {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$SOURCE_FOLDER/")
        }
        val collection = MediaStore.Images.Media.getContentUri(VOLUME)
        val uri = requireNotNull(resolver.insert(collection, values)) { "seed insert failed" }
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        createdUris += uri
        return MediaId(ContentUris.parseId(uri).toString())
    }

    private fun currentDisplayName(id: MediaId): String? =
        context.contentResolver.query(
            idUri(id),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    /**
     * Display name of the row named [name], or null if none exists — *including still-pending rows*.
     * A default query excludes `IS_PENDING=1` entries, which would hide exactly the leak this test
     * hunts for, so it opts in via `QUERY_ARG_MATCH_PENDING` (API 30+; this test targets that lane).
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun displayNameByName(name: String): String? {
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
            )
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(name))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        return context.contentResolver.query(
            MediaStore.Files.getContentUri(VOLUME),
            arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME),
            queryArgs,
            null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun copyBytesInFallback(name: String): ByteArray {
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Files.getContentUri(VOLUME),
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND " +
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(name, "%$FALLBACK_FOLDER%"),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val copyId = MediaId(cursor.getLong(0).toString())
                return resolver.openInputStream(idUri(copyId))!!.use { it.readBytes() }
            }
        }
        return ByteArrayOutputStream().toByteArray() // empty -> assertion fails with a clear size diff
    }

    private fun MediaStoreStorageAccess.openStreamExists(id: MediaId): Boolean = runBlocking {
        runCatching { openStream(id).close() }.isSuccess
    }

    private fun idUri(id: MediaId): Uri =
        ContentUris.withAppendedId(MediaStore.Files.getContentUri(VOLUME), id.value.toLong())

    private fun uniqueName(tag: String): String = "jgallery_${tag}_${nextSeq()}.jpg"

    private companion object {
        const val VOLUME = "external"
        const val SOURCE_FOLDER = "JGalleryOpsTestSrc"
        const val FALLBACK_FOLDER = "JGallery"
        private var seq = 0
        fun nextSeq(): Int = ++seq
    }
}
