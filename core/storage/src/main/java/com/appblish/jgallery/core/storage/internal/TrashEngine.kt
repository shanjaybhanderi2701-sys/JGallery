package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.model.TrashEntry
import com.appblish.jgallery.core.model.TrashPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * The *policy* half of the app-managed Recycle Bin (spec §7.5): given the low-level [TrashOps]
 * primitives and a persistent [TrashMetadataStore], it drives move-to-trash / restore / permanent
 * delete / empty-bin and the 30-day auto-purge, producing the "X done, Y failed" reporting the spec
 * mandates for every bulk operation.
 *
 * Everything here is platform-free and JVM-unit-testable: the only things it touches are the injected
 * [ops] SPI and [store]. The parts most likely to have subtle bugs — retention-metadata bookkeeping,
 * failure isolation, cooperative cancellation, expiry math — live where a fake can exercise every
 * branch without a device.
 *
 * Guarantees mirror [FileOperationEngine]:
 * - **Off-thread:** every entry point runs on [io].
 * - **Isolation of failures:** one item failing is recorded in the summary and does not abort the
 *   batch; only cancellation stops it (and then no terminal [FileOperationEvent.Completed] is emitted).
 * - **Metadata safety:** an item is only forgotten from the bin once its device-side op has actually
 *   succeeded, so a failed restore/delete leaves the item in the bin to retry.
 */
internal class TrashEngine(
    private val ops: TrashOps,
    private val store: TrashMetadataStore,
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
) {

    /** The live bin contents (newest-first), for the Trash screen. */
    fun observeTrash(): Flow<List<TrashEntry>> = store.observe()

    /**
     * Move each id to Trash and record its retention metadata (origin path + trashed-at) so it can be
     * restored later (spec §7.5). The row is snapshotted *before* it is trashed; the metadata is only
     * persisted after the device-side trash succeeds, so the bin never lists an item still visible in
     * the gallery.
     */
    fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = flow {
        runBulk(ids, nameOf = { id -> ops.describe(id)?.displayName }) { id ->
            val snapshot = ops.describe(id) ?: error("item no longer exists")
            if (!ops.trash(id)) error("item no longer exists")
            store.put(
                TrashEntry(
                    id = id,
                    displayName = snapshot.displayName,
                    type = snapshot.type,
                    mimeType = snapshot.mimeType,
                    originalBucketId = snapshot.bucketId,
                    originalBucketName = snapshot.bucketName,
                    originalRelativePath = snapshot.relativePath,
                    trashedAtMillis = now(),
                    sizeBytes = snapshot.sizeBytes,
                    width = snapshot.width,
                    height = snapshot.height,
                    durationMillis = snapshot.durationMillis,
                ),
            )
        }
    }.flowOn(io)

    /**
     * Restore each id from Trash to its original location (spec §7.5). The metadata record is dropped
     * only after the device-side restore succeeds, so a failed restore keeps the item in the bin.
     */
    fun restore(ids: List<MediaId>): Flow<FileOperationEvent> = flow {
        runBulk(ids, nameOf = ::trashedName) { id ->
            if (!ops.restore(id)) error("item no longer exists")
            store.remove(listOf(id))
        }
    }.flowOn(io)

    /** Permanently remove each id from device storage and from the bin (spec §7.5, W2-08 step 2). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = flow {
        runBulk(ids, nameOf = ::trashedName) { id -> deleteOne(id) }
    }.flowOn(io)

    /** Empty the whole bin: permanently delete every recorded item (design W2-09 "Empty bin"). */
    fun emptyTrash(): Flow<FileOperationEvent> = flow {
        val ids = store.current().map { it.id }
        runBulk(ids, nameOf = ::trashedName) { id -> deleteOne(id) }
    }.flowOn(io)

    /**
     * Best-effort purge of items past their retention window (spec §7.5 "auto-purge after 30 days").
     * Called when the bin is opened; a failure to delete one expired item does not stop the rest and
     * is not surfaced to the user. Returns how many entries were purged.
     */
    suspend fun purgeExpired(): Int = withContext(io) {
        val nowMillis = now()
        val expired = store.current().filter { TrashPolicy.isExpired(it.trashedAtMillis, nowMillis) }
        var purged = 0
        for (entry in expired) {
            // Whether the device delete reports success or the row was already gone (`false`), the
            // retention window has elapsed so the item must leave the bin. Only a *thrown* IO error
            // keeps the entry, so a later purge or manual permanent-delete can retry it.
            if (runCatching { ops.delete(entry.id) }.isSuccess) {
                store.remove(listOf(entry.id))
                purged++
            }
        }
        purged
    }

    // --- internals ---

    private suspend fun deleteOne(id: MediaId) {
        // A thrown IO error is a real failure (item still on device — keep it in the bin to retry).
        // `false` means the row was already gone, which is fine: the goal is that it leaves the bin.
        ops.delete(id)
        store.remove(listOf(id))
    }

    /** Display name from the persisted metadata (used for progress on restore/delete/empty). */
    private suspend fun trashedName(id: MediaId): String? =
        store.current().firstOrNull { it.id == id }?.displayName

    /**
     * Runs [action] for each id, isolating per-item failures into the summary, emitting an
     * [FileOperationEvent.InProgress] after every item and a terminal [FileOperationEvent.Completed]
     * at the end. Cancellation propagates (no terminal event) so a cancelled batch is distinguishable
     * from a finished one — identical semantics to [FileOperationEngine].
     */
    private suspend fun FlowCollector<FileOperationEvent>.runBulk(
        ids: List<MediaId>,
        nameOf: suspend (MediaId) -> String?,
        action: suspend (MediaId) -> Unit,
    ) {
        var succeeded = 0
        val failures = ArrayList<OperationResult.Failure>()
        val total = ids.size
        ids.forEachIndexed { index, id ->
            val name = runCatching { nameOf(id) }.getOrNull()
            try {
                action(id)
                succeeded++
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                failures += OperationResult.Failure(id, t.message ?: t.javaClass.simpleName)
            }
            emit(
                FileOperationEvent.InProgress(
                    OperationProgress(completed = index + 1, total = total, currentName = name),
                ),
            )
        }
        emit(FileOperationEvent.Completed(OperationResult(succeeded, failures.size, failures)))
    }
}
