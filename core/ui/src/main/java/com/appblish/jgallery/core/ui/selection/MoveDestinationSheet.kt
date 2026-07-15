package com.appblish.jgallery.core.ui.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.ui.component.JGallerySheet
import com.appblish.jgallery.core.ui.theme.JGalleryColors

// C1-03 green (#23A55A) for the "New album" add-tile. NOT [JGalleryColors.TrustGreen], which is
// reserved for the §9.3 integrity shield — this is a plain create affordance, so it carries its own local.
private val NewAlbumGreen = Color(0xFF23A55A)
private val NewAlbumGreenSoft = Color(0xFFEAF6EF)

/**
 * Copy/Move destination sheet with **album cover thumbnails** and an inline **Create new album** step
 * (C1-03, item 12). Pick a destination by sight (3-up cover grid) instead of reading a text list; the
 * first tile is always a dashed-green "New album" add-tile whose inline create step drops the moved
 * items straight into a fresh album (they become its cover + contents — no empty-album problem, ties
 * C1-09). Serves both Copy and Move — [verb] swaps the wording and primary button. "Browse folders"
 * falls back to the device-folder picker for non-album paths.
 *
 * Cover models are supplied by [coverFor] (the feature layer passes `{ it.coverRequest() }`) so this
 * shared component never has to depend on the thumbnail pipeline — the §1.6 boundary stays intact.
 *
 * @param itemCount how many operands are being copied/moved — restated in the title ("Move 12 items to…").
 * @param itemNoun the operand noun for the title (default "items"; the whole-album path passes "albums"
 *   so it reads "Move 3 albums to…" — D4-03 C1: the count is known up front, no bucket expansion).
 * @param createSubtitle overrides the create-step subtitle. Default asserts the item count ("The 12
 *   items become its cover + contents"); the album path passes a count-free line since folder member
 *   counts aren't expanded to render the picker (D4-03 C1/C2).
 * @param onPick commit to an existing album's bucketId.
 * @param onCreateNew create a fresh album by name and move/copy the items into it (routes to C1-04 progress).
 * @param onBrowseFolders fall back to the full device-folder picker (W2-04).
 * @param excludeBucketId the source album to omit so items can't be moved onto themselves.
 */
@Composable
fun MoveDestinationSheet(
    verb: AlbumOpVerb,
    itemCount: Int,
    albums: List<Album>,
    coverFor: (Album) -> Any?,
    onPick: (bucketId: String) -> Unit,
    onCreateNew: (name: String) -> Unit,
    onBrowseFolders: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    excludeBucketId: String? = null,
    itemNoun: String = "items",
    createSubtitle: String? = null,
) {
    val destinations = remember(albums, excludeBucketId) {
        albums.filter { it.bucketId != excludeBucketId }
    }
    var selectedBucketId by remember { mutableStateOf<String?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val actionWord = if (verb == AlbumOpVerb.COPY) "Copy" else "Move"

    JGallerySheet(onDismiss = onDismiss, modifier = modifier.testTag("move_destination_sheet")) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            if (creatingNew) {
                // Inline create step (C1-03 phone 2): the items become the new album's cover + contents.
                CreateNewAlbumStep(
                    subtitle = createSubtitle ?: "The $itemCount $itemNoun become its cover + contents",
                    verb = verb,
                    name = newName,
                    onNameChange = { newName = it },
                    onCancel = { creatingNew = false },
                    onConfirm = { onCreateNew(newName.trim()) },
                )
                return@Column
            }

            Text(
                text = "$actionWord $itemCount $itemNoun to…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = JGalleryColors.Text,
                modifier = Modifier.padding(bottom = 12.dp).testTag("move_sheet_title"),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            ) {
                item(key = "__new_album__") {
                    NewAlbumTile(onClick = { creatingNew = true; newName = "" })
                }
                items(destinations, key = { it.bucketId }) { album ->
                    DestinationTile(
                        album = album,
                        cover = coverFor(album),
                        selected = album.bucketId == selectedBucketId,
                        onClick = { selectedBucketId = album.bucketId },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBrowseFolders,
                    modifier = Modifier.weight(1f).testTag("move_sheet_browse"),
                ) { Text("Browse folders") }
                Button(
                    onClick = { selectedBucketId?.let(onPick) },
                    enabled = selectedBucketId != null,
                    modifier = Modifier.weight(1f).testTag("move_sheet_commit"),
                ) { Text("$actionWord here") }
            }
        }
    }
}

/** Dashed-green "New album" add-tile — always the first slot (C1-03 callout #2). */
@Composable
private fun NewAlbumTile(onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick).testTag("move_sheet_new_album")) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(NewAlbumGreenSoft)
                .drawBehind {
                    drawRoundRect(
                        color = NewAlbumGreen,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = NewAlbumGreen, modifier = Modifier.size(28.dp))
        }
        Text(
            text = "New album",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = NewAlbumGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Create & move here",
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An existing album: cover thumbnail + name + count; selected = accent ring + check badge (callout #3). */
@Composable
private fun DestinationTile(album: Album, cover: Any?, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("move_dest_${album.bucketId}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(JGalleryColors.TilePlaceholder)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, JGalleryColors.Accent, RoundedCornerShape(16.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(JGalleryColors.Accent)
                        .testTag("move_dest_check_${album.bucketId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = JGalleryColors.OnAccent,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = JGalleryColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${album.itemCount}",
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.TextSecondary,
        )
    }
}

/** Inline "Create new album" step (C1-03 phone 2): name field + Cancel / Create & move. */
@Composable
private fun CreateNewAlbumStep(
    subtitle: String,
    verb: AlbumOpVerb,
    name: String,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("move_sheet_create_step")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NewAlbumGreenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = NewAlbumGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = "Create new album",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = JGalleryColors.Text,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = JGalleryColors.TextSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text("Album name") },
            modifier = Modifier.fillMaxWidth().testTag("move_sheet_name_field"),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = onConfirm,
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("move_sheet_create_confirm"),
            ) { Text(if (verb == AlbumOpVerb.COPY) "Create & copy" else "Create & move") }
        }
    }
}
