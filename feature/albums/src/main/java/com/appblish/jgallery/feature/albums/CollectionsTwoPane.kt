package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.window.LocalWindowSizeClass

/**
 * Collections tab entry point (APP-654, spec APP-645 §4). Width-adaptive:
 *
 * - **Compact / Medium** → the single-pane [AlbumsScreen] exactly as before; tapping an album pushes
 *   the full-screen album detail via [onAlbumClick] (the shell's outer NavController). Zero behavior
 *   change from the pre-654 shell.
 * - **Expanded (≥840dp)** → a **two-pane list + detail** (board decision Q2 = Expanded-only): a
 *   persistent album-list pane on the left and the selected album's contents on the right, no
 *   full-screen push. See [CollectionsTwoPane].
 *
 * The width is read once from [LocalWindowSizeClass] (the APP-651 foundation seam), so a foldable
 * fold/unfold re-derives the layout live on the resulting configuration change.
 */
@Composable
fun CollectionsTab(
    onAlbumClick: (Album, MediaFilter) -> Unit,
    onAlbumCreated: (name: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded =
        LocalWindowSizeClass.current.widthSizeClass == WindowWidthSizeClass.Expanded
    if (expanded) {
        CollectionsTwoPane(
            onAlbumFullScreen = onAlbumClick,
            onAlbumCreated = onAlbumCreated,
            onOpenSearch = onOpenSearch,
            onOpenTrash = onOpenTrash,
            onOpenFavorites = onOpenFavorites,
            onOpenSettings = onOpenSettings,
            onMediaClick = onMediaClick,
            modifier = modifier,
        )
    } else {
        AlbumsScreen(
            onAlbumClick = onAlbumClick,
            onAlbumCreated = onAlbumCreated,
            onOpenSearch = onOpenSearch,
            onOpenTrash = onOpenTrash,
            onOpenFavorites = onOpenFavorites,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    }
}

/** The album currently open in the Expanded detail pane. Hoisted (see [SelectedAlbumSaver]) so it
 *  survives rotate / fold — the DoD's "selected album survives rotate/fold" (spec §7.3/§7.4). */
private data class SelectedAlbum(
    val bucketId: String,
    val name: String,
    val videoOnly: Boolean,
    val filter: MediaFilter,
)

/** rememberSaveable saver for [SelectedAlbum] — flattened to primitives, no parcelable plugin needed. */
private val SelectedAlbumSaver = listSaver<SelectedAlbum?, Any>(
    save = { sel ->
        if (sel == null) emptyList() else listOf(sel.bucketId, sel.name, sel.videoOnly, sel.filter.name)
    },
    restore = { stored ->
        if (stored.isEmpty()) {
            null
        } else {
            SelectedAlbum(
                bucketId = stored[0] as String,
                name = stored[1] as String,
                videoOnly = stored[2] as Boolean,
                filter = MediaFilter.valueOf(stored[3] as String),
            )
        }
    },
)

/** Start destination of the detail-pane NavHost — the "nothing selected yet" placeholder. */
private const val COLLECTIONS_DETAIL_HOME = "collections_detail_home"

/**
 * Expanded two-pane Collections (spec §4.2): list pane (≈340dp) + detail pane (the selected album's
 * media grid). The detail pane is a **nested NavHost** so the reused [albumDetailScreen] resolves each
 * album's own Hilt [AlbumDetailViewModel] from its bucket nav-arg (identical wiring to the full-screen
 * route — §8 "reuse, don't fork"). Because both panes render their own screens, each screen's own
 * multi-select / bulk action bar naturally spans only its pane (§8).
 */
@Composable
private fun CollectionsTwoPane(
    onAlbumFullScreen: (Album, MediaFilter) -> Unit,
    onAlbumCreated: (name: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable(stateSaver = SelectedAlbumSaver) {
        mutableStateOf<SelectedAlbum?>(null)
    }

    val detailNav = rememberNavController()

    // Drive the detail pane from the hoisted selection. Runs on first composition too, so a selection
    // restored across rotate/fold re-opens its album deterministically. launchSingleTop dedupes the
    // re-navigation when the restored nav back stack already lands on the same album.
    LaunchedEffect(selected) {
        val sel = selected
        if (sel == null) {
            detailNav.popBackStack(COLLECTIONS_DETAIL_HOME, inclusive = false)
        } else {
            detailNav.navigate(albumDetailPaneRoute(sel)) {
                popUpTo(COLLECTIONS_DETAIL_HOME) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Row(modifier = modifier.fillMaxSize().testTag("collections_two_pane")) {
        // LIST PANE — the same AlbumsScreen; taps SELECT (no push) instead of navigating. Foldable
        // hinge-snap of this divider is deferred to APP-655 (androidx.window); v1 uses a fixed ratio.
        Box(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight()
                .testTag("collections_list_pane"),
        ) {
            AlbumsScreen(
                onAlbumClick = { album, filter ->
                    when (album.kind) {
                        AlbumKind.RECENT, AlbumKind.DEVICE_FOLDER ->
                            selected = SelectedAlbum(album.bucketId, album.name, videoOnly = false, filter = filter)
                        // The Video smart album opens a nested folder-wise grouping surface, and the
                        // Favorites smart album its own starred collection — neither is a plain album
                        // grid, so keep them full-screen (outer nav) rather than embed a navigator.
                        AlbumKind.VIDEO, AlbumKind.FAVORITES -> onAlbumFullScreen(album, filter)
                    }
                },
                onAlbumCreated = onAlbumCreated,
                onOpenSearch = onOpenSearch,
                onOpenTrash = onOpenTrash,
                onOpenFavorites = onOpenFavorites,
                onOpenSettings = onOpenSettings,
                highlightedBucketId = selected?.bucketId,
                // Auto-select the first album so the detail pane is never empty (§4.2). Only seeds when
                // nothing is chosen, so it never overrides a user tap or a restored selection.
                onFirstAlbumResolved = { first ->
                    // Only seed a browsable album (Recent / real folder) into the detail pane; the Video
                    // and Favorites smart albums are their own full-screen surfaces, never embedded.
                    val browsable = first != null &&
                        (first.kind == AlbumKind.RECENT || first.kind == AlbumKind.DEVICE_FOLDER)
                    if (selected == null && browsable) {
                        selected = SelectedAlbum(first!!.bucketId, first.name, videoOnly = false, filter = MediaFilter.ALL)
                    }
                },
            )
        }

        VerticalDivider()

        // DETAIL PANE — reused album-detail via a nested NavHost, back button suppressed (§4.2).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("collections_detail_pane"),
        ) {
            NavHost(navController = detailNav, startDestination = COLLECTIONS_DETAIL_HOME) {
                composable(COLLECTIONS_DETAIL_HOME) { CollectionsDetailPlaceholder() }
                albumDetailScreen(
                    onBack = {},
                    onMediaClick = onMediaClick,
                    onOpenTrash = onOpenTrash,
                    onAlbumCreated = onAlbumCreated,
                    showBack = false,
                )
            }
        }
    }
}

/** Build the album-detail route for the detail-pane NavHost (mirrors [NavController.navigateToAlbumDetail]). */
private fun albumDetailPaneRoute(sel: SelectedAlbum): String =
    "albumDetail/${Uri.encode(sel.bucketId)}" +
        "?$ALBUM_DETAIL_NAME_ARG=${Uri.encode(sel.name)}" +
        "&$ALBUM_DETAIL_VIDEO_ONLY_ARG=${sel.videoOnly}" +
        "&$ALBUM_DETAIL_FILTER_ARG=${sel.filter.name}"

/**
 * Detail-pane empty state (spec §4.4): centered within the pane, reading-width capped at 640dp so the
 * copy never runs edge-to-edge on a wide tablet. Shown only in the brief window before the first album
 * auto-selects, or if the library has no albums at all.
 */
@Composable
private fun CollectionsDetailPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().testTag("collections_detail_placeholder"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = JGalleryColors.TextSecondary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "Select an album",
                color = JGalleryColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Choose an album from the list to see its photos and videos here.",
                color = JGalleryColors.TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
