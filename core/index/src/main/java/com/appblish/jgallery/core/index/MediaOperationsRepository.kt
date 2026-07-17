package com.appblish.jgallery.core.index

import android.net.Uri
import com.appblish.jgallery.core.model.CaptureKind
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * The feature-facing mutation API for media (spec §7). It sits alongside [MediaIndexRepository] so a
 * feature depends only on `:core:index` for *both* reads and writes and never links `:core:storage`
 * directly — the §1.6 boundary stays a single, swappable seam. Every method just delegates to the
 * storage layer's E8 file-operation core; the cached index refreshes itself reactively off the
 * MediaStore change signal, so callers never manually refresh after a mutation.
 *
 * Bulk variants return the E8 [FileOperationEvent] stream: zero-or-more `InProgress` then one
 * terminal `Completed` carrying the "X done, Y failed" summary. Cancelling collection stops the
 * batch and rolls back the in-flight item. All work is off the main thread and streams (no OOM).
 */
interface MediaOperationsRepository {

    /**
     * Create an empty album/folder to organize media (spec §6). The new folder becomes a copy/move
     * destination. Returns a one-item [OperationResult]: `succeeded = 1` on success (idempotent if it
     * already exists), or `failed = 1` with a human-readable reason (invalid name / IO failure).
     */
    suspend fun createAlbum(name: String): OperationResult

    /**
     * Rename a single item's underlying file (spec §7.3). Returns a one-item [OperationResult]:
     * `succeeded = 1` on success (or a no-op when the name is unchanged), or `failed = 1` with a
     * human-readable reason (blank/illegal name, item gone, IO failure). Off the main thread.
     */
    suspend fun rename(id: MediaId, newDisplayName: String): OperationResult

    /**
     * A readable `content://` uri for [id] to hand to the system "set as" intent (`ACTION_ATTACH_DATA`
     * — wallpaper / contact photo, spec §7.4). Null if the item no longer exists. The caller grants
     * the receiving app read permission on the intent (the only sanctioned uri exposure — APP-297).
     */
    suspend fun viewUri(id: MediaId): Uri?

    /** Copy [ids] into [destinationBucketId]; originals remain (spec §7.1). */
    fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move [ids] into [destinationBucketId]; removed from source, collisions handled (spec §7.2). */
    fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /**
     * "Save a copy" — export [ids] into the user-picked SAF folder [treeUri] (G2 · APP-549). Originals
     * remain; each item is streamed from its MediaStore `content://` into a new document in the granted
     * tree. [treeUri] is the transient, user-scoped `ACTION_OPEN_DOCUMENT_TREE` grant (Security gate
     * APP-542 §5) — no filesystem path, no `WRITE_EXTERNAL_STORAGE`, grant not persisted. Emits progress
     * + a terminal "X saved, Y failed" summary like [copy]; cancelling stops the batch and discards the
     * in-flight partial document.
     */
    fun exportCopy(ids: List<MediaId>, treeUri: Uri): Flow<FileOperationEvent>

    /**
     * Create album [name] and copy [ids] into it in one flow — the copy/move sheet's "New album" tile
     * with a Copy verb (C6 item 12). The new album is born holding its contents (first item = cover).
     * Only the album *name* crosses the boundary (APP-297); the row-less-new-album destination is
     * resolved inside §1.6. Emits progress + a terminal summary like [copy]; a create failure emits one
     * failed terminal event and copies nothing.
     */
    fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent>

    /** Like [copyToNewAlbum] but moves [ids] into the new album (Move verb — spec §7.2). */
    fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent>

    /**
     * Begin a "capture straight into album" for the album named [albumName] (APP-424, C1-09 item 9),
     * returning an [AlbumCapture] whose [AlbumCapture.outputUri] the caller hands to the system camera
     * (`EXTRA_OUTPUT`) — delegated capture, no `CAMERA` permission (Security gate APP-426). Name-scoped
     * like [createAlbum]; returns null when the name is invalid (nothing is minted). On `RESULT_OK` call
     * [AlbumCapture.commit] (the album materialises holding the captured cover); on cancel, [AlbumCapture.abort].
     */
    suspend fun beginCapture(albumName: String, kind: CaptureKind): AlbumCapture?

    /**
     * Sweep this app's orphaned pending capture rows from an interrupted capture — a crash between
     * [beginCapture] and its commit/abort (APP-424 / Security gate APP-426). Returns how many were swept.
     */
    suspend fun sweepOrphanedCaptures(): Int

    /** Move [ids] to the app-managed, restorable Trash (spec §7.5). */
    fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently remove [ids] from device storage (spec §7.5 — reached only via a 2-step confirm). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent>

    // --- Album-as-entity operations (spec §7, §11 DoD: albums are first-class Copy/Move/Rename/Delete
    // targets). [bucketId] is the opaque album handle; Copy/Move/Delete simply enumerate the album's
    // members and reuse the per-item primitives above, so the §1.6 surface grows by only `renameAlbum`.

    /**
     * Rename the album/folder [bucketId] to [newName] — updates the real folder name via the storage
     * layer (spec §7.3, §11). One-shot [OperationResult]: `failed = 0` on success, else a reason.
     */
    suspend fun renameAlbum(bucketId: String, newName: String): OperationResult

    /** Copy every member of album [bucketId] into [destinationBucketId]; originals remain (spec §7.1). */
    fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move every member of album [bucketId] into [destinationBucketId] (spec §7.2). */
    fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move every member of album [bucketId] to the restorable Trash — "delete album" (spec §7.5). */
    fun deleteAlbum(bucketId: String): Flow<FileOperationEvent>
}
