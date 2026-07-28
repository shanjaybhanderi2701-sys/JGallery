package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.TimelineSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject

/** Room-backed [MediaIndexStore]. Pure mapping over [MediaDao]; no business logic lives here. */
internal class RoomMediaIndexStore @Inject constructor(
    private val dao: MediaDao,
) : MediaIndexStore {

    override fun observeMedia(): Flow<List<MediaItem>> =
        dao.observeAll().map { rows -> rows.map(MediaEntity::toMediaItem) }

    override fun observeAlbums(): Flow<List<Album>> =
        dao.observeAlbums().map { rows -> rows.map(AlbumAggregate::toAlbum) }

    override fun observeDayCounts(spec: TimelineSpec): Flow<List<DayCountRow>> =
        dao.observeDayCounts(TimelineQueries.dayCounts(spec))

    override fun observeDayKeys(spec: TimelineSpec): Flow<List<Long>> =
        dao.observeDayKeys(TimelineQueries.dayKeys(spec))

    override suspend fun loadWindow(spec: TimelineSpec, offset: Int, limit: Int): List<MediaItem> =
        dao.loadWindow(TimelineQueries.window(spec, offset = offset, limit = limit))
            .map(MediaEntity::toMediaItem)

    override suspend fun loadIds(spec: TimelineSpec): List<MediaId> =
        dao.loadIds(TimelineQueries.ids(spec)).map(::MediaId)

    override suspend fun persistedSignatures(): List<IndexSignature> =
        dao.signatures().map(SignatureRow::toIndexSignature)

    override suspend fun upsert(items: List<MediaItem>) {
        if (items.isEmpty()) return
        // Denormalize in the current (sync-time) zone — APP-704 D4. A later zone change rebuilds the cache.
        val zone = ZoneId.systemDefault()
        dao.upsert(items.map { it.toEntity(zone) })
    }

    override suspend fun delete(ids: Collection<MediaId>) {
        if (ids.isEmpty()) return
        // Chunk under SQLite's bound-parameter limit; deletions per sync are usually tiny, but a bulk
        // "clear an album off the device" must not blow the `IN (...)` limit.
        ids.map { it.value }.chunked(DELETE_CHUNK).forEach { dao.deleteByIds(it) }
    }

    override suspend fun count(): Int = dao.count()

    private companion object {
        const val DELETE_CHUNK = 900
    }
}
