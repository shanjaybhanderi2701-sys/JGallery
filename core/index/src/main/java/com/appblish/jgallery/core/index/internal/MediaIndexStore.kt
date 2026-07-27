package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.TimelineSpec
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

    /**
     * Section skeleton primitives for [spec] (APP-700). Two shapes, chosen by the repository from the
     * sort: [observeDayCounts] for the monotonic time-sort `GROUP BY`, [observeDayKeys] for the
     * non-monotonic name/size projection. Both re-emit on `media` invalidation — a handful of rows /
     * primitives, never the materialized library.
     */
    fun observeDayCounts(spec: TimelineSpec): Flow<List<DayCountRow>>

    fun observeDayKeys(spec: TimelineSpec): Flow<List<Long>>

    /** A viewport page of tiles for [spec] (ORDER BY + LIMIT/OFFSET). */
    suspend fun loadWindow(spec: TimelineSpec, offset: Int, limit: Int): List<MediaItem>

    /** Ids-only projection for [spec], in display order (on-demand select-all / share). */
    suspend fun loadIds(spec: TimelineSpec): List<MediaId>

    /** Fingerprints of every persisted row, for the incremental delta. */
    suspend fun persistedSignatures(): List<IndexSignature>

    /** Insert-or-update the given rows (the delta's new/changed items). */
    suspend fun upsert(items: List<MediaItem>)

    /** Drop rows that no longer exist on the device. */
    suspend fun delete(ids: Collection<MediaId>)

    /** Number of cached rows (0 ⇒ never indexed ⇒ first sync is a full enumeration). */
    suspend fun count(): Int
}
