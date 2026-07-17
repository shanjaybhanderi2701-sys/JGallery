package com.appblish.jgallery.core.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The UX redline-3 density rule (APP-543): the hollow "unfavorited" heart is discovery chrome and is
 * suppressed on dense grids, but a favorited item's filled heart always shows because it carries state.
 */
class FavoriteHeartBadgeTest {

    @Test
    fun `favorited heart always shows regardless of grid density`() {
        for (columns in 1..12) {
            assertThat(favoriteHeartVisible(favorite = true, columns = columns)).isTrue()
        }
    }

    @Test
    fun `unfavorited heart shows on sparse grids and hides once dense`() {
        // Default threshold is 6 columns (mirrors VideoOverlay dropping its duration pill).
        assertThat(favoriteHeartVisible(favorite = false, columns = 2)).isTrue()
        assertThat(favoriteHeartVisible(favorite = false, columns = 5)).isTrue()
        assertThat(favoriteHeartVisible(favorite = false, columns = 6)).isFalse()
        assertThat(favoriteHeartVisible(favorite = false, columns = 9)).isFalse()
    }

    @Test
    fun `threshold is configurable`() {
        assertThat(favoriteHeartVisible(favorite = false, columns = 4, hideUnfavoritedAtColumns = 4)).isFalse()
        assertThat(favoriteHeartVisible(favorite = false, columns = 3, hideUnfavoritedAtColumns = 4)).isTrue()
    }
}
