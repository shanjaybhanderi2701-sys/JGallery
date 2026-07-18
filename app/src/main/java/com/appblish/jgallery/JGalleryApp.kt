package com.appblish.jgallery

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appblish.jgallery.core.ui.nav.GalleryTab
import com.appblish.jgallery.core.ui.nav.GalleryTabBar
import com.appblish.jgallery.core.ui.nav.GalleryTabBarItem
import com.appblish.jgallery.feature.albums.ADD_TO_ALBUM_ROUTE
import com.appblish.jgallery.feature.albums.ALBUM_DETAIL_ROUTE
import com.appblish.jgallery.feature.albums.AlbumsScreen
import com.appblish.jgallery.feature.albums.NEW_ALBUM_ROUTE
import com.appblish.jgallery.feature.albums.VIDEO_ALBUMS_ROUTE
import com.appblish.jgallery.feature.albums.addToAlbumScreen
import com.appblish.jgallery.feature.albums.albumDetailScreen
import com.appblish.jgallery.feature.albums.navigateToAddToAlbum
import com.appblish.jgallery.feature.albums.navigateToNewAlbum
import com.appblish.jgallery.feature.albums.newAlbumScreen
import com.appblish.jgallery.feature.albums.navigateToFavorites
import com.appblish.jgallery.feature.albums.openAlbum
import com.appblish.jgallery.feature.albums.openVideoMemberAlbum
import com.appblish.jgallery.feature.albums.videoAlbumsScreen
import com.appblish.jgallery.feature.photos.PhotosScreen
import com.appblish.jgallery.feature.search.SEARCH_ROUTE
import com.appblish.jgallery.feature.settings.ABOUT_ROUTE
import com.appblish.jgallery.feature.settings.LICENSES_ROUTE
import com.appblish.jgallery.feature.settings.SETTINGS_ROUTE
import com.appblish.jgallery.feature.settings.navigateToSettings
import com.appblish.jgallery.feature.settings.settingsScreen
import com.appblish.jgallery.feature.search.navigateToSearch
import com.appblish.jgallery.feature.search.searchScreen
import com.appblish.jgallery.feature.trash.TRASH_ROUTE
import com.appblish.jgallery.feature.trash.navigateToTrash
import com.appblish.jgallery.feature.trash.trashScreen
import com.appblish.jgallery.feature.viewer.VIEWER_ROUTE
import com.appblish.jgallery.feature.viewer.navigateToViewer
import com.appblish.jgallery.feature.viewer.viewerScreen

