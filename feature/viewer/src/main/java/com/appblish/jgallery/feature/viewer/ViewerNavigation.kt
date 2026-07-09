package com.appblish.jgallery.feature.viewer

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.appblish.jgallery.core.model.MediaId

const val VIEWER_MEDIA_ID_ARG = "mediaId"
const val VIEWER_BUCKET_ID_ARG = "bucketId"

/** Route pattern — the shell uses this to drop the bottom bar on the viewer destination. */
const val VIEWER_ROUTE = "viewer/{$VIEWER_MEDIA_ID_ARG}?$VIEWER_BUCKET_ID_ARG={$VIEWER_BUCKET_ID_ARG}"

/**
 * Open the full-screen viewer on [id]. [bucketId] scopes the pager to one album (album detail);
 * null pages across the whole Photos stream. This is the E6 grids' tap entry point.
 */
fun NavController.navigateToViewer(id: MediaId, bucketId: String? = null) {
    val base = "viewer/${Uri.encode(id.value)}"
    navigate(if (bucketId == null) base else "$base?$VIEWER_BUCKET_ID_ARG=${Uri.encode(bucketId)}")
}

fun NavGraphBuilder.viewerScreen(onBack: () -> Unit) {
    composable(
        route = VIEWER_ROUTE,
        arguments = listOf(
            navArgument(VIEWER_MEDIA_ID_ARG) { type = NavType.StringType },
            navArgument(VIEWER_BUCKET_ID_ARG) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        ViewerRoute(onBack = onBack)
    }
}
