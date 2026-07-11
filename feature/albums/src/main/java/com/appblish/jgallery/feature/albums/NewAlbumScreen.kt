package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * The empty "Add photos" prompt a just-created album lands on (design C1-09 emptyNew). Reuses the shared
 * [EmptyAlbumState] (open-folder illustration + "Add photos"/Camera CTAs); "Add photos" opens the
 * whole-library picker that copies the first items in (the first becomes the cover). Titled by the new
 * album's name — a fresh album has no bucket id to address yet (APP-422), so this is a name-scoped state
 * rather than the bucket-addressed [AlbumDetailScreen] grid.
 */
@Composable
fun NewAlbumScreen(
    title: String,
    onAddPhotos: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCamera: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().testTag("new_album_screen")) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("new_album_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JGalleryColors.Text)
            }
            Text(
                text = title,
                color = JGalleryColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        EmptyAlbumState(onAddPhotos = onAddPhotos, onOpenCamera = onOpenCamera)
    }
}
