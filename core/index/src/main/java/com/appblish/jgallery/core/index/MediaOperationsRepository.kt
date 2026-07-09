package com.appblish.jgallery.core.index

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

    /** Copy [ids] into [destinationBucketId]; originals remain (spec §7.1). */
    fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move [ids] into [destinationBucketId]; removed from source, collisions handled (spec §7.2). */
    fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move [ids] to the app-managed, restorable Trash (spec §7.5). */
    fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently remove [ids] from device storage (spec §7.5 — reached only via a 2-step confirm). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent>
}
