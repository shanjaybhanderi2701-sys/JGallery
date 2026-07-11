package com.appblish.jgallery.core.ui.grid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The C1-07 appear threshold: offer the FAB only past ~1.5 screens, never on an unknown viewport. */
class ScrollToTopMathTest {

    @Test
    fun `never offers on an unknown or zero viewport`() {
        assertThat(scrolledPastOffer(firstVisibleItemIndex = 999, itemsPerViewport = 0)).isFalse()
        assertThat(scrolledPastOffer(firstVisibleItemIndex = 999, itemsPerViewport = -3)).isFalse()
    }

    @Test
    fun `does not offer at or near the top`() {
        // 12 tiles per screen → threshold is 18 (1.5 screens); index 18 or less does not offer.
        assertThat(scrolledPastOffer(0, 12)).isFalse()
        assertThat(scrolledPastOffer(12, 12)).isFalse()
        assertThat(scrolledPastOffer(18, 12)).isFalse()
    }

    @Test
    fun `offers once scrolled past one and a half screens`() {
        assertThat(scrolledPastOffer(19, 12)).isTrue()
        assertThat(scrolledPastOffer(200, 12)).isTrue()
    }
}
