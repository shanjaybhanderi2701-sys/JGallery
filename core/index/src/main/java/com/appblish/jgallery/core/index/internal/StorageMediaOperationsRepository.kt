package com.appblish.jgallery.core.index.internal

import android.net.Uri
import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.OperationResult
import com.appblish.jgallery.core.storage.StorageAccess
import kotlinx.coroutines.flow.Flow
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

    override fun moveToTrash(ids: List<MediaId>): Flow<FileOperationEvent> =
        storage.moveToTrash(ids)

    override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> =
        storage.deletePermanently(ids)
}
