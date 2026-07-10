package com.appblish.jgallery.core.storage

import android.net.Uri
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * THE single storage-access abstraction (spec §1.6). Every file/media read, write, and enumeration
 * in the app flows through this interface. No feature, no other core module, opens a `File`, queries
 * `MediaStore`, or touches `ContentResolver` directly — the `RawStorageAccess` lint check fails the
 * build if they try.
 *
 * Why it exists: JGallery ships on All Files Access (`MANAGE_EXTERNAL_STORAGE`), but Play may force a
 * migration to media permissions or SAF. Because all access is funnelled here, that migration swaps
 * one implementation and touches zero feature code (spec §9.4).
 *
 * Contract: every method is `suspend` or returns a `Flow` — nothing here runs on the caller's thread.
 * Implementations MUST do decode/IO on `Dispatchers.IO`, stream large files, and never load whole
 * files into memory (spec §1 rules 3, 4, 7).
 */
interface StorageAccess {

    /** Which permission strategy is backing access right now (the swappable dimension). */
    val backend: StorageBackend

    /** True once the app holds sufficient access to enumerate media. */
    suspend fun hasMediaAccess(): Boolean

    /** Enumerate media matching [query]. Backed by MediaStore for speed even under All Files Access. */
    suspend fun queryMedia(query: MediaQuery): List<MediaItem>

    /** Enumerate device albums/buckets with covers + counts (Albums tab). */
    suspend fun queryAlbums(): List<com.appblish.jgallery.core.model.Album>

    /**
     * Open a decoded-size-agnostic stream for [id]. Callers (e.g. `:core:thumbs`) request a target
     * size so the boundary can hand back the smallest source it can — never the full-size bytes for a
     * grid tile (spec §1 rule 2). Caller closes the stream.
     */
    suspend fun openStream(id: MediaId, target: DecodeTarget = DecodeTarget.Full): InputStream

    // --- Incremental indexing support (spec §1 rule 4). Read-only; used by :core:index only. ---

    /**
     * Cheap fingerprint of every media row matching [query] (id + date-modified + size),
     * read with a minimal projection. `:core:index` diffs this against its persisted signatures to
     * find adds/updates/deletes without re-reading full-column data for unchanged rows — the "update
     * incrementally, don't re-scan on every open" mandate.
     */
    suspend fun queryMediaSignatures(query: MediaQuery = MediaQuery()): List<MediaSignature>

    /**
     * Emits once per media-store change while collected (backed by a `ContentObserver` — the one
     * place it may be registered, since only this module may touch `ContentResolver`). The index
     * collects this to run an incremental sync when photos/videos change while the app is open,
     * instead of polling or re-scanning. Conflated: bursts collapse to a single signal.
     */
    fun observeMediaChanges(): Flow<Unit>

    // --- Mutations (spec §7). All off-thread; bulk variants stream progress + a terminal summary. ---

    suspend fun rename(id: MediaId, newDisplayName: String): OperationResult

    suspend fun createAlbum(name: String): OperationResult

    /**
     * Rename an album/folder *as an entity* — the folder itself, not one file inside it (spec §7.3,
     * §11 DoD). [bucketId] is the opaque album handle (APP-297 purity: no path or MediaStore concept
     * crosses the boundary), [newName] the desired folder name (validated like [createAlbum]). Because
     * an album only materialises once it holds media, the rename always has ≥1 member row to relocate
     * into the renamed folder; the returned [OperationResult] aggregates that per-member relocation
     * (`succeeded`/`failed` counts, with a human-readable reason per failure). Off-thread.
     */
    suspend fun renameAlbum(bucketId: String, newName: String): OperationResult

    /**
     * A readable `content://` uri for [id], to hand to a system "set as" / attach intent
     * (`ACTION_ATTACH_DATA` — wallpaper / contact photo, spec §7.4). Returns null if the item no
     * longer exists. This is the one place a uri is *deliberately* exposed across the boundary: the
     * receiving app needs one to read the bytes, so the caller grants read permission on the intent.
     * No filesystem path crosses — only the opaque MediaStore uri (APP-297 boundary-purity ruling).
     */
    suspend fun viewUri(id: MediaId): Uri?

    /**
     * Copy [ids] into [destinationBucketId] (originals remain — spec §7.1). The returned flow emits
     * a [FileOperationEvent.InProgress] per item and one terminal [FileOperationEvent.Completed]
     * carrying the "X copied, Y failed" summary. Runs off-thread and streams (no whole-file buffer);
     * cancelling collection stops the batch and discards the in-flight partial copy.
     */
    fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /** Move [ids] into [destinationBucketId]; removed from source, collisions handled (spec §7.2). */
    fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent>

    /**
     * Move to the app-managed Trash (restorable — spec §7.5). Each item's origin path + a trashed-at
     * timestamp are recorded as retention metadata so [restoreFromTrash] returns it to where it was
     * and the 30-day auto-purge is enforced from the app's own clock. Permanent delete is a separate,
     * 2-step call ([deletePermanently]).
     */
    fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent>

    /** The current Recycle Bin contents (newest-first), for the Trash screen (spec §7.5). */
    fun observeTrash(): Flow<List<TrashEntry>>

    /** Restore [ids] from Trash to their original location, recreating the folder if needed (§7.5). */
    fun restoreFromTrash(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently remove [ids] from device storage AND from the bin's metadata (§7.5, 2-step confirm). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent>

    /** Permanently delete every item in the bin (design W2-09 "Empty bin"). */
    fun emptyTrash(): Flow<FileOperationEvent>

    /** Purge items past the 30-day retention window; returns how many were removed (best-effort). */
    suspend fun purgeExpiredTrash(): Int
}

/** The swappable permission strategy. All Files Access is the locked G1 choice. */
enum class StorageBackend { ALL_FILES_ACCESS, MEDIA_PERMISSIONS, STORAGE_ACCESS_FRAMEWORK }

/** Hint for how small a source the caller needs, so the boundary avoids full-size decode. */
sealed interface DecodeTarget {
    data object Full : DecodeTarget
    data class Thumbnail(val maxEdgePx: Int) : DecodeTarget
}
