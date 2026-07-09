package com.appblish.jgallery.core.index

import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.TrashEntry
import kotlinx.coroutines.flow.Flow

/**
 * The feature-facing Recycle-Bin API (spec §7.5, design W2-09). Like [MediaOperationsRepository] it
 * sits in `:core:index` so the Trash feature depends only on `:core:index` for reads and writes and
 * never links `:core:storage` — the §1.6 boundary stays a single, swappable seam.
 *
 * It adds no behaviour of its own: listing, retention metadata, restore-to-origin, permanent delete,
 * empty-bin and the 30-day auto-purge all live in the storage layer's app-managed Trash engine. Bulk
 * variants return the E8 [FileOperationEvent] stream (zero-or-more `InProgress` then one terminal
 * `Completed` with the "X done, Y failed" summary); all work is off the main thread.
 *
 * (Moving items *into* the bin is [MediaOperationsRepository.moveToTrash] — the delete flows own that
 * entry point; this repository owns everything that happens once an item is already in the bin.)
 */
interface TrashRepository {

    /** The live bin contents, newest-first, for the Trash screen. */
    fun observeTrash(): Flow<List<TrashEntry>>

    /** Restore [ids] to their original locations, recreating the folder if needed (§7.5). */
    fun restore(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently remove [ids] from device storage and from the bin (§7.5 — behind a 2-step confirm). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently delete every item in the bin (design W2-09 "Empty bin"). */
    fun emptyTrash(): Flow<FileOperationEvent>

    /** Purge items past the 30-day retention window; returns how many were removed (best-effort). */
    suspend fun purgeExpired(): Int
}
