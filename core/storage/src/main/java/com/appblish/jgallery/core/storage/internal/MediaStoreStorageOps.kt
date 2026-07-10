package com.appblish.jgallery.core.storage.internal

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType
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
) : StorageOps, TrashOps {

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

    // Ownership of the returned stream transfers to the caller, who closes it (see FileOperationEngine's
    // `.use {}`). Closing it here would defeat the API, so Recycle is a false positive.
    @Suppress("Recycle")
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

    override suspend fun albumRelativePath(bucketId: String): String? = withContext(io) {
        resolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH),
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ?",
            arrayOf(bucketId),
            "${MediaStore.Files.FileColumns._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }
    }

    override suspend fun idsInBucket(bucketId: String): List<MediaId> = withContext(io) {
        resolver.query(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.BUCKET_ID} = ?",
            arrayOf(bucketId),
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            buildList { while (cursor.moveToNext()) add(MediaId(cursor.getLong(idCol).toString())) }
        }.orEmpty()
    }

    override suspend fun moveToFolder(id: MediaId, relativePath: String): Boolean = withContext(io) {
        // Rewriting RELATIVE_PATH relocates the underlying file into that folder — the MediaStore-native
        // way to "move" a row. Under All Files Access this needs no per-item consent dialog.
        //
        // A *bare* RELATIVE_PATH update, however, does not physically relocate the file on a
        // scoped-storage device: the provider reports 0 rows affected and the row stays put (APP-353 —
        // album rename moved 0/N members on-device while unit-green). MediaStore only honours the move
        // when the row is first marked pending, so we toggle IS_PENDING around the rewrite — the same
        // pending lifecycle the insert/copy path uses to commit bytes to disk. Two updates (not one
        // combined write) so the pending flag is committed before the relocation is requested.
        val uri = idToUri(id)
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) }, null, null)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }
        val moved = resolver.update(uri, values, null, null) > 0
        if (!moved) {
            // Relocation was rejected — clear the pending flag we set so the row does not stay
            // invisibly pending (which would hide the member from the gallery, worse than a plain fail).
            runCatching { resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) }
        }
        moved
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

    override suspend fun restore(id: MediaId): Boolean = withContext(io) {
        // Un-trash: clearing IS_TRASHED returns the row to its original RELATIVE_PATH automatically,
        // so restore is inherently "to the original location" (spec §7.5). App-managed retention
        // metadata (origin path + trashed-at) is tracked above this primitive by the TrashEngine.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            error("Trash requires Android 11+")
        }
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
        resolver.update(idToUri(id), values, null, null) > 0
    }

    override suspend fun describe(id: MediaId): TrashItemSnapshot? = withContext(io) {
        // Read the row's origin + display fields BEFORE it is trashed, so the bin can list and restore
        // it without re-querying a row that trashing makes invisible to the index.
        resolver.query(idToUri(id), DESCRIBE_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val isVideo = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)) ==
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            fun col(name: String) = cursor.getColumnIndexOrThrow(name)
            TrashItemSnapshot(
                displayName = cursor.getString(col(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "",
                type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                mimeType = cursor.getString(col(MediaStore.Files.FileColumns.MIME_TYPE)) ?: DEFAULT_MIME,
                bucketId = cursor.getString(col(MediaStore.Files.FileColumns.BUCKET_ID)) ?: "",
                bucketName = cursor.getString(col(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)) ?: "",
                relativePath = cursor.getString(col(MediaStore.Files.FileColumns.RELATIVE_PATH)) ?: "",
                sizeBytes = cursor.getLong(col(MediaStore.Files.FileColumns.SIZE)),
                width = cursor.getInt(col(MediaStore.Files.FileColumns.WIDTH)),
                height = cursor.getInt(col(MediaStore.Files.FileColumns.HEIGHT)),
                durationMillis = cursor.getLong(col(MediaStore.Files.FileColumns.DURATION)),
            )
        }
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

        val DESCRIBE_PROJECTION = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
        )
    }
}
