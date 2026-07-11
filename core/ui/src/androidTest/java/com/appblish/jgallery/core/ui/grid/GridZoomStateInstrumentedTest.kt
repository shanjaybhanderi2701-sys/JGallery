package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented proof of the universal pinch-to-zoom primitive (APP-403): a grid built on
 * [rememberGridZoomState] + [gridPinchColumns] re-lays-out when the column count changes and KEEPS
 * the scroll anchor — the item you were looking at stays on screen, it does not snap to the top.
 * The column count is driven programmatically here (the pinch→count arithmetic is unit-tested in
 * [GridZoomStateTest]/[GridZoomTest]); on-device the same [GridZoomState] is driven by a real pinch.
 */
@RunWith(AndroidJUnit4::class)
class GridZoomStateInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var zoom: GridZoomState

    @Composable
    private fun ZoomGrid() {
        zoom = rememberGridZoomState(initialColumns = ColumnCount(3))
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns.value),
            state = zoom.gridState,
            modifier = Modifier.fillMaxSize().gridPinchColumns(zoom).testTag("zoom_grid"),
        ) {
            items(count = 300, span = { GridItemSpan(1) }) { i ->
                Box(Modifier.aspectRatio(1f).testTag("tile_$i")) { Text("$i") }
            }
        }
    }

    @Test
    fun columnChange_keepsTheAnchorItemOnScreen_ratherThanJumpingToTop() {
        composeRule.setContent { JGalleryTheme { ZoomGrid() } }

        // Scroll deep so tile 0 is nowhere near the viewport, and tile 150 is our anchor.
        composeRule.onNodeWithTag("zoom_grid").performScrollToIndex(150)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tile_150").assertIsDisplayed()

        // Pinch out to fewer columns, then in to more — every step goes through the shared state.
        composeRule.runOnUiThread { zoom.updateColumns(ColumnCount(2)) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { zoom.updateColumns(ColumnCount(6)) }
        composeRule.waitForIdle()

        // New column count took effect AND the anchor held — a reset-to-top bug would show tile 0.
        assert(zoom.columns == ColumnCount(6))
        composeRule.onNodeWithTag("tile_150").assertIsDisplayed()
    }
}
