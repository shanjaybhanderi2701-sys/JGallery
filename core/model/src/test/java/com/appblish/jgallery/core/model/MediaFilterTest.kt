package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The C1-06 format filter: exact partitioning of video / GIF / still-photo, plus the list helpers. */
class MediaFilterTest {

    private fun item(
        id: String = "1",
        name: String = "photo.jpg",
        mime: String = "image/jpeg",
        type: MediaType = MediaType.IMAGE,
        bucket: String = "b",
    ) = MediaItem(
        id = MediaId(id),
        displayName = name,
        type = type,
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

    private val photo = item(id = "p", name = "a.jpg", mime = "image/jpeg")
    private val png = item(id = "n", name = "b.png", mime = "image/png")
    private val gif = item(id = "g", name = "c.gif", mime = "image/gif")
    private val video = item(id = "v", name = "d.mp4", mime = "video/mp4", type = MediaType.VIDEO)

    @Test
    fun `ALL admits everything`() {
        listOf(photo, png, gif, video).forEach {
            assertThat(MediaFilter.ALL.matches(it)).isTrue()
        }
    }

    @Test
    fun `the three format chips partition the library exactly`() {
        // Every item matches exactly one of PHOTOS / VIDEOS / GIFS.
        listOf(photo, png, gif, video).forEach { m ->
            val matches = listOf(MediaFilter.PHOTOS, MediaFilter.VIDEOS, MediaFilter.GIFS)
                .count { it.matches(m) }
            assertThat(matches).isEqualTo(1)
        }
    }

    @Test
    fun `each chip matches its own kind`() {
        assertThat(MediaFilter.PHOTOS.matches(photo)).isTrue()
        assertThat(MediaFilter.PHOTOS.matches(png)).isTrue()
        assertThat(MediaFilter.PHOTOS.matches(gif)).isFalse()   // a GIF is a GIF, not a photo
        assertThat(MediaFilter.PHOTOS.matches(video)).isFalse()

        assertThat(MediaFilter.VIDEOS.matches(video)).isTrue()
        assertThat(MediaFilter.VIDEOS.matches(photo)).isFalse()

        assertThat(MediaFilter.GIFS.matches(gif)).isTrue()
        assertThat(MediaFilter.GIFS.matches(photo)).isFalse()
        assertThat(MediaFilter.GIFS.matches(video)).isFalse()
    }

    @Test
    fun `filteredBy is identity for ALL and narrows otherwise`() {
        val all = listOf(photo, png, gif, video)
        assertThat(all.filteredBy(MediaFilter.ALL)).isEqualTo(all)
        assertThat(all.filteredBy(MediaFilter.VIDEOS)).containsExactly(video)
        assertThat(all.filteredBy(MediaFilter.GIFS)).containsExactly(gif)
        assertThat(all.filteredBy(MediaFilter.PHOTOS)).containsExactly(photo, png).inOrder()
    }

    @Test
    fun `formatsPresentIn reports only the formats actually present`() {
        assertThat(formatsPresentIn(listOf(photo, png)))
            .containsExactly(MediaFilter.PHOTOS)
        assertThat(formatsPresentIn(listOf(photo, gif, video)))
            .containsExactly(MediaFilter.PHOTOS, MediaFilter.VIDEOS, MediaFilter.GIFS)
        assertThat(formatsPresentIn(emptyList())).isEmpty()
    }

    @Test
    fun `chip order is All Photos Videos GIFs with ALL first`() {
        assertThat(MediaFilter.ORDER)
            .containsExactly(MediaFilter.ALL, MediaFilter.PHOTOS, MediaFilter.VIDEOS, MediaFilter.GIFS)
            .inOrder()
    }
}
