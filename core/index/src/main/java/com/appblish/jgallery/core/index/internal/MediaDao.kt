package com.appblish.jgallery.core.index.internal

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
    @Query("SELECT id, dateModifiedMillis, sizeBytes, displayName FROM media")
    suspend fun signatures(): List<SignatureRow>

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
