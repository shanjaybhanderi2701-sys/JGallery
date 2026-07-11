package com.appblish.jgallery.feature.albums

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.appblish.jgallery.core.model.Album

/** Route for the Video smart album's folder-wise grouping screen (spec C4 items 4 & 5). */
const val VIDEO_ALBUMS_ROUTE = "videoAlbums"

/** Open the Video smart album (All Videos + folder-wise grouping). */
fun NavController.navigateToVideoAlbums() {
    navigate(VIDEO_ALBUMS_ROUTE)
}

/**
 * Video-albums destination. [onOpenAlbum] receives either the All-Videos sentinel album or a real
 * folder album; the host translates that into the right album-detail query (video-scoped).
 */
fun NavGraphBuilder.videoAlbumsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    composable(route = VIDEO_ALBUMS_ROUTE) {
        VideoAlbumsScreen(onBack = onBack, onAlbumClick = onOpenAlbum)
    }
}
