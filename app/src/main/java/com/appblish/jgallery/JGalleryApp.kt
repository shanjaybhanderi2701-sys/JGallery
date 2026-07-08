package com.appblish.jgallery

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appblish.jgallery.core.ui.nav.GalleryTab
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens
import com.appblish.jgallery.feature.albums.AlbumsScreen
import com.appblish.jgallery.feature.collections.CollectionsScreen
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.search.SearchScreen

/**
 * The 4-tab navigation shell (spec §2): Albums | Photos | Collections | Search, Albums default.
 * Tab state is preserved on switch (`saveState`/`restoreState`). The bottom bar follows the signed-off
 * Wave 1 design (`w1-design-spec` §1): 86dp tall, white container, the active tab a filled accent
 * glyph in an accent-soft pill with an accent label. Feature screens are supplied by their modules;
 * this shell only knows the tab set and routing — it has no data or storage dependencies.
 */
@Composable
fun JGalleryApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState()
                .value?.destination?.hierarchy?.firstOrNull()?.route
            NavigationBar(
                modifier = Modifier.height(JGalleryDimens.NavHeight),
                containerColor = JGalleryColors.Background,
                tonalElevation = 0.dp,
            ) {
                GalleryTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(GalleryTab.Default.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = JGalleryColors.Accent,
                            selectedTextColor = JGalleryColors.Accent,
                            indicatorColor = JGalleryColors.AccentSoft,
                            unselectedIconColor = JGalleryColors.TextSecondary,
                            unselectedTextColor = JGalleryColors.TextSecondary,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = GalleryTab.Default.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(GalleryTab.ALBUMS.route) { AlbumsScreen() }
            composable(GalleryTab.PHOTOS.route) { PhotosScreen() }
            composable(GalleryTab.COLLECTIONS.route) { CollectionsScreen() }
            composable(GalleryTab.SEARCH.route) { SearchScreen() }
        }
    }
}

/** Filled glyph for the active tab (design §1: active = filled accent icon). */
private val GalleryTab.selectedIcon: ImageVector
    get() = when (this) {
        GalleryTab.ALBUMS -> Icons.Filled.PhotoLibrary
        GalleryTab.PHOTOS -> Icons.Filled.Photo
        GalleryTab.COLLECTIONS -> Icons.Filled.Category
        GalleryTab.SEARCH -> Icons.Filled.Search
    }

/** Outlined glyph for inactive tabs. */
private val GalleryTab.unselectedIcon: ImageVector
    get() = when (this) {
        GalleryTab.ALBUMS -> Icons.Outlined.PhotoLibrary
        GalleryTab.PHOTOS -> Icons.Outlined.Photo
        GalleryTab.COLLECTIONS -> Icons.Outlined.Category
        GalleryTab.SEARCH -> Icons.Outlined.Search
    }
