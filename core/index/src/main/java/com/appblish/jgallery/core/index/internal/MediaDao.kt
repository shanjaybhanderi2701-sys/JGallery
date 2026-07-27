package com.appblish.jgallery.core.index.internal

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/**
 * Room access to the persisted index. Reads are `Flow`s so the UI observes the cache and Room re-emits
 * automatically whenever an incremental sync upserts/deletes rows. Writes are `suspend` (off the main
 * thread by construction).
 */
@Dao
internal interface MediaDao {

    /** The whole cached library, newest-first is applied by the repository per the active sort. */
    @Query("SELECT * FROM media")
    fun observeAll(): Flow<List<MediaEntity>>

    /** Minimal-projection scan for change detection — no full-column read for unchanged rows. */
    @Query("SELECT id, dateModifiedMillis, sizeBytes, displayName, bucketId FROM media")
    suspend fun signatures(): List<SignatureRow>

    // --- APP-700 windowed timeline (Option B). Dynamic WHERE/ORDER/LIMIT is built by TimelineQueries.

    /**
     * Section skeleton for the **time-sort** path: one row per `localDayKey`, ordered by day. Observed
     * (Room re-emits on `media` invalidation) so the timeline layout tracks the cache — but only a
     * handful of rows cross the boundary, never N. The repository folds days into month/year runs.
     */
    @RawQuery(observedEntities = [MediaEntity::class])
    fun observeDayCounts(query: SupportSQLiteQuery): Flow<List<DayCountRow>>

    /**
     * Ordered per-item `localDayKey` projection for the **name/size-sort** path, where the date section
     * key is not monotonic along the sort (APP-704 F2/D3) so `GROUP BY` cannot reproduce the
     * interleaved boundaries. Primitives only — no `MediaItem`, no N-length String arrays — so the
     * dominant resident cost is still deleted. The repository run-length-encodes it into sections.
     */
    @RawQuery(observedEntities = [MediaEntity::class])
    fun observeDayKeys(query: SupportSQLiteQuery): Flow<List<Long>>

    /** A viewport-sized page of tiles (ORDER BY + LIMIT/OFFSET). Re-fetched as scroll moves. */
    @RawQuery
    suspend fun loadWindow(query: SupportSQLiteQuery): List<MediaEntity>

    /** Ids-only projection for on-demand select-all / share materialization (APP-704 D6). */
    @RawQuery
    suspend fun loadIds(query: SupportSQLiteQuery): List<String>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(items: List<MediaEntity>)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /**
     * Albums (buckets) with cover + count. `coverId`/`bucketName` are bare columns pulled from the
     * `MAX(dateTakenMillis)` row (SQLite single-max rule) → cover = newest item in the bucket.
     */
    @Query(
        """
        SELECT bucketId,
               bucketName,
               COUNT(*) AS itemCount,
               id AS coverId,
               MAX(dateTakenMillis) AS newestItemMillis
        FROM media
        GROUP BY bucketId
        ORDER BY newestItemMillis DESC
        """,
    )
    fun observeAlbums(): Flow<List<AlbumAggregate>>
}
