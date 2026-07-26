package com.appblish.jgallery.core.ui.selection

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The multi-select favorite composition rule (APP-670, spec §5): the overflow shows "Add to Favorites"
 * only when some selected item is not yet favorited, "Remove from Favorites" only when some selected item
 * already is, and BOTH exactly when the selection is mixed. At N=1 it degenerates to a single Add-or-Remove.
 */
class FavoriteSelectionActionsTest {

    @Test
    fun `none favorited shows Add only`() {
        val actions = favoriteSelectionActions(selectedCount = 5, favoritedCount = 0)
        assertThat(actions.showAdd).isTrue()
        assertThat(actions.showRemove).isFalse()
    }

    @Test
    fun `all favorited shows Remove only`() {
        val actions = favoriteSelectionActions(selectedCount = 5, favoritedCount = 5)
        assertThat(actions.showAdd).isFalse()
        assertThat(actions.showRemove).isTrue()
    }

    @Test
    fun `mixed selection shows both`() {
        val actions = favoriteSelectionActions(selectedCount = 5, favoritedCount = 2)
        assertThat(actions.showAdd).isTrue()
        assertThat(actions.showRemove).isTrue()
    }

    @Test
    fun `single unfavorited item shows Add only`() {
        val actions = favoriteSelectionActions(selectedCount = 1, favoritedCount = 0)
        assertThat(actions.showAdd).isTrue()
        assertThat(actions.showRemove).isFalse()
    }

    @Test
    fun `single favorited item shows Remove only`() {
        val actions = favoriteSelectionActions(selectedCount = 1, favoritedCount = 1)
        assertThat(actions.showAdd).isFalse()
        assertThat(actions.showRemove).isTrue()
    }

    @Test
    fun `empty selection shows neither`() {
        val actions = favoriteSelectionActions(selectedCount = 0, favoritedCount = 0)
        assertThat(actions.showAdd).isFalse()
        assertThat(actions.showRemove).isFalse()
    }
}
