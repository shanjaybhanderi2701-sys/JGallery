package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.index.TrashRepository
import com.appblish.jgallery.core.model.FileOperationEvent
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.TrashEntry
import com.appblish.jgallery.core.storage.StorageAccess
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin delegation of [TrashRepository] to the §1.6 [StorageAccess] Recycle-Bin operations. The
 * indirection keeps `:core:storage` off the Trash feature's classpath (only `:core:index` sees it)
 * while adding zero behaviour — retention metadata, restore-to-origin, purge and rollback all live in
 * the storage engine.
 */
@Singleton
internal class StorageTrashRepository @Inject constructor(
    private val storage: StorageAccess,
) : TrashRepository {

    override fun observeTrash(): Flow<List<TrashEntry>> = storage.observeTrash()

    override fun restore(ids: List<MediaId>): Flow<FileOperationEvent> = storage.restoreFromTrash(ids)

    override fun deletePermanently(ids: List<MediaId>): Flow<FileOperationEvent> =
        storage.deletePermanently(ids)

    override fun emptyTrash(): Flow<FileOperationEvent> = storage.emptyTrash()

    override suspend fun purgeExpired(): Int = storage.purgeExpiredTrash()
}
