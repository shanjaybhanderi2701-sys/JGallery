package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.saveable.SaverScope
import com.appblish.jgallery.core.model.ColumnCount
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The shared zoomable-grid state (APP-403): the column count a pinch drives, and the fact that it
 * rides on ONE [LazyGridState] instance (that shared instance is what preserves the scroll anchor
 * across a column change). The pinch→column arithmetic itself lives in [GridZoomTest].
 */
class GridZoomStateTest {

    @Test
    fun `setColumns updates the live count`() {
        val state = GridZoomState(ColumnCount(3), LazyGridState())
        assertThat(state.columns).isEqualTo(ColumnCount(3))

        state.updateColumns(ColumnCount(5))
        assertThat(state.columns).isEqualTo(ColumnCount(5))
    }

    @Test
    fun `the grid state instance is stable across column changes — the anchor-preservation contract`() {
        val gridState = LazyGridState(firstVisibleItemIndex = 42)
        val state = GridZoomState(ColumnCount(3), gridState)

        state.updateColumns(ColumnCount(2))
        state.updateColumns(ColumnCount(6))

        // Same LazyGridState throughout → LazyGrid keeps firstVisibleItemIndex; the top item stays put.
        assertThat(state.gridState).isSameInstanceAs(gridState)
        assertThat(state.gridState.firstVisibleItemIndex).isEqualTo(42)
    }

    @Test
    fun `saver round-trips the column count`() {
        val original = GridZoomState(ColumnCount(4), LazyGridState())
        val saver = GridZoomState.Saver(LazyGridState())

        val saved = with(saver) { SaverScope { true }.save(original) }
        val restored = saver.restore(saved!!)

        assertThat(restored!!.columns).isEqualTo(ColumnCount(4))
    }
}
