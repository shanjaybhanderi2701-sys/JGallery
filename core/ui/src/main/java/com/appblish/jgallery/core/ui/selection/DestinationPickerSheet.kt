package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * iOS-style destination picker (spec §7.1/§7.2, design D2). A [ModalBottomSheet] listing the
 * device's albums; tapping one hands its `bucketId` back to the caller to run Copy to / Move to.
 * Creating a *new* folder is E10's create-album dialog — this picker only chooses an existing
 * destination, and hides the source album so you can't move items onto themselves.
 *
 * @param title "Copy to" / "Move to" — set by the caller from the chosen [BulkAction].
 * @param excludeBucketId the source bucket to omit (null on the Photos tab = show all).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationPickerSheet(
    title: String,
    albums: List<Album>,
    onPick: (bucketId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    excludeBucketId: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("destination_picker")) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = JGalleryColors.Text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            val destinations = albums.filter { it.bucketId != excludeBucketId }
            if (destinations.isEmpty()) {
                Text(
                    text = "No other albums to move to.",
                    color = JGalleryColors.TextSecondary,
                    modifier = Modifier.padding(20.dp).testTag("destination_empty"),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(destinations, key = { it.bucketId }) { album ->
                        DestinationRow(album) { onPick(album.bucketId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("destination_${album.bucketId}"),
    ) {
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            color = JGalleryColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${album.itemCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.TextSecondary,
        )
    }
}
