package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.component.EmptyTabState

/**
 * Empty-folder state for album detail (design W3-08). Reuses the shared [EmptyTabState] `.emptybox`
 * family (same as empty-Trash W2-09 and the empty-library state) for the open-folder illustration and
 * copy, then hangs the two folder-specific CTAs in its actions slot:
 *
 * - **Add photos** routes into the W2-04 copy-to picker already targeting this album.
 * - **Camera** opens the system camera to capture straight into it.
 *
 * Distinct from the whole-device first-run state (W1-13) and empty Trash (W2-09): here the album
 * exists and is reachable, it just has no media yet — the state a freshly-created album (W2-03) lands on.
 */
@Composable
fun EmptyAlbumState(
    onAddPhotos: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyTabState(
        icon = Icons.Outlined.FolderOpen,
        title = "This folder is empty",
        caption = "Add photos or videos to this album, or snap a new one with the camera.",
        modifier = modifier.testTag("empty_album_state"),
    ) {
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EmptyAlbumCta(
                icon = Icons.Outlined.AddPhotoAlternate,
                label = "Add photos",
                onClick = onAddPhotos,
                testTag = "empty_album_add_photos",
            )
            EmptyAlbumCta(
                icon = Icons.Outlined.PhotoCamera,
                label = "Camera",
                onClick = onOpenCamera,
                testTag = "empty_album_camera",
            )
        }
    }
}

@Composable
private fun EmptyAlbumCta(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}
