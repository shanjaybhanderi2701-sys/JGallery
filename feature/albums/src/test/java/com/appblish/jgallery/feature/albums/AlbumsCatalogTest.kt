package com.appblish.jgallery.feature.albums

import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure assembly of the Albums tab (spec C4): Recent/Video smart-album synthesis, folder-wise video
 * grouping, deterministic priority + pin ordering. No coroutines/Room/Android — plain data in, list out.
 */
class AlbumsCatalogTest {

    private val defaultSort = SortSpec() // Last Modified, Descending

    // --- Recent smart album (item 4) ---------------------------------------------------------------

    @Test
    fun `Recent aggregates the whole library and covers with the newest folder`() {
        val albums = listOf(
            folder("camera", "Camera", count = 12, newest = 200, cover = "cam-cover"),
            folder("misc", "Misc", count = 3, newest = 500, cover = "misc-cover"),
        )
        val tab = AlbumsCatalog.buildAlbumsTab(albums, videos = emptyList(), pinnedBucketIds = emptySet(), sort = defaultSort)

        val recent = tab.single { it.kind == AlbumKind.RECENT }
        assertThat(recent.bucketId).isEqualTo(AlbumsCatalog.RECENT_BUCKET_ID)
        assertThat(recent.itemCount).isEqualTo(15) // 12 + 3
        assertThat(recent.cover).isEqualTo(MediaId("misc-cover")) // newest folder (newest=500)
        assertThat(tab.first().kind).isEqualTo(AlbumKind.RECENT) // Recent leads
    }

    @Test
    fun `empty library yields no smart albums`() {
        val tab = AlbumsCatalog.buildAlbumsTab(emptyList(), emptyList(), emptySet(), defaultSort)
        assertThat(tab).isEmpty()
    }

    // --- Video smart album + folder-wise grouping (items 4, 5) -------------------------------------

    @Test
    fun `Video smart album appears only when videos exist and aggregates all of them`() {
        val albums = listOf(folder("dcim", "DCIM", count = 4, newest = 10, cover = "c"))
        val videos = listOf(
            video("v1", bucket = "dcim", name = "DCIM", taken = 5),
            video("v2", bucket = "clips", name = "Clips", taken = 9),
        )
        val tab = AlbumsCatalog.buildAlbumsTab(albums, videos, emptySet(), defaultSort)

        val videoAlbum = tab.single { it.kind == AlbumKind.VIDEO }
        assertThat(videoAlbum.bucketId).isEqualTo(AlbumsCatalog.VIDEO_BUCKET_ID)
        assertThat(videoAlbum.itemCount).isEqualTo(2)
        assertThat(videoAlbum.cover).isEqualTo(MediaId("v2")) // newest video
        assertThat(videoAlbum.isPriority).isTrue()
    }

    @Test
    fun `no Video album when there are no videos`() {
        val tab = AlbumsCatalog.buildAlbumsTab(
            listOf(folder("a", "A", count = 1, newest = 1, cover = "c")),
            videos = emptyList(),
            pinnedBucketIds = emptySet(),
            sort = defaultSort,
        )
        assertThat(tab.none { it.kind == AlbumKind.VIDEO }).isTrue()
    }

    @Test
    fun `videoFolderAlbums groups videos by folder with an All-Videos header, newest-first`() {
        val videos = listOf(
            video("v1", bucket = "dcim", name = "DCIM", taken = 5),
            video("v2", bucket = "dcim", name = "DCIM", taken = 8),
            video("v3", bucket = "clips", name = "Clips", taken = 3),
        )
        val grouping = AlbumsCatalog.videoFolderAlbums(videos)

        assertThat(grouping.first().bucketId).isEqualTo(AlbumsCatalog.ALL_VIDEOS_BUCKET_ID)
        assertThat(grouping.first().itemCount).isEqualTo(3)

        val folders = grouping.drop(1)
        assertThat(folders.map { it.bucketId }).containsExactly("dcim", "clips").inOrder() // newest-first
        val dcim = folders.first { it.bucketId == "dcim" }
        assertThat(dcim.itemCount).isEqualTo(2)
        assertThat(dcim.cover).isEqualTo(MediaId("v2")) // newest video in the folder
        assertThat(dcim.newestItemMillis).isEqualTo(8)
    }

