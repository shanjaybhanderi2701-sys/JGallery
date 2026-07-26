package com.appblish.jgallery

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.appblish.jgallery.core.ui.nav.GalleryNavRail
import com.appblish.jgallery.core.ui.nav.GalleryTab
import com.appblish.jgallery.core.ui.nav.GalleryTabBar
import com.appblish.jgallery.core.ui.nav.GalleryTabBarItem
import com.appblish.jgallery.core.ui.window.LocalWindowSizeClass
import com.appblish.jgallery.core.ui.window.desiredOrientation
import com.appblish.jgallery.core.ui.window.findActivity
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

    // Current top-level route: drives both the tab-bar visibility (below) and the central orientation
    // writer. `firstOrNull()` on the destination hierarchy resolves to the route *template* (e.g.
    // VIEWER_ROUTE) even for arg-filled destinations, so equality checks against the route constants hold.
    val currentRoute = navController.currentBackStackEntryAsState()
        .value?.destination?.hierarchy?.firstOrNull()?.route

    // Adaptive foundation (APP-651): the single place that writes Activity.requestedOrientation.
    // Compact (phone) list/detail screens lock to portrait; the full-screen viewer allows landscape
    // (FULL_USER, honoring the system auto-rotate lock); Medium/Expanded never lock. Returning from
    // the viewer to a list re-runs this and restores portrait. See desiredOrientation() for the policy.
    val widthSizeClass = LocalWindowSizeClass.current.widthSizeClass
    val activity = LocalContext.current.findActivity()
    val isViewer = currentRoute == VIEWER_ROUTE
    LaunchedEffect(activity, widthSizeClass, isViewer) {
        activity?.requestedOrientation = desiredOrientation(widthSizeClass, isViewer)
    }

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

    // Top-level navigation surface (adaptive, APP-652 / spec §4.1). Compact (phone) keeps the
    // OnePlus bottom tab bar; Medium & Expanded (tablet / unfolded foldable) swap it for a leading
    // navigation rail. Full-screen routes hide *both* — they own the whole canvas. Same tab set, same
    // VM state, same route-driven selection: container swap only, no forked screens (spec §8).
    val navItems = GalleryTab.entries.map { it.tabBarItem }
    val onSelectTab: (GalleryTabBarItem) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(GalleryTab.Default.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val showTopLevelNav = currentRoute !in FULL_SCREEN_ROUTES
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    if (useNavRail && showTopLevelNav) {
        // Medium / Expanded with a top-level tab visible: rail on the leading edge, content fills the
        // rest. The rail owns the start + vertical safe insets and bleeds its background under the
        // display cutout (see GalleryNavRail); the content Scaffold consumes the start inset so the
        // system-bar / cutout gap is never applied a second time (coordination with APP-643).
        Row(modifier = Modifier.fillMaxSize()) {
            GalleryNavRail(
                items = navItems,
                selectedRoute = currentRoute,
                onSelect = onSelectTab,
            )
            Scaffold(
                modifier = Modifier
                    .weight(1f)
                    .consumeWindowInsets(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Start),
                    ),
            ) { innerPadding ->
                GalleryNavHost(
                    navController = navController,
                    resolvedTabContent = resolvedTabContent,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    } else {
        // Compact (phone), and every full-screen route on any size: a single Scaffold. The bottom bar
        // shows only on Compact top-level tabs — behaviorally unchanged from before this adaptive work.
        Scaffold(
            bottomBar = {
                if (!useNavRail && showTopLevelNav) {
                    GalleryTabBar(
                        items = navItems,
                        selectedRoute = currentRoute,
                        onSelect = onSelectTab,
                    )
                }
            },
        ) { innerPadding ->
            GalleryNavHost(
                navController = navController,
                resolvedTabContent = resolvedTabContent,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/**
 * Routes that own the whole canvas (their own chrome): the top-level tab navigation — bottom bar on
 * Compact, [GalleryNavRail] on Medium/Expanded — hides for these and returns on pop (the viewer,
 * Recycle Bin, Search, album detail, the video smart-album grid, the album create/add-photos flow,
 * and the Settings/About/Licenses stack). Extracted from the old inline bottom-bar allowlist so the
 * bar↔rail swap shares a single source of truth.
 */
private val FULL_SCREEN_ROUTES = setOf(
    VIEWER_ROUTE,
    TRASH_ROUTE,
    SEARCH_ROUTE,
    ALBUM_DETAIL_ROUTE,
    VIDEO_ALBUMS_ROUTE,
    NEW_ALBUM_ROUTE,
    ADD_TO_ALBUM_ROUTE,
    SETTINGS_ROUTE,
    ABOUT_ROUTE,
    LICENSES_ROUTE,
)

/**
 * The app's [NavHost] — the tab bodies plus every full-screen route. Hoisted out of [JGalleryApp] so
 * the Compact (bottom-bar Scaffold) and Medium/Expanded (nav-rail Row) shells render the *same* graph
 * without duplicating it (spec §8: reuse, don't fork). [modifier] carries the shell's content padding.
 */
@Composable
private fun GalleryNavHost(
    navController: NavHostController,
    resolvedTabContent: @Composable (GalleryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = GalleryTab.Default.route,
        modifier = modifier,
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
