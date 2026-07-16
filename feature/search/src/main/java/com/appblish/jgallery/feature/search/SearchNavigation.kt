package com.appblish.jgallery.feature.search

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.appblish.jgallery.core.model.MediaItem

/**
 * Route for Search. Not a top-tab since C1-01 item 10 — reached as a **header action** on both the
 * Photos and Collections tabs, and opened full-screen (the bottom tab bar hides, like the viewer).
 */
const val SEARCH_ROUTE = "search"

/** Open Search full-screen. Entry points: the Photos/Collections header search action. */
fun NavController.navigateToSearch() {
    navigate(SEARCH_ROUTE)
}

/**
 * Register the full-screen Search destination in the app nav graph. [onMediaClick] opens a tapped
 * result in the existing viewer (spec §4.5 / AC-10); [onBack] returns to the originating surface.
 */
fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    composable(SEARCH_ROUTE) {
        SearchScreen(onBack = onBack, onMediaClick = onMediaClick)
    }
}
