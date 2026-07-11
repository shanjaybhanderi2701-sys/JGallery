package com.appblish.jgallery.feature.search

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Route for Search. Not a top-tab since C1-01 item 10 — reached as a **header action** on both the
 * Photos and Collections tabs, and opened full-screen (the bottom tab bar hides, like the viewer).
 */
const val SEARCH_ROUTE = "search"

/** Open Search full-screen. Entry points: the Photos/Collections header search action. */
fun NavController.navigateToSearch() {
    navigate(SEARCH_ROUTE)
}

/** Register the full-screen Search destination in the app nav graph. */
fun NavGraphBuilder.searchScreen(onBack: () -> Unit) {
    composable(SEARCH_ROUTE) {
        SearchScreen(onBack = onBack)
    }
}
