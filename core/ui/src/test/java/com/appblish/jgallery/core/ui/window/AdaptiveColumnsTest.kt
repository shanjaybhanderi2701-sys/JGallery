package com.appblish.jgallery.core.ui.window

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.appblish.jgallery.core.model.ColumnCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-function coverage for the APP-653 width-derived column bonus (APP-645 §5, approach B). The
 * Composable convenience is exercised on-device; the tier math lives here so it is verifiable in JVM.
 */
class AdaptiveColumnsTest {

    private val Compact = WindowWidthSizeClass.Compact
    private val Medium = WindowWidthSizeClass.Medium
    private val Expanded = WindowWidthSizeClass.Expanded

    @Test
    fun `Compact leaves media columns at the pref so phones are unchanged`() {
        for (pref in ColumnCount.MIN..ColumnCount.PREF_MAX) {
            assertThat(Compact.adaptiveColumns(ColumnCount(pref), GridContent.MEDIA))
                .isEqualTo(ColumnCount(pref))
        }
    }

    @Test
    fun `Compact leaves album tiles at the pref so phones are unchanged`() {
        for (pref in ColumnCount.MIN..ColumnCount.PREF_MAX) {
            assertThat(Compact.adaptiveColumns(ColumnCount(pref), GridContent.ALBUM_TILES))
                .isEqualTo(ColumnCount(pref))
        }
    }

    @Test
    fun `media grid adds one column on Medium and two on Expanded`() {
        assertThat(Medium.adaptiveColumns(ColumnCount(3), GridContent.MEDIA)).isEqualTo(ColumnCount(4))
        assertThat(Expanded.adaptiveColumns(ColumnCount(3), GridContent.MEDIA)).isEqualTo(ColumnCount(5))
        // A user who pinched the pref up still scales relative to it.
        assertThat(Medium.adaptiveColumns(ColumnCount(6), GridContent.MEDIA)).isEqualTo(ColumnCount(7))
        assertThat(Expanded.adaptiveColumns(ColumnCount(6), GridContent.MEDIA)).isEqualTo(ColumnCount(8))
    }

    @Test
    fun `album tiles scale one step slower - unchanged on Medium, plus one on Expanded`() {
        assertThat(Medium.adaptiveColumns(ColumnCount(3), GridContent.ALBUM_TILES)).isEqualTo(ColumnCount(3))
        assertThat(Expanded.adaptiveColumns(ColumnCount(3), GridContent.ALBUM_TILES)).isEqualTo(ColumnCount(4))
    }

    @Test
    fun `the rendered count never exceeds the raised MAX`() {
        // A hypothetical high base plus the Expanded bonus is clamped, never throwing.
        assertThat(Expanded.adaptiveColumns(ColumnCount(ColumnCount.MAX), GridContent.MEDIA))
            .isEqualTo(ColumnCount(ColumnCount.MAX))
    }
}
