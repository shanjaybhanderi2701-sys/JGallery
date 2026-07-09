package com.appblish.jgallery.core.storage.internal

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.appblish.jgallery.core.model.MediaId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * The MediaStore / All-Files-Access implementation of the [StorageOps] file-operation primitives.
 *
 * This is platform mechanics only — no batching, no progress, no collision policy (that all lives in
 * [FileOperationEngine]). It sits inside `:core:storage`, the one module allowed to touch
 * `MediaStore` / `ContentResolver` directly (spec §1.6). Because JGallery holds All Files Access, the
 * update/delete/trash calls here need no per-item `RecoverableSecurityException` consent dialog.
 *
 * Its correctness can only be proven on a device — the incremental-index/enumeration seam, insert
 * `IS_PENDING` lifecycle, and trash column all behave against a real provider. That verification is
 * the module's instrumented test; [FileOperationEngine] carries the JVM-unit coverage.
 */
internal class MediaStoreStorageOps(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
) : StorageOps {

    override suspend fun displayName(id: MediaId): String? =
        readString(id, MediaStore.Files.FileColumns.DISPLAY_NAME)

    override suspend fun mimeType(id: MediaId): String =
        readString(id, MediaStore.Files.FileColumns.MIME_TYPE) ?: DEFAULT_MIME

    override suspend fun namesInBucket(destinationBucketId: String): Set<String> = withContext(io) {
        val uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        resolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME),
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ?",
            arrayOf(destinationBucketId),
            null,
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            buildSet {
                while (cursor.moveToNext()) cursor.getString(nameCol)?.let { add(it) }
            }
        }.orEmpty()
    }

    override suspend fun openInput(id: MediaId) = withContext(io) {
        resolver.openInputStream(idToUri(id)) ?: error("Unable to open source stream for $id")
    }

    override suspend fun createSink(
        destinationBucketId: String,
        name: String,
        mimeType: String,
    ): Sink = withContext(io) {
        val collection = collectionFor(mimeType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathFor(destinationBucketId, mimeType))
            put(MediaStore.MediaColumns.IS_PENDING, 1) // invisible until commit()
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused an entry for '$name' in $destinationBucketId")
        MediaStoreSink(uri)
    }

    override suspend fun rename(id: MediaId, newName: String): Boolean = withContext(io) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        resolver.update(idToUri(id), values, null, null) > 0
    }

    override suspend fun trash(id: MediaId): Boolean = withContext(io) {
        // Restorable trash is the MediaStore IS_TRASHED flag (API 30+). E9 builds the app-managed
        // Trash UI/restore on top of this primitive; on Q there is no provider-level trash column.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            error("Trash requires Android 11+")
        }
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }
        resolver.update(idToUri(id), values, null, null) > 0
    }

    override suspend fun delete(id: MediaId): Boolean = withContext(io) {
        resolver.delete(idToUri(id), null, null) > 0
    }

    // --- internals ---

    private inner class MediaStoreSink(private val uri: Uri) : Sink {
        override val output: OutputStream =
            resolver.openOutputStream(uri) ?: error("Unable to open destination stream for $uri")

        override suspend fun commit() = withContext(io) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, values, null, null)
            Unit
        }

        override suspend fun abort() = withContext(NonCancellable + io) {
            // Discard the still-pending entry and its partial bytes. NonCancellable is required: abort
            // is the cancellation-cleanup path, so the coroutine's Job is already cancelled here — a
            // plain withContext(io) would fail its up-front ensureActive() and skip the delete,
            // orphaning the IS_PENDING row. Best-effort: a failed delete must not mask the original
            // failure that triggered the abort.
            runCatching { resolver.delete(uri, null, null) }
            Unit
        }
    }

    private suspend fun readString(id: MediaId, column: String): String? = withContext(io) {
        resolver.query(idToUri(id), arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun idToUri(id: MediaId): Uri =
        ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            id.value.toLong(),
        )

    private fun collectionFor(mimeType: String): Uri =
        if (mimeType.startsWith("video/")) {
            MediaStore.Video.Media.getContentUri(EXTERNAL_VOLUME)
        } else {
            MediaStore.Images.Media.getContentUri(EXTERNAL_VOLUME)
        }

    /**
     * The `RELATIVE_PATH` a new entry must land in. The destination is addressed by bucket id (an
     * opaque hash MediaStore derives from the folder path), so we recover the folder from any
     * existing row in that bucket. When the bucket has no rows yet (or is unknown), fall back to a
     * type-appropriate app folder so a copy/move still succeeds instead of failing outright.
     *
     * NOTE (integration point for E7's destination picker): the picker should hand the operation a
     * concrete folder so this reverse lookup is unnecessary; until then this keeps the primitive
     * usable against existing device albums.
     */
    private fun relativePathFor(bucketId: String, mimeType: String): String {
        val existing = resolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH),
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ?",
            arrayOf(bucketId),
            "${MediaStore.Files.FileColumns._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (!existing.isNullOrBlank()) return existing
        val root = if (mimeType.startsWith("video/")) {
            Environment.DIRECTORY_MOVIES
        } else {
            Environment.DIRECTORY_PICTURES
        }
        return "$root/$FALLBACK_FOLDER/"
    }

    private companion object {
        const val EXTERNAL_VOLUME = "external"
        const val DEFAULT_MIME = "application/octet-stream"
        const val FALLBACK_FOLDER = "JGallery"
    }
}
