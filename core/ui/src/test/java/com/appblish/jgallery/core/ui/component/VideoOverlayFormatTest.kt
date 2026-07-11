package com.appblish.jgallery.core.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The C1-08 duration-pill readout: m:ss, zero-padded, with an hours field only when needed. */
class VideoOverlayFormatTest {

    @Test
    fun `formats sub-hour durations as minutes and zero-padded seconds`() {
        assertThat(formatVideoDuration(0)).isEqualTo("0:00")
        assertThat(formatVideoDuration(8_000)).isEqualTo("0:08")
        assertThat(formatVideoDuration(65_000)).isEqualTo("1:05")
        assertThat(formatVideoDuration(727_000)).isEqualTo("12:07")
    }

    @Test
    fun `adds an hours field past an hour and clamps negatives`() {
        assertThat(formatVideoDuration(5_025_000)).isEqualTo("1:23:45")
        assertThat(formatVideoDuration(-10)).isEqualTo("0:00")
    }
}
