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
import com.appblish.jgallery.feature.albums.ALBUM_DETAIL_ROUTE
import com.appblish.jgallery.feature.albums.AlbumsScreen
import com.appblish.jgallery.feature.albums.albumDetailScreen
import com.appblish.jgallery.feature.albums.navigateToAlbumDetail
import com.appblish.jgallery.feature.collections.CollectionsScreen
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.search.SearchScreen
import com.appblish.jgallery.feature.viewer.VIEWER_ROUTE
import com.appblish.jgallery.feature.viewer.navigateToViewer
import com.appblish.jgallery.feature.viewer.viewerScreen

/**
 * The 4-tab navigation shell (spec §2): Albums | Photos | Collections | Search, Albums default.
 * Tab state is preserved on switch (`saveState`/`restoreState`). The bottom bar follows the signed-off
 * Wave 1 design (`w1-design-spec` §1): 86dp tall, white container, the active tab a filled accent
 * glyph in an accent-soft pill with an accent label. Feature screens are supplied by their modules;
 * this shell only knows the tab set and routing — it has no data or storage dependencies.
 *
 * [tabContent] (null = the real Hilt-backed feature screens, with grid taps wired to the viewer).
 * Shell tests pass tagged stubs so tab routing stays verifiable without DI (the grid screens have
 * their own tests against their stateless overloads).
 */
@Composable
fun JGalleryApp(
    tabContent: (@Composable (GalleryTab) -> Unit)? = null,
) {
    val navController = rememberNavController()

    val resolvedTabContent: @Composable (GalleryTab) -> Unit = tabContent ?: { tab ->
        when (tab) {
            GalleryTab.ALBUMS -> AlbumsScreen(
                // Tapping an album opens its media grid (album detail), where E11 multi-select works.
                onAlbumClick = { album -> navController.navigateToAlbumDetail(album.bucketId, album.name) },
            )
            // Tapping a tile opens the E7 full-screen viewer, paged across the whole Photos stream.
            GalleryTab.PHOTOS -> PhotosScreen(
                onMediaClick = { item -> navController.navigateToViewer(item.id) },
            )
            GalleryTab.COLLECTIONS -> CollectionsScreen()
            GalleryTab.SEARCH -> SearchScreen()
        }
    }

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState()
                .value?.destination?.hierarchy?.firstOrNull()?.route
            // The full-screen viewer owns the whole canvas (viewer-only dark chrome, spec §5) —
            // the tab bar disappears for it and returns on pop.
            if (currentRoute == VIEWER_ROUTE || currentRoute == ALBUM_DETAIL_ROUTE) return@Scaffold
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
            GalleryTab.entries.forEach { tab ->
                composable(tab.route) { resolvedTabContent(tab) }
            }
            // Album detail (spec §3): a bucket's media grid — the Albums surface for E11 multi-select.
            albumDetailScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { item -> navController.navigateToViewer(item.id, item.bucketId) },
            )
            // Full-screen viewer (E7). Grids open it via NavController.navigateToViewer(id, bucketId).
            viewerScreen(onBack = { navController.popBackStack() })
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
