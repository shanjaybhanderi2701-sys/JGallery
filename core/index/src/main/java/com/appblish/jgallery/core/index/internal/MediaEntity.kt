package com.appblish.jgallery.core.index.internal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType

/**
 * The persisted index row (path/date/size/type — spec §1 rule 4). Persisting the cache is what lets
 * the app open straight into the last-known library and reconcile incrementally, rather than
 * re-scanning MediaStore on every launch. [bucketId] is indexed because album/bucket queries and the
 * albums aggregate group by it.
 */
@Entity(tableName = "media", indices = [Index("bucketId")])
internal data class MediaEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val type: String, // MediaType.name
    val bucketId: String,
    val bucketName: String,
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val mimeType: String,
)

/** DAO projection for the cheap change-detection scan — only the columns the delta needs. */
internal data class SignatureRow(
    val id: String,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val displayName: String,
    val bucketId: String,
)

/**
 * DAO projection for the Albums tab. `coverId` and `bucketName` are bare columns taken from the row
 * that holds `MAX(dateTakenMillis)` — SQLite's documented single-max bare-column rule — so the cover
 * is the newest item in the bucket (matches the reference gallery).
 */
internal data class AlbumAggregate(
    val bucketId: String,
    val bucketName: String,
    val itemCount: Int,
    val coverId: String?,
    val newestItemMillis: Long,
)

internal fun MediaItem.toEntity(): MediaEntity = MediaEntity(
    id = id.value,
    displayName = displayName,
    type = type.name,
    bucketId = bucketId,
    bucketName = bucketName,
    dateTakenMillis = dateTakenMillis,
    dateModifiedMillis = dateModifiedMillis,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    mimeType = mimeType,
)

internal fun MediaEntity.toMediaItem(): MediaItem = MediaItem(
    id = MediaId(id),
    displayName = displayName,
    type = enumValueOf<MediaType>(type),
    bucketId = bucketId,
    bucketName = bucketName,
    dateTakenMillis = dateTakenMillis,
    dateModifiedMillis = dateModifiedMillis,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    durationMillis = durationMillis,
    mimeType = mimeType,
)

internal fun SignatureRow.toIndexSignature(): IndexSignature = IndexSignature(
    id = MediaId(id),
    dateModifiedMillis = dateModifiedMillis,
    sizeBytes = sizeBytes,
    displayName = displayName,
    bucketId = bucketId,
)

internal fun AlbumAggregate.toAlbum(): Album = Album(
    bucketId = bucketId,
    name = bucketName,
    itemCount = itemCount,
    cover = coverId?.let { MediaId(it) },
    newestItemMillis = newestItemMillis,
)
