package com.appblish.jgallery.feature.settings

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Full-screen Settings route (design SET-01). Reached from the shared overflow menu on both tabs. */
const val SETTINGS_ROUTE = "settings"
const val ABOUT_ROUTE = "settings/about"
const val LICENSES_ROUTE = "settings/licenses"

/** Open the Settings screen. Entry point: the `GalleryOverflowMenu` footer on Photos + Albums. */
fun NavController.navigateToSettings() {
    navigate(SETTINGS_ROUTE)
}

/**
 * Register the Settings destinations (root + About + Licenses) in the app nav graph. The tab bar hides
 * for [SETTINGS_ROUTE]; About/Licenses push on top and pop back with the system/back-arrow.
 */
fun NavGraphBuilder.settingsScreen(navController: NavController, onBack: () -> Unit) {
    composable(SETTINGS_ROUTE) {
        SettingsScreen(
            onBack = onBack,
            onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
        )
    }
    composable(ABOUT_ROUTE) {
        AboutScreen(
            onBack = { navController.popBackStack() },
            onOpenLicenses = { navController.navigate(LICENSES_ROUTE) },
        )
    }
    composable(LICENSES_ROUTE) {
        LicensesScreen(onBack = { navController.popBackStack() })
    }
}
