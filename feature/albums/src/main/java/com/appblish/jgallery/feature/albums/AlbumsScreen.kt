package com.appblish.jgallery.feature.albums

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.component.SortBySheet
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Albums tab — the default tab (spec §2/§3, design a04): album grid with cover thumbnails + item
 * counts from the cached index. The overflow (spec §3) hosts Sort By, Column count and Create album
 * (spec §6). Covers are [coverRequest] models, so they ride the same E4 cache as grid tiles. Same
 * structural perf properties as the Photos grid: stable keys, fixed geometry, precomputed state,
 * pinch column morph 2–6 persisted per tab.
 */
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface create-album outcomes (spec §6) as a toast while this tab is on screen.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            val message = when (result) {
                is CreateAlbumResult.Success -> "Album “${result.name}” created"
                is CreateAlbumResult.Failure -> result.reason
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AlbumsScreen(
        state = state,
        columns = columns,
        sort = sort,
        onColumnsChange = viewModel::setColumns,
        onSortChange = viewModel::setSort,
        onCreateAlbum = viewModel::createAlbum,
        onAlbumClick = onAlbumClick,
        modifier = modifier,
    )
}

/** Stateless body — instrumented tests drive this without Hilt. */
@Composable
fun AlbumsScreen(
    state: AlbumsUiState,
    columns: ColumnCount,
    sort: SortSpec,
    onColumnsChange: (ColumnCount) -> Unit,
    onSortChange: (SortSpec) -> Unit,
    onCreateAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().testTag("albums_screen")) {
        GalleryTabHeader(title = "Albums") {
            AlbumsOverflowMenu(
                onSortBy = { showSortSheet = true },
                onColumnCount = { showColumnSheet = true },
                onCreateAlbum = { showCreateDialog = true },
            )
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
    if (showSortSheet) {
        SortBySheet(
            current = sort,
            onSelect = onSortChange,
            onDismiss = { showSortSheet = false },
        )
    }
    if (showCreateDialog) {
        NameInputDialog(
            title = "Create album",
            label = "Album name",
            confirmLabel = "Create",
            onConfirm = { name ->
                onCreateAlbum(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

/** Albums overflow (spec §3): Sort By, Column count, Create album. */
@Composable
private fun AlbumsOverflowMenu(
    onSortBy: () -> Unit,
    onColumnCount: () -> Unit,
    onCreateAlbum: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("albums_overflow_action"),
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More options",
                tint = JGalleryColors.Text,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sort by") },
                onClick = { expanded = false; onSortBy() },
                modifier = Modifier.testTag("albums_menu_sort_by"),
            )
            DropdownMenuItem(
                text = { Text("Column count") },
                onClick = { expanded = false; onColumnCount() },
                modifier = Modifier.testTag("albums_menu_column_count"),
            )
            DropdownMenuItem(
                text = { Text("Create album") },
                onClick = { expanded = false; onCreateAlbum() },
                modifier = Modifier.testTag("albums_menu_create_album"),
            )
        }
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
