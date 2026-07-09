package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.appblish.jgallery.core.model.MediaItem

const val ALBUM_DETAIL_BUCKET_ID_ARG = "bucketId"
const val ALBUM_DETAIL_NAME_ARG = "name"

/** Route pattern for one album's media grid — the second surface E11 multi-select works on. */
const val ALBUM_DETAIL_ROUTE = "albumDetail/{$ALBUM_DETAIL_BUCKET_ID_ARG}?$ALBUM_DETAIL_NAME_ARG={$ALBUM_DETAIL_NAME_ARG}"

/** Open the media grid for [bucketId] (tapped album card). [name] titles the screen. */
fun NavController.navigateToAlbumDetail(bucketId: String, name: String) {
    navigate("albumDetail/${Uri.encode(bucketId)}?$ALBUM_DETAIL_NAME_ARG=${Uri.encode(name)}")
}

/**
 * Album-detail destination: a flat media grid scoped to one bucket, with the shared E11 multi-select
 * + bulk-ops chrome. [onMediaClick] opens the viewer scoped to this album; [onBack] pops.
 */
fun NavGraphBuilder.albumDetailScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
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
        ),
    ) {
        AlbumDetailScreen(onBack = onBack, onMediaClick = onMediaClick)
    }
}
