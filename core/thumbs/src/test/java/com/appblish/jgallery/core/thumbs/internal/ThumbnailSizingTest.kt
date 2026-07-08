package com.appblish.jgallery.core.thumbs.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThumbnailSizingTest {

    @Test
    fun `rounds up to the covering bucket so tiles are never upscaled`() {
        assertThat(selectThumbnailBucket(100)).isEqualTo(128)
        assertThat(selectThumbnailBucket(129)).isEqualTo(192)
        assertThat(selectThumbnailBucket(300)).isEqualTo(384)
    }

    @Test
    fun `exact bucket sizes map to themselves`() {
        THUMBNAIL_EDGE_BUCKETS.forEach { edge ->
            assertThat(selectThumbnailBucket(edge)).isEqualTo(edge)
        }
    }

    @Test
    fun `oversized requests are capped — grid tiles never approach full size`() {
        assertThat(selectThumbnailBucket(2000)).isEqualTo(MAX_THUMBNAIL_EDGE_PX)
        assertThat(selectThumbnailBucket(Int.MAX_VALUE)).isEqualTo(MAX_THUMBNAIL_EDGE_PX)
    }

    @Test
    fun `unmeasured (non-positive) requests fall to the smallest bucket`() {
        assertThat(selectThumbnailBucket(0)).isEqualTo(THUMBNAIL_EDGE_BUCKETS.first())
        assertThat(selectThumbnailBucket(-1)).isEqualTo(THUMBNAIL_EDGE_BUCKETS.first())
    }
}
