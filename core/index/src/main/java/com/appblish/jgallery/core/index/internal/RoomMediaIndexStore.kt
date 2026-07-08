package com.appblish.jgallery.core.index.internal

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Room-backed [MediaIndexStore]. Pure mapping over [MediaDao]; no business logic lives here. */
internal class RoomMediaIndexStore @Inject constructor(
    private val dao: MediaDao,
) : MediaIndexStore {

    override fun observeMedia(): Flow<List<MediaItem>> =
        dao.observeAll().map { rows -> rows.map(MediaEntity::toMediaItem) }

    override fun observeAlbums(): Flow<List<Album>> =
        dao.observeAlbums().map { rows -> rows.map(AlbumAggregate::toAlbum) }

    override suspend fun persistedSignatures(): List<IndexSignature> =
        dao.signatures().map(SignatureRow::toIndexSignature)

    override suspend fun upsert(items: List<MediaItem>) {
        if (items.isEmpty()) return
        dao.upsert(items.map(MediaItem::toEntity))
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
