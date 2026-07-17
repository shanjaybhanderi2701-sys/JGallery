package com.appblish.jgallery.feature.viewer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The slideshow advance rule (APP-544) as executable spec: where the next slide lands, how looping
 * wraps, and when auto-play stops. Pure JVM — the timer + scroll live in the composable driver, this
 * only owns the "next index or stop" decision.
 */
class SlideshowTest {

    @Test
    fun `advances to the next item mid-run`() {
        assertThat(Slideshow.nextPage(current = 0, count = 5, loop = true)).isEqualTo(1)
        assertThat(Slideshow.nextPage(current = 3, count = 5, loop = true)).isEqualTo(4)
    }

    @Test
    fun `wraps to the first item at the end when looping`() {
        assertThat(Slideshow.nextPage(current = 4, count = 5, loop = true)).isEqualTo(0)
    }

    @Test
    fun `stops at the end when not looping`() {
        assertThat(Slideshow.nextPage(current = 4, count = 5, loop = false)).isNull()
    }

    @Test
    fun `keeps advancing before the end regardless of loop`() {
        assertThat(Slideshow.nextPage(current = 2, count = 5, loop = false)).isEqualTo(3)
    }

    @Test
    fun `a single item has nowhere to advance`() {
        assertThat(Slideshow.nextPage(current = 0, count = 1, loop = true)).isNull()
        assertThat(Slideshow.nextPage(current = 0, count = 1, loop = false)).isNull()
    }

    @Test
    fun `an empty scope never advances`() {
        assertThat(Slideshow.nextPage(current = 0, count = 0, loop = true)).isNull()
    }

    @Test
    fun `default interval is a sane lean-back dwell`() {
        assertThat(Slideshow.DEFAULT_INTERVAL_MS).isEqualTo(3_000L)
    }

    // Video-dwell fallback (APP-548): auto-play must never stall forever on a video page. The driver
    // dwells for the clip's duration, but [videoDwellMs] clamps it into a bounded window so the run
    // always makes progress.

    @Test
    fun `a video plays through for roughly its own duration`() {
        // Between the floor and the cap, the dwell tracks the clip length so it isn't cut off.
        assertThat(Slideshow.videoDwellMs(10_000L)).isEqualTo(10_000L)
    }

    @Test
    fun `a zero or unknown duration video still advances at the default interval`() {
        assertThat(Slideshow.videoDwellMs(0L)).isEqualTo(Slideshow.DEFAULT_INTERVAL_MS)
        assertThat(Slideshow.videoDwellMs(-1L)).isEqualTo(Slideshow.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `a very short video is not left below the default interval`() {
        assertThat(Slideshow.videoDwellMs(500L)).isEqualTo(Slideshow.DEFAULT_INTERVAL_MS)
    }

    @Test
    fun `a long or looping video cannot pin the slideshow past the hard cap`() {
        assertThat(Slideshow.videoDwellMs(2 * 60 * 60 * 1_000L)).isEqualTo(Slideshow.MAX_VIDEO_DWELL_MS)
        assertThat(Slideshow.videoDwellMs(Long.MAX_VALUE)).isEqualTo(Slideshow.MAX_VIDEO_DWELL_MS)
    }

    @Test
    fun `the video dwell window is a sane bounded range`() {
        assertThat(Slideshow.MAX_VIDEO_DWELL_MS).isEqualTo(60_000L)
        assertThat(Slideshow.MAX_VIDEO_DWELL_MS).isGreaterThan(Slideshow.DEFAULT_INTERVAL_MS)
    }
}
