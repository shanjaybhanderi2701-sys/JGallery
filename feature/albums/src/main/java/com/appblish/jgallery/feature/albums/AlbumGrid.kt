package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.GridReflowPlacementSpec
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.selection.selectableGridDrag
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Reusable album cover grid shared by the Albums tab and the Video smart album's folder-wise screen
 * (spec C4). Same structural perf properties as the Photos grid: stable keys, fixed geometry, pinch
 * column morph persisted per tab. [onBeginSelect]/[onDragSelect] are optional — the nested Video grid
 * has no selection, so it passes neither and the drag gesture is not attached.
 */
@Composable
internal fun AlbumCoverGrid(
    albums: List<Album>,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    gridTestTag: String = "albums_grid",
    // Multi-select (G1-8, APP-467): bucketIds currently selected. Non-empty → selection mode is on, so
    // every card shows its selection check and the whole grid reads as a picker.
    selectedBucketIds: Set<String> = emptySet(),
    // Long-press-to-select + drag range-select (items 5 & 6): the container owns both, exactly like the
    // Photos grid, so long-press holds (no click-toggle rollback) and a sweep range-selects. Null in the
    // nested Video folder grid, which has no selection.
    onBeginSelect: ((bucketId: String) -> Unit)? = null,
    onDragSelect: ((bucketId: String, ordered: List<String>) -> Unit)? = null,
) {
    val selecting = selectedBucketIds.isNotEmpty()
    val gridState = rememberLazyGridState()
    // Grid adapter index → album bucketId (flat list, no headers) for the drag hit-test.
    val bucketAt: (Int) -> String? = { index -> albums.getOrNull(index)?.bucketId }
    val orderedBucketIds = remember(albums) { albums.map { it.bucketId } }

    val selectionModifier = if (onBeginSelect != null) {
        Modifier.selectableGridDrag(
            gridState = gridState,
            onSelectStart = { index -> bucketAt(index)?.let(onBeginSelect) },
            onDragOverIndex = { index -> bucketAt(index)?.let { onDragSelect?.invoke(it, orderedBucketIds) } },
        )
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize().then(selectionModifier)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.value),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.AlbumsGutter),
            verticalArrangement = Arrangement.spacedBy(JGalleryDimens.AlbumsGutter),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .gridPinchColumns(currentColumns = { columns }, onColumnsChange = onColumnsChange)
                .testTag(gridTestTag),
        ) {
            items(albums, key = { it.bucketId }) { album ->
                AlbumCoverCard(
                    // Pinch-release column swap slides each card to its new slot (APP-519).
                    modifier = Modifier.animateItem(placementSpec = GridReflowPlacementSpec),
                    album = album,
                    onClick = { onAlbumClick(album) },
                    selecting = selecting,
                    selected = album.bucketId in selectedBucketIds,
                )
            }
        }

        // APP-466: flat-grid fast-scroller so a large Collections/Video folder list is grabbable too
        // (auto-hidden until the list is more than ~4 viewports deep).
        GridFastScroller(gridState = gridState, itemCount = albums.size)

        // Item 2 (design C1-07): back-to-top FAB on the album grid (Collections + folder-wise Video grid).
        ScrollToTopFab(gridState = gridState)
    }
}

/** Cover (16dp radius, square) + optional pin badge + name + count, per design a04 / token table. */
@Composable
internal fun AlbumCoverCard(
    album: Album,
    onClick: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Tap only — long-press + drag selection is owned by the grid container (items 5 & 6), so a
            // long-press never resolves as a click here and can't toggle the just-selected card back off.
            .clickable(onClick = onClick)
            .testTag("album_card_${album.bucketId}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(JGalleryDimens.AlbumCoverRadius)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, JGalleryColors.Accent, JGalleryDimens.AlbumCoverRadius)
                    } else {
                        Modifier
                    },
                )
                .background(JGalleryColors.TilePlaceholder),
        ) {
            val cover = album.coverRequest()
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Item 8: under the Video filter the cover is a video frame — draw the shared play disc over
            // it (duration pill suppressed via hideDurationAtColumns = 0) so it reads unmistakably as a
            // video cover, matching the play affordance on the Photos/album-detail grids.
            if (album.coverIsVideo) {
                VideoOverlay(
                    durationMillis = 0L,
                    columns = 0,
                    hideDurationAtColumns = 0,
                    modifier = Modifier.testTag("album_video_cover_${album.bucketId}"),
                )
            }
            // Multi-select (G1-8, APP-467): a selection scrim + a check/hollow-ring in the top-end
            // corner, so long-press reads as "entered picker mode" and each tap toggles this card.
            if (selecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            JGalleryColors.Accent.copy(alpha = if (selected) 0.28f else 0f),
                        ),
                )
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) JGalleryColors.Accent else JGalleryColors.Background,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .then(if (selected) Modifier.background(JGalleryColors.Background, CircleShape) else Modifier)
                        .testTag(
                            if (selected) "album_selected_${album.bucketId}" else "album_unselected_${album.bucketId}",
                        ),
                )
            }
            // Pin affordance (spec C4 item 6) — a pinned album shows a pin badge. Final visual per C1.
            if (album.pinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(JGalleryColors.Accent)
                        .testTag("album_pin_badge_${album.bucketId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = JGalleryColors.Background,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            color = JGalleryColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "${album.itemCount}",
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.TextSecondary,
        )
    }
}
