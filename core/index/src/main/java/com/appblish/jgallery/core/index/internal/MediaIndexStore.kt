package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Persistence port for the index. Keeping it an interface (Room is the impl) means the synchronizer
 * and repository are unit-testable against an in-memory fake, and the storage engine stays swappable.
 */
internal interface MediaIndexStore {

    /** The full cached library as a cold, auto-refreshing stream. */
    fun observeMedia(): Flow<List<MediaItem>>

    /** Albums (buckets) with cover + count, refreshed whenever the cache changes. */
    fun observeAlbums(): Flow<List<Album>>

    /** Fingerprints of every persisted row, for the incremental delta. */
    suspend fun persistedSignatures(): List<IndexSignature>

    /** Insert-or-update the given rows (the delta's new/changed items). */
    suspend fun upsert(items: List<MediaItem>)

    /** Drop rows that no longer exist on the device. */
    suspend fun delete(ids: Collection<MediaId>)

    /** Number of cached rows (0 ⇒ never indexed ⇒ first sync is a full enumeration). */
    suspend fun count(): Int
}
