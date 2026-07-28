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

    // APP-702 finding #6 — the top rung must cover every *reachable* grid tile edge, or the source is
    // upscaled and the tile looks soft. A tile edge is `≈ (contentWidthPx − (cols−1)·4dp) / cols`,
    // largest at the 2-column floor. These are the previously-blurred large-screen configurations.
    @Test
    fun `large-screen tiles above 768px select a covering bucket, not the old cap`() {
        // Tablet / unfolded-foldable portrait, 2 cols (~1600–1840px content ÷ 2 ≈ 800–920px):
        assertThat(selectThumbnailBucket(800)).isEqualTo(1024)
        assertThat(selectThumbnailBucket(920)).isEqualTo(1024)
        // Tablet landscape, 3 cols (~2560px ÷ 3 ≈ 850px) still covered by the 1024 rung:
        assertThat(selectThumbnailBucket(850)).isEqualTo(1024)
        // Tablet landscape, 2 cols (~2560px ÷ 2 ≈ 1280px) — the extreme reachable corner:
        assertThat(selectThumbnailBucket(1280)).isEqualTo(1536)
        // None of these upscale: the chosen bucket is always ≥ the request.
        listOf(800, 920, 850, 1280).forEach { edge ->
            assertThat(selectThumbnailBucket(edge)).isAtLeast(edge)
        }
    }

    // Round-up means phones are unaffected by the new rungs — a 2-col phone tile (≤ ~720px) still
    // lands on 768, so the crispness fix adds zero decode cost off large screens.
    @Test
    fun `phone-sized tiles are unchanged by the added rungs`() {
        assertThat(selectThumbnailBucket(538)).isEqualTo(768) // 1080px ÷ 2 cols
        assertThat(selectThumbnailBucket(718)).isEqualTo(768) // 1440px ÷ 2 cols
        assertThat(selectThumbnailBucket(768)).isEqualTo(768)
    }

    @Test
    fun `unmeasured (non-positive) requests fall to the smallest bucket`() {
        assertThat(selectThumbnailBucket(0)).isEqualTo(THUMBNAIL_EDGE_BUCKETS.first())
        assertThat(selectThumbnailBucket(-1)).isEqualTo(THUMBNAIL_EDGE_BUCKETS.first())
    }
}
