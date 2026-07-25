package com.appblish.jgallery.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the slideshow-interval picker mapping (§6, APP-642): the option set exposes the shorter
 * sub-5s intervals, 0.5s is representable without integer-seconds truncation, and every option
 * yields a unique, human-readable label + testTag.
 */
class SlideshowIntervalTest {

    @Test
    fun `picker exposes 0_5 to 5s shorter intervals plus longer options in ascending order`() {
        assertThat(SLIDESHOW_INTERVALS_MS)
            .containsExactly(500L, 1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 10_000L)
            .inOrder()
    }

    @Test
    fun `half-second interval is representable and does not truncate to 0s`() {
        assertThat(SLIDESHOW_INTERVALS_MS).contains(500L)
        assertThat(slideshowLabel(500L)).isEqualTo("0.5s")
    }

    @Test
    fun `whole-second intervals render as integers without a trailing decimal`() {
        assertThat(slideshowLabel(1_000L)).isEqualTo("1s")
        assertThat(slideshowLabel(2_000L)).isEqualTo("2s")
        assertThat(slideshowLabel(5_000L)).isEqualTo("5s")
        assertThat(slideshowLabel(10_000L)).isEqualTo("10s")
    }

    @Test
    fun `every option label is unique so the radio list has no duplicate rows`() {
        val labels = SLIDESHOW_INTERVALS_MS.map { slideshowLabel(it) }
        assertThat(labels).containsNoDuplicates()
    }
}
