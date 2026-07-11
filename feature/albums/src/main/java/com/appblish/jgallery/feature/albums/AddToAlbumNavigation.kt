package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val ADD_TO_ALBUM_NAME_ARG = "name"

/** Route pattern for the whole-library "Add photos to this album" picker (design C1-09). */
const val ADD_TO_ALBUM_ROUTE = "addToAlbum/{$ADD_TO_ALBUM_NAME_ARG}"

/**
 * Open the "Add photos" picker for the new album [name] (design C1-09): a flat, newest-first grid of the
 * whole library with a sticky "Add N" bar that copies the selection into the album via the §1.6
 * create-and-fill seam. The album is addressed by name — a just-created album has no bucket id to route
 * to (APP-422 ruling).
 */
fun NavController.navigateToAddToAlbum(name: String) {
    navigate("addToAlbum/${Uri.encode(name)}")
}

/**
 * Add-photos-to-album destination. [onDone] pops back once items are added (or the user backs out); the
 * new album then renders on the Albums home with a cover.
 */
fun NavGraphBuilder.addToAlbumScreen(onDone: () -> Unit) {
    composable(
        route = ADD_TO_ALBUM_ROUTE,
        arguments = listOf(navArgument(ADD_TO_ALBUM_NAME_ARG) { type = NavType.StringType }),
    ) {
        AddToAlbumScreen(onDone = onDone)
    }
}
