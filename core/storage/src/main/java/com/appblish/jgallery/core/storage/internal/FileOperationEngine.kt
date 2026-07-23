package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.FileNames
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationProgress
import com.appblish.jgallery.core.model.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * The *policy* half of the file-operation core (spec §7): given a batch of ids and the low-level
 * [StorageOps] primitives, it drives copy / move / rename / trash / delete and produces the
 * "X done, Y failed" reporting the spec mandates.
 *
 * Everything here is platform-free and pure enough to unit-test on the JVM — the only thing it can
 * do is call the injected [ops] SPI. That is deliberate: collision naming, the chunked streaming
 * loop, cooperative cancellation, and result aggregation are the parts most likely to have subtle
 * bugs, so they live where a fake `StorageOps` can exercise every branch without a device.
 *
 * Guarantees:
 * - **Off-thread:** every entry point runs on [io]; nothing touches the caller's thread.
 * - **Streaming:** bytes are copied in [bufferSize] chunks — a whole file/video is never buffered
 *   in memory (spec §1 rules 3, 4).
 * - **Cancellation:** the stream loop checks [ensureActive] between chunks, so cancelling the
 *   collector stops a large copy promptly and the partial destination is discarded via [Sink.abort].
 * - **Isolation of failures:** one item failing (missing source, IO error) is recorded in the
 *   summary and does not abort the batch; only cancellation stops it.
 */
