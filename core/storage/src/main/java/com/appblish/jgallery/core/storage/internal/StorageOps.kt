package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.MediaId
import java.io.InputStream
import java.io.OutputStream

/**
 * Low-level, per-item storage primitives — the platform half of the file-operation core (spec §7).
 *
 * This SPI exists to keep the *policy* of a bulk operation (collision naming, the chunked streaming
 * loop, cancellation checks, progress + result aggregation — see [FileOperationEngine]) separate
 * from the *mechanics* of talking to MediaStore / ContentResolver / the filesystem. The engine owns
 * the former and is a pure, JVM-unit-testable class; this SPI is the only thing that reaches the
 * platform, so it is the only part that needs an on-device (instrumented) test.
 *
 * It lives inside `:core:storage` (the one privileged, boundary-owning module), so implementing it
 * with real `MediaStore` calls does not breach the §1.6 rule that no other module touches raw file
 * APIs. Swapping the permission model (All Files → media permissions → SAF) means swapping this
 * implementation; the engine and every feature above the boundary are untouched (spec §1.6, §9.4).
 *
 * Every method is `suspend` — implementations MUST do their IO off the caller's thread. The engine
 * already dispatches onto IO, but implementations must not assume any particular caller thread.
 */
internal interface StorageOps {

    /** Current display name of [id], or null if the item no longer exists on the device. */
    suspend fun displayName(id: MediaId): String?

    /** MIME type of [id], used when creating the destination entry for a copy. */
    suspend fun mimeType(id: MediaId): String

    /**
     * Display names already occupying [destinationBucketId]. The engine diffs candidate names
     * against this set to resolve collisions ("IMG.jpg" → "IMG (1).jpg") without a per-name query.
     */
    suspend fun namesInBucket(destinationBucketId: String): Set<String>

    /** Open the source bytes of [id] for streaming. The engine closes the returned stream. */
    suspend fun openInput(id: MediaId): InputStream

    /**
     * Create a new, still-*pending* destination entry named [name] in [destinationBucketId] and
     * return a [Sink] over it. The entry is invisible to the gallery until [Sink.commit]; on
     * failure or cancellation the engine calls [Sink.abort] to discard the partial bytes so a
     * half-written file is never published (spec §1 rule 3 — bulk ops must not corrupt on failure).
     */
    suspend fun createSink(destinationBucketId: String, name: String, mimeType: String): Sink

    /**
     * Display names already present directly under the SAF tree [treeUri] (opaque uri string). The
     * engine diffs candidate names against this set to resolve collisions on export the same way it
     * does for a bucket copy, so re-exporting into a folder that already holds "IMG.jpg" writes
     * "IMG (1).jpg" instead of relying on the provider's own (inconsistent) auto-rename.
     */
    suspend fun namesInTree(treeUri: String): Set<String>

    /**
     * Create a new document named [name] (MIME [mimeType]) directly under the SAF tree [treeUri] and
     * return a [Sink] over its `OutputStream` (G2 · APP-549). [treeUri] is the transient, user-scoped
     * grant from `ACTION_OPEN_DOCUMENT_TREE`; this is the one write target that reaches outside the
     * app without a filesystem path or `WRITE_EXTERNAL_STORAGE`. On failure or cancellation the engine
     * calls [Sink.abort] to delete the partially written document so a half-copy is never left behind.
     */
    suspend fun createTreeSink(treeUri: String, name: String, mimeType: String): Sink

    /** Rename [id] in place to [newName]. Returns false if the item no longer exists. */
    suspend fun rename(id: MediaId, newName: String): Boolean

    /**
     * The `RELATIVE_PATH` of the folder backing [bucketId] (read from any member row), or null when
     * the album has no rows. Used to compute the destination folder for an album rename.
     */
    suspend fun albumRelativePath(bucketId: String): String?

    /** Ids of every media row currently in [bucketId] — the album's members to relocate on rename. */
    suspend fun idsInBucket(bucketId: String): List<MediaId>

    /**
     * Move [id] into the folder [relativePath] (a `RELATIVE_PATH` like `"Pictures/Holiday/"`) — the
     * per-row half of an album rename. Returns false if the row no longer exists.
     */
    suspend fun moveToFolder(id: MediaId, relativePath: String): Boolean

    /** Move [id] to the app-managed Trash (restorable). Returns false if already gone. */
    suspend fun trash(id: MediaId): Boolean

    /** Permanently remove [id] from device storage. Returns false if already gone. */
    suspend fun delete(id: MediaId): Boolean
}

/**
 * A handle to a pending destination file. The engine streams source bytes into [output], then
 * either [commit]s (publish) or [abort]s (discard partial). Exactly one of the two is always called.
 */
internal interface Sink {
    val output: OutputStream

    /** Publish the fully written file to the gallery. */
    suspend fun commit()

    /** Discard the partially written entry (failure / cancellation). Must be idempotent-safe. */
    suspend fun abort()
}