    @Test
    fun `videoFolderAlbums is empty when there are no videos`() {
        assertThat(AlbumsCatalog.videoFolderAlbums(emptyList())).isEmpty()
    }

    // --- Priority ordering (item 7) ---------------------------------------------------------------

    @Test
    fun `priority order is Recent then Camera then Screenshots then Video then other folders`() {
        val albums = listOf(
            folder("whatever", "Whatever", count = 1, newest = 999, cover = "c"), // newest, but ordinary
            folder("screenshots", "Screenshots", count = 2, newest = 100, cover = "c"),
            folder("camera", "Camera", count = 5, newest = 50, cover = "c"),
        )
        val videos = listOf(video("v1", bucket = "camera", name = "Camera", taken = 7))
        val tab = AlbumsCatalog.buildAlbumsTab(albums, videos, emptySet(), defaultSort)

        assertThat(tab.map { it.kindOrBucket() }).containsExactly(
            "RECENT", "camera", "screenshots", "VIDEO", "whatever",
        ).inOrder()
    }

    @Test
    fun `priority is case-insensitive on folder name`() {
        val albums = listOf(
            folder("cam", "camera", count = 1, newest = 10, cover = "c"),
            folder("z", "Zoo", count = 1, newest = 999, cover = "c"),
        )
        val tab = AlbumsCatalog.buildAlbumsTab(albums, emptyList(), emptySet(), defaultSort)
        val folders = tab.filter { it.kind == AlbumKind.DEVICE_FOLDER }
        // "camera" is a priority folder despite the newer "Zoo" and the default newest-first sort.
        assertThat(folders.map { it.bucketId }).containsExactly("cam", "z").inOrder()
    }

    // --- Pin ordering (item 6) --------------------------------------------------------------------

    @Test
    fun `pinned albums sort above the priority folders and Recent`() {
        val albums = listOf(
            folder("camera", "Camera", count = 5, newest = 50, cover = "c"),
            folder("holiday", "Holiday", count = 3, newest = 10, cover = "c"),
        )
        val tab = AlbumsCatalog.buildAlbumsTab(albums, emptyList(), pinnedBucketIds = setOf("holiday"), sort = defaultSort)

        assertThat(tab.first().bucketId).isEqualTo("holiday")
        assertThat(tab.first().pinned).isTrue()
        // Recent + Camera still follow, in their usual order.
        assertThat(tab.map { it.kindOrBucket() }).containsExactly("holiday", "RECENT", "camera").inOrder()
    }

    @Test
    fun `multiple pins order among themselves by the active sort`() {
        val albums = listOf(
            folder("a", "Aaa", count = 1, newest = 100, cover = "c"),
            folder("b", "Bbb", count = 1, newest = 200, cover = "c"),
            folder("c", "Ccc", count = 1, newest = 300, cover = "c"),
        )
        val nameAsc = SortSpec(SortKey.FILE_NAME, SortDirection.ASCENDING)
        val tab = AlbumsCatalog.buildAlbumsTab(albums, emptyList(), pinnedBucketIds = setOf("b", "c"), sort = nameAsc)

        val pinned = tab.filter { it.pinned }
        assertThat(pinned.map { it.name }).containsExactly("Bbb", "Ccc").inOrder() // name-ascending among pins
    }

    // --- Determinism ------------------------------------------------------------------------------

    @Test
    fun `assembly is deterministic for the same input regardless of source order`() {
        val a = listOf(
            folder("camera", "Camera", 5, 50, "c"),
            folder("screenshots", "Screenshots", 2, 100, "c"),
            folder("m", "Misc", 1, 999, "c"),
        )
        val shuffled = a.reversed()
        val videos = listOf(video("v1", "camera", "Camera", 7))

        val one = AlbumsCatalog.buildAlbumsTab(a, videos, setOf("m"), defaultSort).map { it.kindOrBucket() }
        val two = AlbumsCatalog.buildAlbumsTab(shuffled, videos, setOf("m"), defaultSort).map { it.kindOrBucket() }
        assertThat(one).isEqualTo(two)
    }

    // --- Format filter (design C1-06, item 3) -----------------------------------------------------

