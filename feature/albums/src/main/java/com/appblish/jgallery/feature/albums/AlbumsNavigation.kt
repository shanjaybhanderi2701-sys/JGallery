package com.appblish.jgallery.feature.albums

import androidx.navigation.NavController
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind

/**
 * Route a tapped Albums-tab card to the right destination (spec C4). The Video smart album opens its
 * folder-wise grouping screen; Recent and real folders open the album-detail media grid (Recent via
 * its sentinel bucket id, which the detail ViewModel maps to a library-wide newest-first query).
 */
fun NavController.openAlbum(album: Album) {
    when (album.kind) {
        AlbumKind.VIDEO -> navigateToVideoAlbums()
        AlbumKind.RECENT, AlbumKind.DEVICE_FOLDER -> navigateToAlbumDetail(album.bucketId, album.name)
    }
}

/**
 * Route a tapped Video-album member to its video-scoped media grid (spec C4 item 5): the All-Videos
 * sentinel opens every video library-wide; a folder opens that folder's videos only.
 */
fun NavController.openVideoMemberAlbum(album: Album) {
    if (album.bucketId == AlbumsCatalog.ALL_VIDEOS_BUCKET_ID) {
        navigateToAlbumDetail(album.bucketId, album.name)
    } else {
        navigateToAlbumDetail(album.bucketId, album.name, videoOnly = true)
    }
}
