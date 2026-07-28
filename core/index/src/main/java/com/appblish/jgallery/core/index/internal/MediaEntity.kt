package com.appblish.jgallery.core.index.internal

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import java.time.Instant
import java.time.ZoneId

/**
 * The persisted index row (path/date/size/type — spec §1 rule 4). Persisting the cache is what lets
 * the app open straight into the last-known library and reconcile incrementally, rather than
 * re-scanning MediaStore on every launch. [bucketId] is indexed because album/bucket queries and the
 * albums aggregate group by it.
 *
 * APP-700 (Option B) denormalizes four **sort/section-authority** columns so the timeline sort +
 * window + section skeleton run entirely in indexed SQL instead of a fully-materialized, re-sorted
 * in-memory list:
 * - [effectiveTimeMillis] — `dateTaken` when present else `dateModified` (the capture-time the
 *   timeline actually orders by); indexed `(effectiveTimeMillis, id)` to back the default window.
 * - [localDayKey] — the epoch-day of [effectiveTimeMillis] **in the sync-time zone** (APP-704 D4);
 *   the `GROUP BY` key for the day/month/year section skeleton. Computed in Kotlin (SQLite `localtime`
 *   is unreliable); a timezone change ⇒ rebuild the rebuildable cache.
 * - [nameSortKey] — `displayName.lowercase()` so a BINARY `ORDER BY` matches the Kotlin name comparator.
 * - [formatBucket] — the [MediaFilter] partition the row belongs to ("PHOTOS"/"VIDEOS"/"GIFS"), so the
 *   format chip filters in the SQL WHERE (APP-704 D5) without re-implementing the format classifier
 *   in SQL. "ALL" is never stored — it is the absence of a bucket constraint.
 */
@Entity(
    tableName = "media",
    indices = [
        Index("bucketId"),
        Index("effectiveTimeMillis", "id"),
        Index("localDayKey"),
        Index("nameSortKey"),
        Index("sizeBytes"),
    ],
)
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
    val effectiveTimeMillis: Long,
    val localDayKey: Long,
    val nameSortKey: String,
    val formatBucket: String, // MediaFilter.bucketOf(...).name — PHOTOS | VIDEOS | GIFS
)

/**
 * DAO projection for the time-sort section skeleton (APP-700): one row per `localDayKey` with its
 * item count, ordered by day. The repository folds these into DAY/MONTH/YEAR [com.appblish.jgallery.core.model.SectionRun]s.
 */
internal data class DayCountRow(
    val dayKey: Long,
    val itemCount: Int,
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

/**
 * Capture time when known, file-modified time otherwise — the moment the timeline orders by. Mirrors
 * the feature-layer `effectiveTimeMillis`; kept here so the denormalized column is written from the
 * same rule the display path reads.
 */
internal val MediaItem.effectiveTimeMillis: Long
    get() = if (dateTakenMillis > 0) dateTakenMillis else dateModifiedMillis

/**
 * Persist a row, denormalizing the sort/section-authority columns in [zone] (the sync-time zone —
 * APP-704 D4). The epoch-day is computed the same way the display path buckets dates, so the SQL
 * section skeleton matches `buildPhotosTimeline` when the sync and display zones agree.
 */
internal fun MediaItem.toEntity(zone: ZoneId): MediaEntity {
    val effective = effectiveTimeMillis
    return MediaEntity(
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
        effectiveTimeMillis = effective,
        localDayKey = Instant.ofEpochMilli(effective).atZone(zone).toLocalDate().toEpochDay(),
        nameSortKey = displayName.lowercase(),
        formatBucket = MediaFilter.bucketOf(this).name,
    )
}

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
