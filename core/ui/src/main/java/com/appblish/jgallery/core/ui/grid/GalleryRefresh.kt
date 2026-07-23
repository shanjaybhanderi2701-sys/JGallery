package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared pull-to-refresh wrapper (design G1-D7 item 13). One home in `:core:ui` so every grid/list —
 * Photos, Albums, album detail, trash — gets identical behavior and chrome: pulling down past the
 * threshold fires [onRefresh] (a media re-scan) and shows an accent spinner, centered, tucked just
 * under the status bar (`statusBars` + 8dp) so it clears the edge-to-edge system bar. No custom chrome.
 *
 * Wrap the scrollable directly; the child grid's nested-scroll is what drives the pull. [content] runs
 * inside a [PullToRefreshBox] so the indicator draws above the grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Kept internal so no experimental Material type leaks into this wrapper's public signature.
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Grids draw under the status bar (edge-to-edge, design §Inset); nudge the spinner
                    // below it so it never hides behind the clock/icons.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
            )
        },
    ) {
        content()
    }
}
