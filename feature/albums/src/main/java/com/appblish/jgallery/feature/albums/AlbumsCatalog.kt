package com.appblish.jgallery.feature.albums

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.formatsPresentIn

/**
 * Pure, platform-free assembly of the Albums tab (spec C4). Kept out of the ViewModel so the whole of
 * the deterministic ordering + smart-album synthesis + folder-wise video grouping is unit-testable
 * against plain data — no coroutines, no Room, no Android.
 *
 * The tab is a single ordered list built from three inputs: the device folders served by the cached
 * index, the video subset of the index (for the Video smart album + its grouping), and the user's
 * persisted pin/sort preferences. Two smart albums are synthesized on top:
 *  - **Recent** — the whole library, newest-first (cover = newest item across all folders).
 *  - **Video** — all videos, opening into All-Videos + one sub-album per folder that holds videos.
 *
 * Ordering, top → bottom, is a total, deterministic order (spec C4 items 6 & 7):
 *  0. **Pinned** albums (any kind), among themselves by the active sort — pins win over everything.
 *  1. **Recent** smart album.
 *  2. **Camera** folder.
 *  3. **Screenshots** folder.
 *  4. **Video** smart album.
 *  5. every other device folder, by the active sort.
 * Camera/Screenshots/Video are the "priority" folders that always precede ordinary folders; Recent
 * leads as the marquee smart album. A pinned album is promoted out of its usual tier to the very top.
 */
internal object AlbumsCatalog {

    /** Stable sentinel bucket ids for the smart albums — recognized by album-detail navigation. */
    const val RECENT_BUCKET_ID = "__jgallery_recent__"
    const val VIDEO_BUCKET_ID = "__jgallery_video__"
    const val ALL_VIDEOS_BUCKET_ID = "__jgallery_all_videos__"

    const val RECENT_NAME = "Recent"
    const val VIDEO_NAME = "Video"
    const val ALL_VIDEOS_NAME = "All Videos"

    private const val CAMERA = "Camera"
    private const val SCREENSHOTS = "Screenshots"

    /** True for the always-sort-first named folders (Camera, Screenshots). Case-insensitive. */
    fun isPriorityFolder(album: Album): Boolean =
        album.kind == AlbumKind.DEVICE_FOLDER &&
            (album.name.equals(CAMERA, ignoreCase = true) || album.name.equals(SCREENSHOTS, ignoreCase = true))

    /**
     * The ordered Albums-tab list (spec C4). [deviceAlbums] are the index's real folders; [videos] is
     * the video subset of the index (used to build the Video smart album); [pinnedBucketIds] and
     * [sort] are the persisted user preferences.
     */
    fun buildAlbumsTab(
        deviceAlbums: List<Album>,
        videos: List<MediaItem>,
        pinnedBucketIds: Set<String>,
        sort: SortSpec,
        coverOverrides: Map<String, MediaId> = emptyMap(),
    ): List<Album> {
        val recent = recentAlbum(deviceAlbums)
        val video = videoAlbum(videos)

        val enrichedFolders = deviceAlbums.map { folder ->
            folder.copy(
                isPriority = isPriorityFolder(folder),
                pinned = folder.bucketId in pinnedBucketIds,
                // "Set as cover" (G1-11): the user's chosen cover wins over the index's newest-item default.
                cover = coverOverrides[folder.bucketId] ?: folder.cover,
            )
        }
        val smart = listOfNotNull(recent, video).map { it.copy(pinned = it.bucketId in pinnedBucketIds) }

        return (smart + enrichedFolders).sortedWith(comparator(sort))
    }

    /**
     * Per-bucket format presence (design C1-06): for each device folder, which of the non-`ALL` format
     * filters it holds at least one member of. Powers the Collections filter row — filtering *which*
     * albums show. Computed off the same cached media the tab is already built from (no rescan).
     */
    fun bucketFormats(media: List<MediaItem>): Map<String, Set<MediaFilter>> =
        media.groupBy { it.bucketId }.mapValues { (_, items) -> formatsPresentIn(items) }

