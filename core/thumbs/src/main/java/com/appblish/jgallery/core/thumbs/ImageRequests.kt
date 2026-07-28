package com.appblish.jgallery.core.thumbs

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.thumbs.internal.thumbnailMemoryKey

/**
 * The model feature code passes to Coil (`AsyncImage(model = item.thumbnailRequest())`) for a GRID
 * TILE. Loads go through the cached thumbnail pipeline (spec §1 rule 2): in-memory LRU → on-disk
 * downsized cache → a size-capped source from `:core:storage` — never a full-size decode.
 *
 * [versionMillis] is the invalidation token, sourced from the E3 index (`dateModifiedMillis`): when
 * a file changes on disk the index observes it, emits a new version, and every stale cache layer
 * misses naturally. Nothing is invalidated by hand.
 */
data class ThumbnailRequest(
    val id: MediaId,
    val versionMillis: Long,
)

/**
 * The model for a FULL-QUALITY load (the E7 viewer). Streams the original bytes through the §1.6
 * boundary; the thumbnail disk cache is not involved. Coil still decodes to the requested target
 * size (subsampled), so even the viewer never allocates a needlessly full-size bitmap.
 */
data class FullImageRequest(
    val id: MediaId,
    val versionMillis: Long,
)

/** Grid-tile request for this item, versioned by the index's last-modified time. */
fun MediaItem.thumbnailRequest(): ThumbnailRequest = ThumbnailRequest(id, dateModifiedMillis)

/**
 * The Coil in-memory cache key for this grid-tile request — exactly the [ThumbnailKeyer] output.
 *
 * Exposed for the APP-709 progressive two-pass decode: the key is **size-agnostic** (`id` + version
 * only — Coil size-checks the cached bitmap on read), so a coarse low-res entry and the crisp
 * display-size entry share it. A tile whose crisp decode is still in flight can therefore paint a
 * warm coarse entry as an instant placeholder via `placeholderMemoryCacheKey`, and the crisp result
 * simply replaces the coarse one in the LRU when it lands. Because Coil treats a *larger* cached
 * bitmap as valid for a smaller request, a later coarse warm can never downgrade a crisp entry — it
 * resolves as a cache hit and skips the decode.
 */
fun ThumbnailRequest.memoryCacheKey(): String = thumbnailMemoryKey(this)

/** Full-quality request for this item (viewer), versioned by the index's last-modified time. */
fun MediaItem.fullImageRequest(): FullImageRequest = FullImageRequest(id, dateModifiedMillis)

/**
 * Cover tile request for this album, or null when the album has no cover. Versioned by the album's
 * newest-item time: when the newest item changes, the cover row changes with it, so the stale cover
 * ages out of the caches by key rather than by manual eviction.
 */
fun Album.coverRequest(): ThumbnailRequest? = cover?.let { ThumbnailRequest(it, newestItemMillis) }
