package com.appblish.jgallery.core.index.internal

import android.net.Uri
import com.appblish.jgallery.core.index.AlbumCapture
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.CaptureKind
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.storage.StorageAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin delegation of [MediaOperationsRepository] to the §1.6 [StorageAccess] E8 primitives. The
 * indirection keeps `:core:storage` off every feature's classpath (only `:core:index` sees it) while
 * adding zero behaviour of its own — progress, streaming, collision handling and rollback all live in
 * the storage engine.
 */
@Singleton
internal class StorageMediaOperationsRepository @Inject constructor(
    private val storage: StorageAccess,
) : MediaOperationsRepository {

    override suspend fun createAlbum(name: String): OperationResult = storage.createAlbum(name)

    override suspend fun rename(id: MediaId, newDisplayName: String): OperationResult =
        storage.rename(id, newDisplayName)

    override suspend fun viewUri(id: MediaId): Uri? = storage.viewUri(id)

    override fun copy(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> =
        storage.copy(ids, destinationBucketId)

    override fun move(ids: List<MediaId>, destinationBucketId: String): Flow<FileOperationEvent> =
        storage.move(ids, destinationBucketId)

    override fun exportCopy(ids: List<MediaId>, treeUri: Uri): Flow<FileOperationEvent> =
        storage.exportCopy(ids, treeUri)

    override fun copyToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> =
        storage.copyToNewAlbum(ids, name)

    override fun moveToNewAlbum(ids: List<MediaId>, name: String): Flow<FileOperationEvent> =
        storage.moveToNewAlbum(ids, name)

    override suspend fun beginCapture(albumName: String, kind: CaptureKind): AlbumCapture? =
        storage.beginCapture(albumName, kind)?.let(::StorageAlbumCapture)

    override suspend fun sweepOrphanedCaptures(): Int = storage.sweepOrphanedCaptures()

    override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> =
        storage.moveToTrash(ids)

    override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> =
        storage.deletePermanently(ids)

    override suspend fun renameAlbum(bucketId: String, newName: String): OperationResult =
        storage.renameAlbum(bucketId, newName)

    override fun copyAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> =
        overAlbumMembers(bucketId) { ids -> storage.copy(ids, destinationBucketId) }

    override fun moveAlbum(bucketId: String, destinationBucketId: String): Flow<FileOperationEvent> =
        overAlbumMembers(bucketId) { ids -> storage.move(ids, destinationBucketId) }

    override fun deleteAlbum(bucketId: String): Flow<FileOperationEvent> =
        overAlbumMembers(bucketId) { ids -> storage.moveToTrash(ids) }

    /**
     * Enumerate an album's members through the §1.6 read seam (authoritative, not a stale UI
     * snapshot) then run [op] over them — so album Copy/Move/Delete reuse the exact per-item engine
     * primitives with all their progress/streaming/rollback guarantees. An album with no members
     * emits a single empty terminal event so collectors always see a completion.
     */
    private fun overAlbumMembers(
        bucketId: String,
        op: (List<MediaId>) -> Flow<FileOperationEvent>,
    ): Flow<FileOperationEvent> = flow {
        val ids = storage.queryMedia(MediaQuery(bucketId = bucketId)).map { it.id }
        if (ids.isEmpty()) {
            emit(FileOperationEvent.Completed(OperationResult(succeeded = 0, failed = 0)))
        } else {
            emitAll(op(ids))
        }
    }
}