internal class FileOperationEngine(
    private val ops: StorageOps,
    private val io: CoroutineDispatcher,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) {

    /** Copy every id into [destinationBucketId]; originals remain (spec §7.1). */
    fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = flow {
        val reserved = ops.namesInBucket(destinationBucketId).toMutableSet()
        runBulk(ids) { id, name -> copyOne(id, name, destinationBucketId, reserved) }
    }.flowOn(io)

    /**
     * "Save a copy" — export every id into the user-picked SAF tree [treeUri] (G2 · APP-549). Reuses
     * the exact copy machinery (streaming, cancellation, collision naming, per-item failure isolation,
     * progress + summary); only the *destination* differs — a document created in the granted tree
     * instead of a MediaStore bucket row. Originals remain, like [copy].
     */
    fun exportCopy(ids: List<MediaId>, treeUri: String): Flow<FileOperationEvent> = flow {
        val reserved = ops.namesInTree(treeUri).toMutableSet()
        runBulk(ids) { id, name -> exportOne(id, name, treeUri, reserved) }
    }.flowOn(io)

    /**
     * Move every id into [destinationBucketId]: copy, then remove the source (spec §7.2). Name
     * collisions in the destination are resolved just like copy. If the copy succeeds but the
     * source cannot be removed, the item is reported as failed and the copy is intentionally kept
     * (losing the only surviving bytes on a partial move would be worse than a duplicate).
     */
    fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> = flow {
        val reserved = ops.namesInBucket(destinationBucketId).toMutableSet()
        runBulk(ids) { id, name ->
            copyOne(id, name, destinationBucketId, reserved)
            if (!ops.delete(id)) error("copied, but the source could not be removed")
        }
    }.flowOn(io)

    /** Move every id to the app-managed Trash — restorable (spec §7.5). */
    fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> = flow {
        runBulk(ids) { id, _ -> if (!ops.trash(id)) error("item no longer exists") }
    }.flowOn(io)

    /** Permanently remove every id from device storage (spec §7.5). */
    fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> = flow {
        runBulk(ids) { id, _ -> if (!ops.delete(id)) error("item no longer exists") }
    }.flowOn(io)

    /**
     * Rename a single item's real file via the storage layer (spec §7.3).
     *
     * The new name is normalized through the shared [FileNames] policy: illegal/blank names are
     * rejected with the same message the UI shows inline, and the **original extension is always
     * preserved** so a `DISPLAY_NAME` write can't strip or change the file's type. A no-op (same name)
     * short-circuits to success. A storage-layer refusal (e.g. a colliding name in the same folder)
     * surfaces as a clear, non-corrupting failure rather than a raw provider exception.
     */
    suspend fun rename(id: MediaId, newDisplayName: String): OperationResult = withContext(io) {
        val current = ops.displayName(id) ?: return@withContext failure(id, "item no longer exists")
        val newName = when (val v = FileNames.normalizeRename(newDisplayName, current)) {
            is FileNames.Result.Invalid -> return@withContext failure(id, v.reason)
            is FileNames.Result.Valid -> v.name
        }
        if (newName == current) return@withContext OperationResult(succeeded = 1, failed = 0)
        val ok = try {
            ops.rename(id, newName)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return@withContext failure(id, t.message ?: t.javaClass.simpleName)
        }
        if (ok) OperationResult(succeeded = 1, failed = 0) else failure(id, "That name may already be in use")
    }

    /**
     * Rename an album/folder as an entity (spec §7.3, §11): a folder rename manifests through
     * MediaStore as moving every member row into a sibling folder whose last path segment is
     * [newName]. Pure policy over [ops] — name validation, a no-op when the name is unchanged, and a
     * per-member relocation with failure isolation aggregated into a "moved / failed" summary; the
     * `RELATIVE_PATH` rewrite itself is device-verified in the instrumented test.
     *
     * The failure marker uses [bucketId] (not a member id) so a whole-album failure — invalid name,
     * album gone — is attributable without a member handle.
     */
    suspend fun renameAlbum(bucketId: String, newName: String): OperationResult = withContext(io) {
        val marker = MediaId(bucketId)
        val validName = when (val v = AlbumNames.validate(newName)) {
            is AlbumNames.Result.Invalid -> return@withContext failure(marker, v.reason)
            is AlbumNames.Result.Valid -> v.name
        }
        val currentPath = ops.albumRelativePath(bucketId)
            ?: return@withContext failure(marker, "album no longer exists")
        if (AlbumPaths.leaf(currentPath) == validName) {
            return@withContext OperationResult(succeeded = 1, failed = 0) // unchanged → no-op success
        }
        val newPath = AlbumPaths.renameLeaf(currentPath, validName)
        val ids = ops.idsInBucket(bucketId)
        if (ids.isEmpty()) return@withContext failure(marker, "album no longer exists")

        var succeeded = 0
        val failures = ArrayList<OperationResult.Failure>()
        for (id in ids) {
            val ok = try {
                ops.moveToFolder(id, newPath)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                false
            }
            if (ok) succeeded++ else failures += OperationResult.Failure(id, "could not move into renamed folder")
        }
        OperationResult(succeeded, failures.size, failures)
    }

    // --- internals ---

    /**
     * Runs [action] for each id, isolating per-item failures into the summary, emitting an
     * [FileOperationEvent.InProgress] after every item and a terminal [FileOperationEvent.Completed]
     * at the end. Cancellation propagates (no terminal event) so the collector can tell a finished
     * batch from a cancelled one.
     */
    private suspend fun FlowCollector<FileOperationEvent>.runBulk(
        ids: List<MediaId>,
        action: suspend (id: MediaId, name: String?) -> Unit,
    ) {
        var succeeded = 0
        val failures = ArrayList<OperationResult.Failure>()
        val total = ids.size
        ids.forEachIndexed { index, id ->
            val name = runCatching { ops.displayName(id) }.getOrNull()
            try {
                action(id, name)
                succeeded++
            } catch (c: CancellationException) {
                throw c // never counted as a failure — the whole batch is being cancelled
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

    private suspend fun copyOne(
        id: MediaId,
        name: String?,
        destinationBucketId: String,
        reserved: MutableSet<String>,
    ) {
        val target = reserveTarget(name, reserved)
        streamInto(id, ops.createSink(destinationBucketId, target, ops.mimeType(id)))
    }

    /** Export one id into the SAF tree [treeUri] — copy semantics with a tree-document sink (APP-549). */
    private suspend fun exportOne(
        id: MediaId,
        name: String?,
        treeUri: String,
        reserved: MutableSet<String>,
    ) {
        val target = reserveTarget(name, reserved)
        streamInto(id, ops.createTreeSink(treeUri, target, ops.mimeType(id)))
    }

    /** Resolve [name]'s collision-free target and reserve it so two items never race onto one name. */
    private fun reserveTarget(name: String?, reserved: MutableSet<String>): String {
        requireNotNull(name) { "source no longer exists" }
        val target = resolveCollision(name, reserved)
        reserved += target
        return target
    }

    /** Stream a source id's bytes into [sink], committing on success and aborting the partial on failure. */
    private suspend fun streamInto(id: MediaId, sink: Sink) {
        try {
            ops.openInput(id).use { input -> sink.output.use { out -> streamCopy(input, out) } }
            sink.commit()
        } catch (t: Throwable) {
            sink.abort() // discard the partial destination on failure OR cancellation
            throw t
        }
    }

    /** Chunked copy that yields to cancellation between chunks and never buffers the whole file. */
    private suspend fun streamCopy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(bufferSize)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    /**
     * Resolve a name collision the way a gallery does: "IMG.jpg" → "IMG (1).jpg" → "IMG (2).jpg".
     * The extension (last dot, not for dotfiles like ".nomedia") is preserved so the copy stays a
     * valid image/video. [reserved] holds both destination names and names already claimed earlier
     * in this same batch.
     */
    private fun resolveCollision(name: String, reserved: Set<String>): String {
        if (name !in reserved) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (candidate !in reserved) return candidate
            n++
        }
    }

    private fun failure(id: MediaId, reason: String) =
        OperationResult(succeeded = 0, failed = 1, failures = listOf(OperationResult.Failure(id, reason)))

    private companion object {
        // 64 KiB: large enough to keep throughput high on big videos, small enough that memory stays
        // flat regardless of file size and cancellation is checked frequently.
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
    }
}
