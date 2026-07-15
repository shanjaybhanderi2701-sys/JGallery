package com.appblish.jgallery.core.ui.selection

import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure aggregate-summary + formatting logic behind multi-select Details (design G1-D7 item 11). */
class SelectionDetailsTest {

    private fun item(
        id: String,
        type: MediaType = MediaType.IMAGE,
        size: Long = 0L,
        dateTaken: Long = 0L,
    ) = MediaItem(
        id = MediaId(id),
        displayName = id,
        type = type,
        bucketId = "b",
        bucketName = "B",
        dateTakenMillis = dateTaken,
        dateModifiedMillis = dateTaken,
        sizeBytes = size,
        width = 0,
        height = 0,
        durationMillis = 0,
        mimeType = "image/jpeg",
    )

    @Test
    fun `mixed selection splits photo and video counts and sums size`() {
        val details = mediaSelectionDetails(
            listOf(
                item("a", MediaType.IMAGE, size = 1_000),
                item("b", MediaType.IMAGE, size = 2_000),
                item("c", MediaType.VIDEO, size = 3_000),
            ),
        )
        assertThat(details.title).isEqualTo("3 items")
        val rows = details.rows.toMap()
        assertThat(rows["Items"]).isEqualTo("3")
        assertThat(rows["Photos"]).isEqualTo("2")
        assertThat(rows["Videos"]).isEqualTo("1")
        // 6,000 bytes rounds to 5.9 KB (6000 / 1024).
        assertThat(rows["Size"]).isEqualTo("5.9 KB")
    }

    @Test
    fun `all-photo selection uses photo noun and omits the split rows`() {
        val single = mediaSelectionDetails(listOf(item("a", MediaType.IMAGE)))
        assertThat(single.title).isEqualTo("1 photo")
        val plural = mediaSelectionDetails(listOf(item("a"), item("b")))
        assertThat(plural.title).isEqualTo("2 photos")
        assertThat(plural.rows.toMap().keys).doesNotContain("Photos")
    }

    @Test
    fun `all-video selection uses video noun`() {
        val details = mediaSelectionDetails(listOf(item("a", MediaType.VIDEO)))
        assertThat(details.title).isEqualTo("1 video")
    }

    @Test
    fun `byte size formats binary units and marks unknown`() {
        assertThat(formatByteSize(0)).isEqualTo("—")
        assertThat(formatByteSize(512)).isEqualTo("512 B")
        assertThat(formatByteSize(1024)).isEqualTo("1.0 KB")
        assertThat(formatByteSize(1_572_864)).isEqualTo("1.5 MB")
    }

    @Test
    fun `date range collapses to a single date when oldest equals newest`() {
        assertThat(formatDateRange(0, 0)).isEqualTo("—")
        val one = formatDateRange(1_600_000_000_000, 1_600_000_000_000)
        assertThat(one).doesNotContain("–")
        val range = formatDateRange(1_600_000_000_000, 1_700_000_000_000)
        assertThat(range).contains("–")
    }
}
