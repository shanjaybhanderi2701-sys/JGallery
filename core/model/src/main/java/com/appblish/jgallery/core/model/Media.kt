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

/** A device folder / album with a cover + count, as shown on the Albums tab. */
data class Album(
    val bucketId: String,
    val name: String,
    val itemCount: Int,
    val cover: MediaId?,
    val newestItemMillis: Long,
)
