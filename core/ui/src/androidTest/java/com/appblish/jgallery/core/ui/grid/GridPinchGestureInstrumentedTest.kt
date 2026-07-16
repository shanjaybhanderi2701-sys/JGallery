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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * QA evidence for APP-410 (DoD gate of APP-403): unlike [GridZoomStateInstrumentedTest] — which
 * drives the column count programmatically — this test injects a REAL two-finger pinch gesture
 * through [Modifier.gridPinchColumns] on a live Android runtime, so the actual gesture plumbing
 * (`awaitEachGesture` + `calculateZoom` + [columnsForPinch]) is exercised end-to-end, not just the
 * arithmetic. This is the closest automatable proxy for the manual physical-device pinch.
 *
 * Spread (fingers apart) → tiles grow → FEWER columns; pinch in (fingers together) → MORE columns.
 */
@RunWith(AndroidJUnit4::class)
class GridPinchGestureInstrumentedTest {

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
    fun spreadGesture_reducesColumns_pinchInGesture_increasesColumns() {
        composeRule.setContent { JGalleryTheme { ZoomGrid() } }
        composeRule.waitForIdle()
        assertTrue("precondition: starts at 3 columns", zoom.columns == ColumnCount(3))

        // Spread: two pointers start close to center and move far apart → zoom > 1 → fewer columns.
        composeRule.onNodeWithTag("zoom_grid").performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f),
                end0 = center + Offset(-width * 0.42f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(width * 0.42f, 0f),
            )
        }
        composeRule.waitForIdle()
        val afterSpread = zoom.columns
        assertTrue(
            "spread pinch must REDUCE columns (was 3, now ${afterSpread.value})",
            afterSpread.value < 3,
        )

        // Pinch in: reverse — start far apart, move together → zoom < 1 → more columns.
        composeRule.onNodeWithTag("zoom_grid").performTouchInput {
            pinch(
                start0 = center + Offset(-width * 0.42f, 0f),
                end0 = center + Offset(-40f, 0f),
                start1 = center + Offset(width * 0.42f, 0f),
                end1 = center + Offset(40f, 0f),
            )
        }
        composeRule.waitForIdle()
        val afterPinchIn = zoom.columns
        assertTrue(
            "pinch-in must INCREASE columns above the spread count (${afterSpread.value} -> ${afterPinchIn.value})",
            afterPinchIn.value > afterSpread.value,
        )
    }

    /**
     * APP-521 regression on the REAL two-finger handler (the Column-count control never touches the
     * `pointerInput` path, so it structurally could not catch these). Two things the board saw on
     * device and this asserts:
     *
     * - **No residual release scale / wrong-size frame (fixes 2 & 3):** after a spread pinch settles,
     *   a tile's on-screen width must equal the fresh grid's rest width (rootWidth / newColumns). If
     *   the `graphicsLayer` scale were left anywhere but 1f — a stuck per-event `snapTo`, or the size
     *   bridge never resolving to 1f — the tile would be visibly mis-sized here.
     * - **Travelling-centroid pinch does not corrupt the settle (fix 1):** a pinch whose midpoint
     *   slides sideways (the case that made the grid "swim") must still land on the right column count
     *   and rest at the right tile size — the origin is anchored once, so lateral travel only pans the
     *   pivot, it does not shake or mis-settle the grid.
     */
    @Test
    fun travellingPinch_settlesToExactRestTileSize_withNoResidualScale() {
        composeRule.setContent { JGalleryTheme { ZoomGrid() } }
        composeRule.waitForIdle()
        assertTrue("precondition: starts at 3 columns", zoom.columns == ColumnCount(3))

        val rootWidthDp = composeRule.onNodeWithTag("zoom_grid").getUnclippedBoundsInRoot().width.value

        // Spread WHILE dragging the whole gesture ~15% to the right, so the centroid travels mid-pinch
        // (the "swim" trigger). With the origin recomputed every event this shook the grid; anchored
        // once it does not. Spread magnitude still lands on fewer columns.
        val pan = rootWidthDp * 0.15f
        composeRule.onNodeWithTag("zoom_grid").performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f),
                end0 = center + Offset(-width * 0.42f + pan, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(width * 0.42f + pan, 0f),
            )
        }
        composeRule.waitForIdle()

        val settledColumns = zoom.columns
        assertTrue(
            "travelling spread must still settle to fewer columns (was 3, now ${settledColumns.value})",
            settledColumns.value < 3,
        )

        // At rest the graphicsLayer scale must be back at 1f: a tile spans exactly rootWidth/columns.
        val restTileWidth = composeRule.onNodeWithTag("tile_0").getUnclippedBoundsInRoot().width.value
        val expectedRestWidth = rootWidthDp / settledColumns.value
        val tolerance = expectedRestWidth * 0.06f // a few px of rounding/gutter slack
        assertTrue(
            "at rest a tile must be ~rootWidth/columns (=$expectedRestWidth dp) — a residual scale " +
                "or wrong-size settle frame would leave it at $restTileWidth dp",
            abs(restTileWidth - expectedRestWidth) <= tolerance,
        )
    }
}