/**
 * The 2-tab navigation shell (design C1-01, item 10 — OnePlus-modeled): **Photos · Collections**,
 * Photos default. The old 4-tab bar (Albums/Photos/Collections/Search) collapses:
 *  - **Collections** hosts the Albums grid (AlbumsCatalog: Recent/Video/folders/pin, spec C4).
 *  - **Search** is a header action on both tabs → a full-screen route (tab bar hides, like the viewer).
 *  - **Recycle Bin** re-homes to the Collections (Albums) header overflow (the placeholder
 *    `CollectionsScreen` is retired from the shell).
 *
 * The bottom bar is [GalleryTabBar] (78dp, 25dp glyphs, 13sp/600 labels, active = accent icon+label +
 * a 4dp accent dot; no center FAB, no badges). Tab state is preserved on switch
 * (`saveState`/`restoreState`). Feature screens are supplied by their modules; this shell only knows
 * the tab set and routing — it has no data or storage dependencies.
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
            // Tapping a tile opens the E7 full-screen viewer, paged across the whole Photos stream.
            // Search is a header action → the full-screen search route.
            GalleryTab.PHOTOS -> PhotosScreen(
                onMediaClick = { item -> navController.navigateToViewer(item.id) },
                onOpenSearch = { navController.navigateToSearch() },
                // Photos overflow parity (design G1-D7 §2): Recycle bin + Create album reachable here too.
                onOpenTrash = { navController.navigateToTrash() },
                onAlbumCreated = { name -> navController.navigateToNewAlbum(name) },
                onOpenSettings = { navController.navigateToSettings() },
            )
            // Collections tab body = the Albums grid (spec C4). Album taps route by kind; Search is a
            // header action; the overflow's "Recycle Bin" re-homes the retired Collections utility.
            GalleryTab.COLLECTIONS -> AlbumsScreen(
                onAlbumClick = { album, filter -> navController.openAlbum(album, filter) },
                // Create-album (design C1-09): route into the new album's empty "Add photos" prompt so
                // it gets a cover and appears on the Albums home once the first item is added (APP-416).
                onAlbumCreated = { name -> navController.navigateToNewAlbum(name) },
                onOpenSearch = { navController.navigateToSearch() },
                onOpenTrash = { navController.navigateToTrash() },
                onOpenFavorites = { navController.navigateToFavorites() },
                onOpenSettings = { navController.navigateToSettings() },
            )
        }
    }

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState()
                .value?.destination?.hierarchy?.firstOrNull()?.route
            // Full-screen destinations own the whole canvas (their own chrome) — the tab bar hides for
            // them and returns on pop: the viewer, Recycle Bin, Search, album detail, and the album
            // create/add-photos flow.
            if (currentRoute == VIEWER_ROUTE || currentRoute == TRASH_ROUTE ||
                currentRoute == SEARCH_ROUTE || currentRoute == ALBUM_DETAIL_ROUTE ||
                currentRoute == VIDEO_ALBUMS_ROUTE || currentRoute == NEW_ALBUM_ROUTE ||
                currentRoute == ADD_TO_ALBUM_ROUTE || currentRoute == SETTINGS_ROUTE ||
                currentRoute == ABOUT_ROUTE || currentRoute == LICENSES_ROUTE
            ) return@Scaffold
            GalleryTabBar(
                items = GalleryTab.entries.map { it.tabBarItem },
                selectedRoute = currentRoute,
                onSelect = { item ->
                    navController.navigate(item.route) {
                        popUpTo(GalleryTab.Default.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
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
                onOpenTrash = { navController.navigateToTrash() },
                onAlbumCreated = { name -> navController.navigateToNewAlbum(name) },
            )
            // New-album empty prompt (design C1-09): after Create album, land here to add first photos.
            newAlbumScreen(
                onAddPhotos = { name -> navController.navigateToAddToAlbum(name) },
                onBack = { navController.popBackStack() },
            )
            // Add-photos picker (design C1-09): copies the selection into the new album, then pops back.
            addToAlbumScreen(
                onDone = {
                    // Pop past the picker AND the emptyNew prompt back to the Albums tab, where the new
                    // album now renders with a cover. Falls back to a single pop if the prompt is gone.
                    if (!navController.popBackStack(NEW_ALBUM_ROUTE, inclusive = true)) {
                        navController.popBackStack()
                    }
                },
            )
            // Video smart album (spec C4): All Videos + folder-wise grouping; each opens a video-scoped grid.
            videoAlbumsScreen(
                onBack = { navController.popBackStack() },
                onOpenAlbum = { album -> navController.openVideoMemberAlbum(album) },
            )
            // Full-screen viewer (E7). Grids open it via NavController.navigateToViewer(id, bucketId).
            viewerScreen(onBack = { navController.popBackStack() })
            // Full-screen Search (C1-01 item 10): opened from the Photos/Collections header search
            // action. Tapping a result opens the shared viewer (paged across the whole library).
            searchScreen(
                onBack = { navController.popBackStack() },
                onMediaClick = { item -> navController.navigateToViewer(item.id) },
            )
            // Recycle Bin (E9, spec §7.5). Opened from the Collections (Albums) header overflow.
            trashScreen(onBack = { navController.popBackStack() })
            // Settings (G2, APP-545): full-screen route opened from the overflow menu on both tabs.
            settingsScreen(navController = navController, onBack = { navController.popBackStack() })
        }
    }
}

/**
 * The [GalleryTabBar] item for each tab: route + label from the enum, with the filled/outlined glyph
 * pair (design §1: active = filled accent icon). Photos = a single photo; Collections = the stacked
 * album-library glyph (it hosts the Albums grid).
 */
private val GalleryTab.tabBarItem: GalleryTabBarItem
    get() = when (this) {
        GalleryTab.PHOTOS -> GalleryTabBarItem(
            route = route,
            label = label,
            selectedIcon = Icons.Filled.Photo,
            unselectedIcon = Icons.Outlined.Photo,
        )
        GalleryTab.COLLECTIONS -> GalleryTabBarItem(
            route = route,
            label = label,
            selectedIcon = Icons.Filled.PhotoLibrary,
            unselectedIcon = Icons.Outlined.PhotoLibrary,
        )
    }
