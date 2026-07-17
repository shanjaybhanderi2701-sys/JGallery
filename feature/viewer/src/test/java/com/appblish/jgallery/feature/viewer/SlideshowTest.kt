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
}
