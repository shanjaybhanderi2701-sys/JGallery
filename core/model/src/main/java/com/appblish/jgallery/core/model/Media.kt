package com.appblish.jgallery.core.model

/** Photo or video. Kept platform-free so it is safe to pass across every layer. */
enum class MediaType { IMAGE, VIDEO }

/**
 * An opaque, stable handle to a piece of media. Feature code passes this around; only
 * `:core:storage` knows how to resolve it to a `content://` uri or file path. Keeping the
 * underlying locator opaque is what lets the storage layer swap All-Files-Access for media
 * permissions / SAF without changing any consumer (spec §1.6).
 */
@JvmInline
value class MediaId(val value: String)

/** A single indexed media entry. Times are epoch-millis; [sizeBytes] and dimensions may be 0 if unknown. */
data class MediaItem(
    val id: MediaId,
    val displayName: String,
    val type: MediaType,
    val bucketId: String,
    val bucketName: String,
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long, // 0 for images
    val mimeType: String,
)

/**
 * What an [Album] card represents on the Albums tab.
 *
 * The index only ever produces [DEVICE_FOLDER]s; the feature layer synthesizes [RECENT] and [VIDEO]
 * "smart" albums on top of the cached index and tags them so ordering and navigation can treat them
 * specially (spec C4: Recent album, Video album with folder-wise grouping).
 */
enum class AlbumKind {
    /** A real device folder (MediaStore bucket). */
    DEVICE_FOLDER,

    /** Synthetic "Recent" — the whole library, newest-first. */
    RECENT,

    /** Synthetic "Video" — all videos, with folder-wise sub-grouping. */
    VIDEO,
}

/**
 * A device folder / album with a cover + count, as shown on the Albums tab.
 *
 * [kind] distinguishes real folders from the synthetic Recent/Video smart albums. [isPriority] marks
 * the always-sort-first folders (Camera, Screenshots, Video — spec C4 item 7). [pinned] reflects the
 * user's persisted pin (spec C4 item 6); pinned albums sort above the priority folders. The last three
 * default so the index-produced album path (real folders) is unaffected — the feature layer enriches.
 */
data class Album(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val cover: MediaId?,
    val newestItemMillis: Long,
    val kind: AlbumKind = AlbumKind.DEVICE_FOLDER,
    val isPriority: Boolean = false,
    val pinned: Boolean = false,
)