    @Test
    fun `bucketFormats reports the formats each folder actually holds`() {
        val media = listOf(
            image("p1", bucket = "camera", mime = "image/jpeg"),
            video("v1", bucket = "camera", name = "Camera", taken = 1),
            image("g1", bucket = "downloads", mime = "image/gif"),
        )
        val formats = AlbumsCatalog.bucketFormats(media)
        assertThat(formats["camera"]).containsExactly(MediaFilter.PHOTOS, MediaFilter.VIDEOS)
        assertThat(formats["downloads"]).containsExactly(MediaFilter.GIFS)
    }

    @Test
    fun `ALL filter is the identity`() {
        val tab = AlbumsCatalog.buildAlbumsTab(
            listOf(folder("camera", "Camera", 2, 10, "c")),
            listOf(video("v1", "camera", "Camera", 5)),
            emptySet(),
            defaultSort,
        )
        assertThat(AlbumsCatalog.applyFormatFilter(tab, MediaFilter.ALL, emptyMap())).isEqualTo(tab)
    }

    @Test
    fun `Videos filter keeps video folders, the Video album and Recent, drops photo-only folders`() {
        val albums = listOf(
            folder("camera", "Camera", 2, 50, "c"),   // has a video below
            folder("shots", "Screenshots", 3, 40, "c"), // photos only
        )
        val videos = listOf(video("v1", "camera", "Camera", 7))
        val tab = AlbumsCatalog.buildAlbumsTab(albums, videos, emptySet(), defaultSort)
        val bucketFormats = mapOf(
            "camera" to setOf(MediaFilter.PHOTOS, MediaFilter.VIDEOS),
            "shots" to setOf(MediaFilter.PHOTOS),
        )

        val filtered = AlbumsCatalog.applyFormatFilter(tab, MediaFilter.VIDEOS, bucketFormats)

        assertThat(filtered.map { it.kindOrBucket() })
            .containsExactly("RECENT", "camera", "VIDEO").inOrder()
        assertThat(filtered.none { it.kindOrBucket() == "shots" }).isTrue()
    }

    @Test
    fun `GIFs filter keeps only GIF-bearing folders and drops the Video album`() {
        val albums = listOf(
            folder("downloads", "Downloads", 4, 30, "c"),
            folder("camera", "Camera", 2, 50, "c"),
        )
        val videos = listOf(video("v1", "camera", "Camera", 7))
        val tab = AlbumsCatalog.buildAlbumsTab(albums, videos, emptySet(), defaultSort)
        val bucketFormats = mapOf(
            "downloads" to setOf(MediaFilter.GIFS),
            "camera" to setOf(MediaFilter.PHOTOS, MediaFilter.VIDEOS),
        )

        val filtered = AlbumsCatalog.applyFormatFilter(tab, MediaFilter.GIFS, bucketFormats)

        // Recent stays (library has a GIF somewhere); the Video smart album and camera drop.
        assertThat(filtered.map { it.kindOrBucket() }).containsExactly("RECENT", "downloads").inOrder()
    }

    private fun image(id: String, bucket: String, mime: String) = MediaItem(
        id = MediaId(id),
        displayName = "$id.${mime.substringAfterLast('/')}",
        type = MediaType.IMAGE,
        bucketId = bucket,
        bucketName = bucket,
        dateTakenMillis = 0,
        dateModifiedMillis = 0,
        sizeBytes = 0,
        width = 100,
        height = 100,
        durationMillis = 0,
        mimeType = mime,
    )

    private fun Album.kindOrBucket(): String = when (kind) {
        AlbumKind.RECENT -> "RECENT"
        AlbumKind.VIDEO -> "VIDEO"
        AlbumKind.DEVICE_FOLDER -> bucketId
    }

    private fun folder(bucketId: String, name: String, count: Int, newest: Long, cover: String) = Album(
        bucketId = bucketId,
        name = name,
        itemCount = count,
        cover = MediaId(cover),
        newestItemMillis = newest,
    )

    private fun video(id: String, bucket: String, name: String, taken: Long) = MediaItem(
        id = MediaId(id),
        displayName = id,
        type = MediaType.VIDEO,
        bucketId = bucket,
        bucketName = name,
        dateTakenMillis = taken,
        dateModifiedMillis = taken,
        sizeBytes = 0,
        width = 0,
        height = 0,
        durationMillis = 1000,
        mimeType = "video/mp4",
    )
}
