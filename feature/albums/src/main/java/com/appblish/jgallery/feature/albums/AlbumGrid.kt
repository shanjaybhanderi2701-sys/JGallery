package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Reusable album cover grid shared by the Albums tab and the Video smart album's folder-wise screen
 * (spec C4). Same structural perf properties as the Photos grid: stable keys, fixed geometry, pinch
 * column morph persisted per tab. [onAlbumLongClick] is optional — the nested Video grid has no
 * per-album actions.
 */
@Composable
internal fun AlbumCoverGrid(
    albums: List<Album>,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    gridTestTag: String = "albums_grid",
    onAlbumLongClick: ((Album) -> Unit)? = null,
) {
    val gridState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize()) {
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
                    album = album,
                    onClick = { onAlbumClick(album) },
                    onLongClick = onAlbumLongClick?.let { { it(album) } },
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumCoverCard(album: Album, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("album_card_${album.bucketId}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(JGalleryDimens.AlbumCoverRadius)
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
