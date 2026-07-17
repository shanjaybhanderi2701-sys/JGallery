package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaItem

const val ALBUM_DETAIL_BUCKET_ID_ARG = "bucketId"
const val ALBUM_DETAIL_NAME_ARG = "name"
const val ALBUM_DETAIL_VIDEO_ONLY_ARG = "videoOnly"
const val ALBUM_DETAIL_FILTER_ARG = "filter"

/** Route pattern for one album's media grid — the second surface E11 multi-select works on. */
const val ALBUM_DETAIL_ROUTE =
    "albumDetail/{$ALBUM_DETAIL_BUCKET_ID_ARG}?$ALBUM_DETAIL_NAME_ARG={$ALBUM_DETAIL_NAME_ARG}" +
        "&$ALBUM_DETAIL_VIDEO_ONLY_ARG={$ALBUM_DETAIL_VIDEO_ONLY_ARG}" +
        "&$ALBUM_DETAIL_FILTER_ARG={$ALBUM_DETAIL_FILTER_ARG}"

/**
 * Open the media grid for [bucketId] (tapped album card). [name] titles the screen. [videoOnly] scopes
 * the grid to videos — used by the Video smart album's folder-wise sub-albums (spec C4 item 5). [filter]
 * carries the active top-bar format chip into the album so opening a folder while "Videos" (or Photos/
 * GIFs) is selected yields only matching media (design C1-06, APP-467). The Recent / All-Videos smart
 * albums pass their sentinel bucket id (see [AlbumsCatalog]).
 */
fun NavController.navigateToAlbumDetail(
    bucketId: String,
    name: String,
    videoOnly: Boolean = false,
    filter: MediaFilter = MediaFilter.ALL,
) {
    navigate(
        "albumDetail/${Uri.encode(bucketId)}" +
            "?$ALBUM_DETAIL_NAME_ARG=${Uri.encode(name)}" +
            "&$ALBUM_DETAIL_VIDEO_ONLY_ARG=$videoOnly" +
            "&$ALBUM_DETAIL_FILTER_ARG=${filter.name}",
    )
}

/**
 * Open the Favorites smart view (G2 · APP-543): the album-detail grid backed by the [FavoritesStore]
 * sentinel bucket, which the detail ViewModel maps to a live "starred ids" query. Reuses the whole
 * album-detail surface (sort / group / grid-size / selection) for free.
 */
fun NavController.navigateToFavorites() {
    navigateToAlbumDetail(AlbumsCatalog.FAVORITES_BUCKET_ID, AlbumsCatalog.FAVORITES_NAME)
}

/**
 * Album-detail destination: a media grid scoped to one bucket, with the shared E11 multi-select +
 * bulk-ops chrome. [onMediaClick] opens the viewer scoped to this album; [onBack] pops. [onOpenTrash]
 * and [onAlbumCreated] back the shared 3-dot menu's Recycle Bin / Create album entries (APP-499) so
 * the album menu matches the home menu.
 */
fun NavGraphBuilder.albumDetailScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onOpenTrash: () -> Unit = {},
    onAlbumCreated: (name: String) -> Unit = {},
) {
    composable(
        route = ALBUM_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(ALBUM_DETAIL_BUCKET_ID_ARG) { type = NavType.StringType },
            navArgument(ALBUM_DETAIL_NAME_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument(ALBUM_DETAIL_VIDEO_ONLY_ARG) {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument(ALBUM_DETAIL_FILTER_ARG) {
                type = NavType.StringType
                defaultValue = MediaFilter.ALL.name
            },
        ),
    ) {
        AlbumDetailScreen(
            onBack = onBack,
            onMediaClick = onMediaClick,
            onOpenTrash = onOpenTrash,
            onAlbumCreated = onAlbumCreated,
        )
    }
}
