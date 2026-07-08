package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ColumnCountTest {

    @Test
    fun `clamp keeps values within 2 to 6`() {
        assertThat(ColumnCount.clamp(0).value).isEqualTo(2)
        assertThat(ColumnCount.clamp(9).value).isEqualTo(6)
        assertThat(ColumnCount.clamp(4).value).isEqualTo(4)
    }

    @Test
    fun `constructor rejects out-of-range column counts`() {
        assertThrows(IllegalArgumentException::class.java) { ColumnCount(1) }
        assertThrows(IllegalArgumentException::class.java) { ColumnCount(7) }
    }

    @Test
    fun `default is three columns`() {
        assertThat(ColumnCount.DEFAULT.value).isEqualTo(3)
    }
}
