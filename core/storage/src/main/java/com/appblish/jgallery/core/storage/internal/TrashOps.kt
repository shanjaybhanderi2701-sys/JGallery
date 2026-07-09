package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaType

/**
 * Low-level, per-item Recycle-Bin platform primitives — the platform half of Trash (spec §7.5).
 *
 * Kept separate from [StorageOps] (the copy/move/rename file-op SPI) on purpose: the *policy* of the
 * bin (recording retention metadata, purging expired items, progress + result aggregation) lives in
 * the pure, JVM-testable [TrashEngine]; this SPI is the only part that reaches MediaStore, so it is
 * the only part that needs an on-device (instrumented) test.
 *
 * It lives inside `:core:storage`, the one privileged module allowed to touch MediaStore /
 * ContentResolver (spec §1.6). Swapping the permission model (All Files → media permissions → SAF)
 * swaps this implementation; the engine and every feature above the boundary are untouched.
 *
 * Every method is `suspend` — implementations MUST do their IO off the caller's thread.
 */
internal interface TrashOps {

    /**
     * Snapshot of [id]'s current row, captured *before* it is trashed so the engine can persist the
     * origin path + display fields as retention metadata. Null if the item no longer exists.
     */
    suspend fun describe(id: MediaId): TrashItemSnapshot?

    /** Move [id] into the (device-managed, restorable) trash. Returns false if the item is already gone. */
    suspend fun trash(id: MediaId): Boolean

    /** Bring [id] back out of the trash to its original location. Returns false if it is already gone. */
    suspend fun restore(id: MediaId): Boolean

    /** Permanently remove [id] from device storage. Returns false if it is already gone. */
    suspend fun delete(id: MediaId): Boolean
}

/**
 * The subset of a media row the Recycle Bin persists so it can list a trashed item (which is hidden
 * from the normal index) and restore it to exactly where it came from.
 */
internal data class TrashItemSnapshot(
    val displayName: String,
    val type: MediaType,
    val mimeType: String,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
)
