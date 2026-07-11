package com.appblish.jgallery.core.model

/**
 * The top-bar format filter (design C1-06, item 3): one single-select chip row — **All · Photos ·
 * Videos · GIFs** — that scopes what a grid shows. It is platform-free and derived purely from indexed
 * metadata (type + the W3 format classifier), so the same value filters the in-memory index on both
 * the Photos and the Collections/Albums surfaces with no rescan.
 *
 * The three non-[ALL] filters partition the library exactly — every item is a video, a GIF, or a still
 * photo, and never two at once — so counts across the chips always add up to the whole:
 * - [PHOTOS] = still images (a GIF is a *GIF*, not a photo).
 * - [VIDEOS] = video items.
 * - [GIFS] = animated GIFs (the reliable proxy for the index's `isAnimated`).
 */
enum class MediaFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    GIFS;

    /** True when [item] belongs under this filter. [ALL] admits everything. */
    fun matches(item: MediaItem): Boolean = when (this) {
        ALL -> true
        VIDEOS -> item.type == MediaType.VIDEO
        GIFS -> item.type == MediaType.IMAGE && item.isAnimatedImage
        PHOTOS -> item.type == MediaType.IMAGE && !item.isAnimatedImage
    }

    companion object {
        /** The chip order rendered left-to-right; [ALL] is the default selection. */
        val ORDER: List<MediaFilter> = listOf(ALL, PHOTOS, VIDEOS, GIFS)
    }
}

/** Keep only the items admitted by [filter] (identity list for [MediaFilter.ALL]). */
fun List<MediaItem>.filteredBy(filter: MediaFilter): List<MediaItem> =
    if (filter == MediaFilter.ALL) this else filter { filter.matches(it) }

/** The set of non-[MediaFilter.ALL] filters that [items] contain at least one member of. */
fun formatsPresentIn(items: List<MediaItem>): Set<MediaFilter> {
    val present = mutableSetOf<MediaFilter>()
    for (item in items) {
        if (MediaFilter.VIDEOS.matches(item)) present += MediaFilter.VIDEOS
        else if (MediaFilter.GIFS.matches(item)) present += MediaFilter.GIFS
        else present += MediaFilter.PHOTOS
        if (present.size == 3) break
    }
    return present
}
