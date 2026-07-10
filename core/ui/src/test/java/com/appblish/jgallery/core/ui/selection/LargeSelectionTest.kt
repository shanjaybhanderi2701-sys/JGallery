package com.appblish.jgallery.core.ui.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The at-scale Select-All guardrail threshold (design W3-09). */
class LargeSelectionTest {

    @Test
    fun `selection is large only at or above the threshold`() {
        assertThat(isLargeSelection(LARGE_SELECTION_THRESHOLD)).isTrue()
        assertThat(isLargeSelection(LARGE_SELECTION_THRESHOLD + 1)).isTrue()
        assertThat(isLargeSelection(LARGE_SELECTION_THRESHOLD - 1)).isFalse()
        assertThat(isLargeSelection(0)).isFalse()
        // A 61,908-item Select-All (the design's worked example) always warns.
        assertThat(isLargeSelection(61_908)).isTrue()
    }
}
