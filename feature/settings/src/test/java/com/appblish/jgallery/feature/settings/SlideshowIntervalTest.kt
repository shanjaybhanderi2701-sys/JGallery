package com.appblish.jgallery.feature.settings

import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Interval-picker mapping coverage (APP-642): the Settings §6 slideshow dialog exposes the short
 * options 0.5/1/2/3/4/5s alongside the original longer 6/10s dwell times, and each option maps to a
 * label that survives the sub-second case without integer-seconds truncation.
 */
class SlideshowIntervalTest {

    @Test
    fun `picker exposes the short options and keeps the longer ones`() {
        assertThat(SLIDESHOW_INTERVALS_MS)
            .containsExactly(500L, 1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 10_000L)
            .inOrder()
    }

    @Test
    fun `half-second option keeps its decimal instead of truncating to zero`() {
        // The bug guard: integer division would render 500ms as "0s". It must read as "0.5s".
        assertThat(slideshowLabel(500L)).isEqualTo("0.5s")
    }

    @Test
    fun `whole-second options render without a trailing decimal`() {
        assertThat(slideshowLabel(1_000L)).isEqualTo("1s")
        assertThat(slideshowLabel(2_000L)).isEqualTo("2s")
        assertThat(slideshowLabel(3_000L)).isEqualTo("3s")
        assertThat(slideshowLabel(4_000L)).isEqualTo("4s")
        assertThat(slideshowLabel(5_000L)).isEqualTo("5s")
        assertThat(slideshowLabel(6_000L)).isEqualTo("6s")
        assertThat(slideshowLabel(10_000L)).isEqualTo("10s")
    }

    @Test
    fun `every offered option survives the ViewDefaults clamp unchanged`() {
        // Nothing the picker can offer may be silently clamped away by the seam's floor/ceiling —
        // in particular the 0.5s option must sit at or above MIN (APP-642 lowered it to 500ms).
        SLIDESHOW_INTERVALS_MS.forEach { ms ->
            val clamped = ms.coerceIn(
                ViewDefaults.MIN_SLIDESHOW_INTERVAL_MS,
                ViewDefaults.MAX_SLIDESHOW_INTERVAL_MS,
            )
            assertThat(clamped).isEqualTo(ms)
        }
    }
}
