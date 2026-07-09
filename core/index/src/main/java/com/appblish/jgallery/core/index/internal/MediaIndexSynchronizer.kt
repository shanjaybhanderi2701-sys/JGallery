package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles the persisted index against the current device library, incrementally.
 *
 * One [sync] does: (1) a cheap minimal-projection signature scan of the whole library via the storage
 * boundary; (2) a diff against the persisted signatures; (3) a full-column re-read of ONLY the
 * new/changed rows; (4) a delete of rows gone from the device. Unchanged rows are never re-read or
 * re-written, so this is not a full re-scan — the first sync on an empty cache naturally degenerates
 * to a one-time full enumeration (spec §1 rule 4).
 *
 * Runs on [io] and is serialized by a [Mutex] so a burst of ContentObserver signals collapses to
 * sequential, non-overlapping syncs.
 */
@Singleton
internal class MediaIndexSynchronizer @Inject constructor(
    private val storage: StorageAccess,
    private val store: MediaIndexStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    suspend fun sync(): IndexDelta = withContext(io) {
        mutex.withLock {
            val current = storage.queryMediaSignatures().map { it.toIndexSignature() }
            val persisted = store.persistedSignatures()
            val delta = computeIndexDelta(persisted = persisted, current = current)
            if (delta.isEmpty) return@withLock IndexDelta.EMPTY

            if (delta.changedIds.isNotEmpty()) {
                val changed = storage.queryMedia(MediaQuery(ids = delta.changedIds))
                store.upsert(changed)
            }
            if (delta.deletedIds.isNotEmpty()) {
                store.delete(delta.deletedIds)
            }
            delta
        }
    }
}

/** Bridge the storage boundary's signature type to the index's pure fingerprint. */
private fun com.appblish.jgallery.core.storage.MediaSignature.toIndexSignature(): IndexSignature =
    IndexSignature(id = id, dateModifiedMillis = dateModifiedMillis, sizeBytes = sizeBytes)
