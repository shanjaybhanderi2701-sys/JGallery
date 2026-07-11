package com.appblish.jgallery.core.storage.internal

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.storage.DecodeTarget
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.storage.MediaSignature
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StorageBackend
import com.appblish.jgallery.core.storage.ThumbnailBitmapSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * The All-Files-Access + MediaStore implementation of [StorageAccess]. This is the ONE place in the
 * app that references `MediaStore`, `ContentResolver`, `Environment` and (via the manifest)
 * `MANAGE_EXTERNAL_STORAGE`. Everything is off the caller thread on [io].
 *
 * Scope note: read/enumerate paths are the Wave-1 seam. The file-operation mutations (copy / move /
 * rename / trash / delete — spec §7) are delegated to [FileOperationEngine] over the platform
 * [MediaStoreStorageOps] primitives (W2-E8). `createAlbum` (spec §6) makes the destination folder
 * directly under All Files Access, since MediaStore has no empty-bucket concept (W2-E10).
 */
internal class MediaStoreStorageAccess(
    private val resolver: ContentResolver,
    private val io: CoroutineDispatcher,
    trashStore: TrashMetadataStore,
    now: () -> Long = System::currentTimeMillis,
) : StorageAccess, ThumbnailBitmapSource {

    // The single platform SPI (copy/move/rename/trash/restore/delete primitives) — the only part that
    // talks to MediaStore. Both engines share it so there is exactly one MediaStore surface.
    private val storageOps = MediaStoreStorageOps(resolver, io)

    // Bulk/rename policy (streaming, collisions, progress, cancellation, result summary) lives here;
    // the platform SPI below it is the only part that talks to MediaStore.
    private val fileOps = FileOperationEngine(storageOps, io)

    // Recycle Bin policy (retention metadata, restore, purge, empty) — spec §7.5, built on the same
    // platform SPI plus the persistent metadata store.
    private val trashEngine = TrashEngine(storageOps, trashStore, io, now)

    override val backend: StorageBackend = StorageBackend.ALL_FILES_ACCESS

    override suspend fun hasMediaAccess(): Boolean = withContext(io) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-R: covered by READ_EXTERNAL_STORAGE granted at install/runtime
        }
    }

    override suspend fun queryMedia(query: MediaQuery): List<MediaItem> = withContext(io) {
        // An explicit empty id-set means "these specific rows" where there are none — match nothing
        // without hitting the provider (and without emitting invalid `IN ()` SQL).
        if (query.ids?.isEmpty() == true) return@withContext emptyList()
        val uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        val selection = buildSelection(query)
        resolver.query(uri, PROJECTION, selection.first, selection.second, orderBy(query))
            ?.use { cursor -> cursor.readMediaItems(query.limit) }
            .orEmpty()
    }

    override suspend fun queryAlbums(): List<Album> = withContext(io) {
        // Fold the media stream into buckets. Wave 2 replaces this with a cached-index-backed query.
        queryMedia(MediaQuery())
            .groupBy { it.bucketId }
            .map { (bucketId, items) ->
                val newest = items.maxByOrNull { it.dateTakenMillis }
                Album(
                    bucketId = bucketId,
                    name = items.first().bucketName,
                    itemCount = items.size,
                    cover = newest?.id,
                    newestItemMillis = newest?.dateTakenMillis ?: 0L,
                )
            }
            .sortedByDescending { it.newestItemMillis }
    }

    // Ownership of the stream transfers to the caller (see StorageAccess.openStream — "Caller closes
    // the stream"); closing it here would defeat the purpose, so the Recycle check is a false positive.
    @SuppressLint("Recycle")
    override suspend fun openStream(id: MediaId, target: DecodeTarget): InputStream =
        withContext(io) {
            val uri = idToUri(id)
            when (target) {
                is DecodeTarget.Full -> fullStream(uri, id)
                // Grid tiles: hand back the smallest source MediaStore can produce (pre-generated /
                // EXIF thumbnails where available — no full-size decode). If the provider cannot
                // thumbnail this item, fall back to the original bytes; Coil's subsampled decode
                // still bounds the resulting bitmap to the tile size (spec §1 rule 2).
                is DecodeTarget.Thumbnail ->
                    thumbnailStream(uri, target.maxEdgePx) ?: fullStream(uri, id)
            }
        }

    private fun fullStream(uri: Uri, id: MediaId): InputStream =
        resolver.openInputStream(uri) ?: error("Unable to open stream for $id")

    /**
     * Downsized JPEG bytes via [ContentResolver.loadThumbnail] (API 29+, min for this app), which
     * serves MediaStore's own thumbnail for both images and video frames. The tile-sized re-encode
     * is a one-time cost per (item, size) — `:core:thumbs` persists the result in its disk cache.
     * Null when the provider cannot produce a thumbnail (corrupt/unsupported source).
     */
    private fun thumbnailStream(uri: Uri, maxEdgePx: Int): InputStream? =
        try {
            val bitmap = resolver.loadThumbnail(uri, Size(maxEdgePx, maxEdgePx), null)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, out)
                out.toByteArray()
            }
            bitmap.recycle()
            ByteArrayInputStream(bytes)
        } catch (_: Exception) {
            null // IOException for un-thumbnailable rows; caller streams the original instead
        }

    /**
     * The APP-391 R1 decode-once seam: the SAME [ContentResolver.loadThumbnail] call as
     * [thumbnailStream] but WITHOUT the `bitmap.compress(...)` re-encode — that JPEG round-trip is the
     * pixel op we are deleting from the fling-critical path. The fetcher hands this decoded [Bitmap]
     * straight to Coil (one decode total) and re-encodes for the disk cache off the hot path. Null =
     * un-thumbnailable, so the fetcher falls back to the full-size stream.
     */
    override suspend fun loadThumbnailBitmap(id: MediaId, maxEdgePx: Int): Bitmap? =
        withContext(io) {
            try {
                resolver.loadThumbnail(idToUri(id), Size(maxEdgePx, maxEdgePx), null)
            } catch (_: Exception) {
                null // corrupt/unsupported/un-downsizable → caller streams the original instead
            }
        }

    override suspend fun queryMediaSignatures(query: MediaQuery): List<MediaSignature> =
        withContext(io) {
            if (query.ids?.isEmpty() == true) return@withContext emptyList()
            val uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
            val selection = buildSelection(query)
            resolver.query(uri, signatureProjection(), selection.first, selection.second, orderBy(query))
                ?.use { cursor -> cursor.readSignatures(query.limit) }
                .orEmpty()
        }

    override fun observeMediaChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val uri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)
        // notifyForDescendants = true: any image/video row change under the Files collection signals.
        resolver.registerContentObserver(uri, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().flowOn(io)

    override suspend fun rename(id: MediaId, newDisplayName: String): OperationResult =
        fileOps.rename(id, newDisplayName)

    // The cursor is closed by `use`; no stream/ownership transfer here, so Recycle is a false positive.
    @SuppressLint("Recycle")
    override suspend fun viewUri(id: MediaId): Uri? = withContext(io) {
        val rowId = id.value.toLongOrNull() ?: return@withContext null
        val uri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(EXTERNAL_VOLUME), rowId)
        // Confirm the row still exists so "Set as" on an item deleted underneath us degrades to a
        // graceful "no longer available" rather than firing an intent at a dangling uri.
        val exists = resolver
            .query(uri, arrayOf(MediaStore.Files.FileColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
        if (exists) uri else null
    }

    /**
     * Create an empty album folder under the public Pictures root (spec §6). Under All Files Access
     * the directory can be created directly on the volume — MediaStore itself has no "empty bucket"
     * concept (a bucket materialises only once it holds media), so this folder becomes a real,
     * enumerable album the moment a copy/move drops the first item into it (spec §7.1/§7.2). An
     * existing folder of the same name is treated as success (idempotent create). All IO is on [io].
     */
    override suspend fun createAlbum(name: String): OperationResult =
        when (val outcome = ensureAlbumFolder(name)) {
            is AlbumFolder.Failed -> failedAlbum(name, outcome.reason)
            is AlbumFolder.Ready -> OperationResult(succeeded = 1, failed = 0)
        }

    /**
     * Create album [name] and copy [ids] into it in one flow (spec §7.1, C6 item 12 "New album" tile).
     * The new album is row-less so it can't be a bucket-id destination; the flow creates the folder and
     * hands the engine its concrete RELATIVE_PATH instead (resolved inside [MediaStoreStorageOps]).
     */
    override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> =
        intoNewAlbum(name) { relativePath -> fileOps.copy(ids, relativePath) }

    /** As [copyToNewAlbum] but moves [ids] (removed from source) into the new album (spec §7.2). */
    override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> =
        intoNewAlbum(name) { relativePath -> fileOps.move(ids, relativePath) }

    /**
     * Shared create-then-fill scaffold for [copyToNewAlbum] / [moveToNewAlbum]: create (or reuse) the
     * album folder, then run [fill] with its concrete RELATIVE_PATH as the engine destination. If the
     * folder can't be created the flow emits a single failed terminal event and fills nothing, so a
     * collector always sees exactly one [FileOperationEvent.Completed].
     */
    private fun intoNewAlbum(
        name: String,
        fill: (relativePath: String) -> Flow<FileOperationEvent>,
    ): Flow<FileOperationEvent> = flow {
        when (val outcome = ensureAlbumFolder(name)) {
            is AlbumFolder.Failed ->
                emit(FileOperationEvent.Completed(failedAlbum(name, outcome.reason)))
            is AlbumFolder.Ready -> emitAll(fill(outcome.relativePath))
        }
    }.flowOn(io)

    /**
     * Create the album folder [name] under the public Pictures root (idempotent — an existing folder of
     * that name is reused) and return its RELATIVE_PATH, or a failure reason (invalid name, name taken
     * by a file, IO failure). The one place album-folder IO happens; [createAlbum] and the
     * create-and-move flows share it so the created folder and the fill destination can never drift.
     */
    private suspend fun ensureAlbumFolder(name: String): AlbumFolder = withContext(io) {
        when (val validation = AlbumNames.validate(name)) {
            is AlbumNames.Result.Invalid -> AlbumFolder.Failed(validation.reason)
            is AlbumNames.Result.Valid -> {
                val picturesRoot = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES,
                )
                val dir = File(picturesRoot, validation.name)
                val created = when {
                    dir.isDirectory -> true // already exists → idempotent success
                    dir.exists() -> false // a file (not a dir) already owns the name
                    else -> dir.mkdirs()
                }
                if (created) {
                    AlbumFolder.Ready(AlbumPaths.newAlbumPath(validation.name))
                } else {
                    AlbumFolder.Failed("Could not create the album folder")
                }
            }
        }
    }

    /** Outcome of [ensureAlbumFolder]: the created folder's RELATIVE_PATH, or why it couldn't be made. */
    private sealed interface AlbumFolder {
        data class Ready(val relativePath: String) : AlbumFolder
        data class Failed(val reason: String) : AlbumFolder
    }

    /**
     * Rename an album/folder as an entity (spec §7.3, §11) by relocating every member row into the
     * renamed folder — the MediaStore-native folder rename. Policy (validation, no-op, per-member
     * aggregation) lives in the pure [FileOperationEngine]; the RELATIVE_PATH rewrite is the platform
     * primitive. An album exists in MediaStore only while it holds media, so there is always ≥1 row
     * to move (an empty just-created folder never appears as an album to rename).
     */
    override suspend fun renameAlbum(bucketId: String, newName: String): OperationResult =
        fileOps.renameAlbum(bucketId, newName)

    private fun failedAlbum(name: String, reason: String): OperationResult =
        OperationResult(
            succeeded = 0,
            failed = 1,
            failures = listOf(OperationResult.Failure(MediaId(name), reason)),
        )

    override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> =
        fileOps.copy(ids, destinationBucketId)

    override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> =
        fileOps.move(ids, destinationBucketId)

    override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> =
        trashEngine.moveToTrash(ids)

    override fun observeTrash(): Flow<List<com.appblish.jgallery.core.model.TrashEntry>> =
        trashEngine.observeTrash()

    override fun restoreFromTrash(ids: List<MediaId>): Flow<FileOperationEvent> =
        trashEngine.restore(ids)

    override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> =
        trashEngine.deletePermanently(ids)

    override fun emptyTrash(): Flow<FileOperationEvent> =
        trashEngine.emptyTrash()

    override suspend fun purgeExpiredTrash(): Int =
        trashEngine.purgeExpired()

    // --- internals ---

    private fun idToUri(id: MediaId): Uri =
        ContentUris.withAppendedId(
            MediaStore.Files.getContentUri(EXTERNAL_VOLUME),
            id.value.toLong(),
        )

    private fun buildSelection(query: MediaQuery): Pair<String?, Array<String>?> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        val typeClauses = query.types.map {
            when (it) {
                MediaType.IMAGE -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                MediaType.VIDEO -> MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            }
        }
        if (typeClauses.isNotEmpty()) {
            clauses += typeClauses.joinToString(" OR ", "(", ")") {
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = $it"
            }
        }
        query.bucketId?.let {
            clauses += "${MediaStore.Files.FileColumns.BUCKET_ID} = ?"
            args += it
        }
        query.ids?.takeIf { it.isNotEmpty() }?.let { ids ->
            val placeholders = ids.joinToString(",") { "?" }
            clauses += "${MediaStore.Files.FileColumns._ID} IN ($placeholders)"
            args += ids.map { it.value }
        }
        return (clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")) to
            (args.takeIf { it.isNotEmpty() }?.toTypedArray())
    }

    private fun orderBy(query: MediaQuery): String {
        val column = when (query.sort.key) {
            SortKey.FILE_NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
            SortKey.FILE_PATH -> MediaStore.Files.FileColumns.RELATIVE_PATH
            SortKey.FILE_SIZE -> MediaStore.Files.FileColumns.SIZE
            SortKey.LAST_MODIFIED -> MediaStore.Files.FileColumns.DATE_MODIFIED
        }
        val dir = if (query.sort.direction == SortDirection.ASCENDING) "ASC" else "DESC"
        return "$column $dir"
    }

    private fun Cursor.readMediaItems(limit: Int?): List<MediaItem> {
        val idCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val typeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val bucketIdCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketNameCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val dateTakenCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
        val dateModCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val sizeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val widthCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.HEIGHT)
        val durationCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
        val mimeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

        val out = ArrayList<MediaItem>(if (limit != null) minOf(limit, count) else count)
        while (moveToNext()) {
            if (limit != null && out.size >= limit) break
            val isVideo = getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            out += MediaItem(
                id = MediaId(getLong(idCol).toString()),
                displayName = getString(nameCol) ?: "",
                type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                bucketId = getString(bucketIdCol) ?: "",
                bucketName = getString(bucketNameCol) ?: "",
                dateTakenMillis = getLong(dateTakenCol),
                dateModifiedMillis = getLong(dateModCol) * 1000L, // MediaStore stores seconds
                sizeBytes = getLong(sizeCol),
                width = getInt(widthCol),
                height = getInt(heightCol),
                durationMillis = getLong(durationCol),
                mimeType = getString(mimeCol) ?: "application/octet-stream",
            )
        }
        return out
    }

    private fun signatureProjection(): Array<String> = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.SIZE,
    )

    private fun Cursor.readSignatures(limit: Int?): List<MediaSignature> {
        val idCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val dateModCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val sizeCol = getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

        val out = ArrayList<MediaSignature>(if (limit != null) minOf(limit, count) else count)
        while (moveToNext()) {
            if (limit != null && out.size >= limit) break
            out += MediaSignature(
                id = MediaId(getLong(idCol).toString()),
                dateModifiedMillis = getLong(dateModCol) * 1000L, // MediaStore stores seconds
                sizeBytes = getLong(sizeCol),
            )
        }
        return out
    }

    private companion object {
        // Literal value of MediaStore.VOLUME_EXTERNAL (API 29 constant) — using the string keeps
        // the call available without a NewApi guard below minSdk 29's floor.
        const val EXTERNAL_VOLUME = "external"

        // Tile-sized re-encode quality: visually lossless at grid scale, ~½ the bytes of q95.
        const val THUMBNAIL_JPEG_QUALITY = 85

        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MIME_TYPE,
        )
    }
}
