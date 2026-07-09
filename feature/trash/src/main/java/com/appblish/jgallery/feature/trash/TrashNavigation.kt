package com.appblish.jgallery.feature.trash

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Route for the Recycle Bin. Not a top-tab — reached from Collections → Utilities (design W2-09). */
const val TRASH_ROUTE = "trash"

/** Open the Recycle Bin. Entry points: Collections "Recover" utility (and later, Albums overflow). */
fun NavController.navigateToTrash() {
    navigate(TRASH_ROUTE)
}

/** Register the Recycle Bin destination in the app nav graph. */
fun NavGraphBuilder.trashScreen(onBack: () -> Unit) {
    composable(TRASH_ROUTE) {
        TrashScreen(onBack = onBack)
    }
}
