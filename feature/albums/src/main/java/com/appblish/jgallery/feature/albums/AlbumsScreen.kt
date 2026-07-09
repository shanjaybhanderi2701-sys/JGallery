package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Albums tab — the default tab (spec §2/§3, design a04): album grid with cover thumbnails + item
 * counts from the cached index. Covers are [coverRequest] models, so they ride the same E4 cache as
 * grid tiles. Same structural perf properties as the Photos grid: stable keys, fixed geometry,
 * precomputed state, pinch column morph 2–6 persisted per tab.
 */
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    AlbumsScreen(
        state = state,
        columns = columns,
        onColumnsChange = viewModel::setColumns,
        onAlbumClick = onAlbumClick,
        modifier = modifier,
    )
}

/** Stateless body — instrumented tests drive this without Hilt. */
@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().testTag("albums_screen")) {
        GalleryTabHeader(title = "Albums") {
            IconButton(
                onClick = { showColumnSheet = true },
                modifier = Modifier.testTag("albums_column_count_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = "Column count",
                    tint = JGalleryColors.Text,
                )
            }
        }

        when (state) {
            AlbumsUiState.Loading -> SkeletonGrid(columns = columns)
            AlbumsUiState.Empty -> EmptyTabState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "No albums yet",
                caption = "Folders with photos or videos will show up here.",
            )
            is AlbumsUiState.Content -> AlbumsGrid(
                albums = state.albums,
                columns = columns,
                onColumnsChange = onColumnsChange,
                onAlbumClick = onAlbumClick,
            )
        }
    }

    if (showColumnSheet) {
        ColumnCountSheet(
            current = columns,
            onSelect = onColumnsChange,
            onDismiss = { showColumnSheet = false },
        )
    }
}

@Composable
private fun AlbumsGrid(
    albums: List<Album>,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onAlbumClick: (Album) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.value),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.AlbumsGutter),
        verticalArrangement = Arrangement.spacedBy(JGalleryDimens.AlbumsGutter),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 16.dp,
        ),
        modifier = Modifier
            .fillMaxSize()
            .gridPinchColumns(currentColumns = { columns }, onColumnsChange = onColumnsChange)
            .testTag("albums_grid"),
    ) {
        items(albums, key = { it.bucketId }) { album ->
            AlbumCard(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

/** Cover (16dp radius, square) + name + count, per design a04 / token table. */
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
