package com.appblish.jgallery.core.storage.internal

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

    /** Rename a single item/album's real file/folder via the storage layer (spec §7.3). */
    suspend fun rename(id: MediaId, newDisplayName: String): OperationResult = withContext(io) {
        if (newDisplayName.isBlank()) return@withContext failure(id, "name must not be blank")
        val current = ops.displayName(id) ?: return@withContext failure(id, "item no longer exists")
        if (newDisplayName == current) return@withContext OperationResult(succeeded = 1, failed = 0)
        val ok = try {
            ops.rename(id, newDisplayName)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return@withContext failure(id, t.message ?: t.javaClass.simpleName)
        }
        if (ok) OperationResult(succeeded = 1, failed = 0) else failure(id, "rename failed")
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
        requireNotNull(name) { "source no longer exists" }
        val target = resolveCollision(name, reserved)
        reserved += target // reserve within this batch so two copies never race onto one name
        val sink = ops.createSink(destinationBucketId, target, ops.mimeType(id))
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