    /**
     * Filter the Albums-tab list by the active format chip (design C1-06 callout 4): "Videos" surfaces
     * video-bearing albums, etc. [MediaFilter.ALL] is the identity. A device folder is kept when it
     * holds that format ([bucketFormats]); the Recent smart album is kept when the library holds it
     * anywhere; the Video smart album is a videos-only entry. Ordering is preserved.
     */
    fun applyFormatFilter(
        albums: List<Album>,
        filter: MediaFilter,
        bucketFormats: Map<String, Set<MediaFilter>>,
    ): List<Album> {
        if (filter == MediaFilter.ALL) return albums
        val presentAnywhere = bucketFormats.values.any { filter in it }
        return albums.filter { album ->
            when (album.kind) {
                AlbumKind.RECENT -> presentAnywhere
                AlbumKind.VIDEO -> filter == MediaFilter.VIDEOS
                AlbumKind.DEVICE_FOLDER -> filter in (bucketFormats[album.bucketId] ?: emptySet())
            }
        }
    }

    /**
     * "All Videos" + one sub-album per folder that contains videos (spec C4 item 5). This is the
     * folder-wise grouping shown when the Video smart album is opened; folders are newest-first with a
     * bucketId tiebreak so the order is deterministic. Empty when there are no videos.
     */
    fun videoFolderAlbums(videos: List<MediaItem>): List<Album> {
        val allVideos = allVideosAlbum(videos) ?: return emptyList()
        val folders = videos
            .groupBy { it.bucketId }
            .map { (bucketId, items) ->
                val newest = items.maxBy { it.dateTakenMillis }
                Album(
                    bucketId = bucketId,
                    name = newest.bucketName,
                    itemCount = items.size,
                    cover = newest.id,
                    newestItemMillis = newest.dateTakenMillis,
                    kind = AlbumKind.DEVICE_FOLDER,
                )
            }
            .sortedWith(compareByDescending<Album> { it.newestItemMillis }.thenBy { it.bucketId })
        return listOf(allVideos) + folders
    }

    /** The ordering comparator (see class doc): tier rank first, then the active sort within a tier. */
    fun comparator(sort: SortSpec): Comparator<Album> =
        compareBy<Album> { rank(it) }.then(sort.albumComparator())

    private fun rank(album: Album): Int = when {
        album.pinned -> 0
        album.kind == AlbumKind.RECENT -> 1
        album.kind == AlbumKind.DEVICE_FOLDER && album.name.equals(CAMERA, ignoreCase = true) -> 2
        album.kind == AlbumKind.DEVICE_FOLDER && album.name.equals(SCREENSHOTS, ignoreCase = true) -> 3
        album.kind == AlbumKind.VIDEO -> 4
        else -> 5
    }

    /** Recent = the whole library, newest-first. Cover/newest come from the newest folder. */
    private fun recentAlbum(deviceAlbums: List<Album>): Album? {
        if (deviceAlbums.isEmpty()) return null
        val total = deviceAlbums.sumOf { it.itemCount }
        if (total == 0) return null
        val newestFolder = deviceAlbums.maxBy { it.newestItemMillis }
        return Album(
            bucketId = RECENT_BUCKET_ID,
            name = RECENT_NAME,
            itemCount = total,
            cover = newestFolder.cover,
            newestItemMillis = newestFolder.newestItemMillis,
            kind = AlbumKind.RECENT,
            isPriority = true,
        )
    }

    /** The Video smart album card (aggregate over every video). Null when there are no videos. */
    private fun videoAlbum(videos: List<MediaItem>): Album? {
        val aggregate = allVideosAlbum(videos) ?: return null
        return aggregate.copy(
            bucketId = VIDEO_BUCKET_ID,
            name = VIDEO_NAME,
            kind = AlbumKind.VIDEO,
            isPriority = true,
        )
    }

    /** The "All Videos" aggregate album over [videos] (video-typed items only). Null when empty. */
    private fun allVideosAlbum(videos: List<MediaItem>): Album? {
        val onlyVideos = videos.filter { it.type == MediaType.VIDEO }
        if (onlyVideos.isEmpty()) return null
        val newest = onlyVideos.maxBy { it.dateTakenMillis }
        return Album(
            bucketId = ALL_VIDEOS_BUCKET_ID,
            name = ALL_VIDEOS_NAME,
            itemCount = onlyVideos.size,
            cover = newest.id,
            newestItemMillis = newest.dateTakenMillis,
            kind = AlbumKind.DEVICE_FOLDER,
        )
    }
}
