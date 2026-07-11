package com.appblish.jgallery.feature.albums

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

const val NEW_ALBUM_NAME_ARG = "name"

/** Route pattern for the just-created album's empty "Add photos" prompt (design C1-09 emptyNew). */
const val NEW_ALBUM_ROUTE = "newAlbum/{$NEW_ALBUM_NAME_ARG}"

/**
 * Route straight into the just-created album [name] on its empty "Add photos"/Camera prompt (design
 * C1-09). The album folder already exists (createAlbum ran) but holds no media yet, so this is a
 * name-titled empty state rather than a bucket-addressed grid — a fresh album has no MediaStore rows to
 * query (APP-422). "Add photos" opens the whole-library picker that fills it.
 */
fun NavController.navigateToNewAlbum(name: String) {
    navigate("newAlbum/${Uri.encode(name)}")
}

/**
 * New-album emptyNew destination. [onAddPhotos] opens the add-photos picker for this album; [onBack]
 * pops back to the Albums tab.
 */
fun NavGraphBuilder.newAlbumScreen(
    onAddPhotos: (name: String) -> Unit,
    onBack: () -> Unit,
) {
    composable(
        route = NEW_ALBUM_ROUTE,
        arguments = listOf(navArgument(NEW_ALBUM_NAME_ARG) { type = NavType.StringType }),
    ) { entry ->
        val name = entry.arguments?.getString(NEW_ALBUM_NAME_ARG).orEmpty()
        NewAlbumScreen(
            title = name,
            onAddPhotos = { onAddPhotos(name) },
            onBack = onBack,
        )
    }
}
