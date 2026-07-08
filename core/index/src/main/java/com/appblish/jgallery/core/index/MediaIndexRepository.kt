package com.appblish.jgallery.core.index

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.storage.MediaQuery
import kotlinx.coroutines.flow.Flow

/**
 * The cached, incrementally-updated media index (spec §1 rule 4). Features observe this — they never
 * query MediaStore or the storage layer directly. The index enumerates ONCE via `:core:storage`,
 * caches (path, date, size, type), and updates incrementally rather than re-scanning on every open.
 *
 * The full incremental sync (change observation + signature diff) is delivered in APP-272; this
 * interface is the contract feature modules build against now.
 */
interface MediaIndexRepository {

    /** Albums with covers + counts (Albums tab), served from cache and refreshed incrementally. */
    fun observeAlbums(): Flow<List<Album>>

    /** Media matching [query] (Photos tab / album detail), served from the cached index. */
    fun observeMedia(query: MediaQuery): Flow<List<MediaItem>>

    /** Force a full re-enumeration (e.g. pull-to-refresh). Incremental sync is automatic otherwise. */
    suspend fun refresh()
}
