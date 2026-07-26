package com.appblish.jgallery.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ColumnCountTest {

    @Test
    fun `clamp keeps the user preference within 2 to PREF_MAX`() {
        // clamp() governs the persisted phone-portrait pref, capped at PREF_MAX (6) even though the
        // value class now accepts up to MAX (10) for the render-time adaptive bonus (APP-653).
        assertThat(ColumnCount.clamp(0).value).isEqualTo(2)
        assertThat(ColumnCount.clamp(9).value).isEqualTo(ColumnCount.PREF_MAX)
        assertThat(ColumnCount.clamp(9).value).isEqualTo(6)
        assertThat(ColumnCount.clamp(4).value).isEqualTo(4)
    }

    @Test
    fun `constructor accepts the raised adaptive range up to MAX`() {
        // The adaptive size-class bonus builds counts above PREF_MAX, so 7..MAX must be constructible.
        assertThat(ColumnCount(ColumnCount.PREF_MAX + 1).value).isEqualTo(7)
        assertThat(ColumnCount(ColumnCount.MAX).value).isEqualTo(10)
    }

    @Test
    fun `constructor rejects out-of-range column counts`() {
        assertThrows(IllegalArgumentException::class.java) { ColumnCount(1) }
        assertThrows(IllegalArgumentException::class.java) { ColumnCount(ColumnCount.MAX + 1) }
    }

    @Test
    fun `default is three columns`() {
        assertThat(ColumnCount.DEFAULT.value).isEqualTo(3)
    }
}
