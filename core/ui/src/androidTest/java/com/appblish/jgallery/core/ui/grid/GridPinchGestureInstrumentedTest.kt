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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
