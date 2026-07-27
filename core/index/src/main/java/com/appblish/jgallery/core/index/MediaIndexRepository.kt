package com.appblish.jgallery.core.index

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaQuery
import com.appblish.jgallery.core.model.TimelineSkeleton
import com.appblish.jgallery.core.model.TimelineSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    /**
     * The windowed-timeline API (APP-700, Option B). Splits the heavy per-cell payload (window it)
     * from the light structural skeleton (derive it cheaply), so a 50k library is never materialized,
     * sorted or re-sectioned in memory.
     *
     * [observeTimelineSkeleton] streams the sections + total for [spec] — a handful of rows that the
     * feature layer turns into headers, `sectionStarts`, per-cell ordinal and the fast-scroll bubble
     * arithmetically. It re-emits only on a real content change, not on every idle MediaStore signal.
     *
     * [loadWindow] fetches a viewport-sized page of tiles as scroll moves (SQL ORDER BY + LIMIT/OFFSET
     * — the sort is the authority, no in-memory re-sort). [mediaIdsMatching] projects the ids of the
     * whole [spec] set on demand, for select-all / share materialization without ever holding 50k ids.
     *
     * The three default to an empty timeline so the many surfaces that don't render one (Albums,
     * Search, Viewer and their test doubles) need not implement them; the cached Room repository and
     * the Photos tab override them with the real windowed behavior.
     */
    fun observeTimelineSkeleton(spec: TimelineSpec): Flow<TimelineSkeleton> = flowOf(TimelineSkeleton.EMPTY)

    suspend fun loadWindow(spec: TimelineSpec, offset: Int, limit: Int): List<MediaItem> = emptyList()

    suspend fun mediaIdsMatching(spec: TimelineSpec): List<MediaId> = emptyList()

    /** Force a full re-enumeration (e.g. pull-to-refresh). Incremental sync is automatic otherwise. */
    suspend fun refresh()
}
