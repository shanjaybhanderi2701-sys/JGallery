package com.appblish.jgallery.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Photos tab (spec §4, design a05/a06/a07/a13/a14): the time-grouped stream over the whole cached
 * index. Every hot-path property the 10k gate needs is structural here:
 *
 * - cells/sections precomputed in the ViewModel (off-main) — composition does zero grouping work;
 * - stable per-item keys + contentType → LazyVerticalGrid reuses tiles instead of recomposing;
 * - fixed square tile geometry (no per-item measure);
 * - tiles load [thumbnailRequest] models only — the E4 pipeline guarantees no full-size decode, and
 *   its size-agnostic memory key makes the pinch morph a pure re-layout (no re-decode).
 */
@Composable
fun PhotosScreen(
    modifier: Modifier = Modifier,
    onMediaClick: (MediaItem) -> Unit = {},
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    PhotosScreen(
        state = state,
        columns = columns,
        onColumnsChange = viewModel::setColumns,
        onMediaClick = onMediaClick,
        modifier = modifier,
    )
}

/** Stateless body — instrumented tests drive this without Hilt (10k-item fixture, no device index). */
@Composable
fun PhotosScreen(
    state: PhotosUiState,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    modifier: Modifier = Modifier,
    onMediaClick: (MediaItem) -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().testTag("photos_screen")) {
        GalleryTabHeader(title = "Photos") {
            IconButton(
                onClick = { showColumnSheet = true },
                modifier = Modifier.testTag("photos_column_count_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = "Column count",
                    tint = JGalleryColors.Text,
                )
            }
        }

        when (state) {
            PhotosUiState.Loading -> SkeletonGrid(columns = columns)
            PhotosUiState.Empty -> EmptyTabState(
                icon = Icons.Outlined.Photo,
                title = "No photos yet",
                caption = "Photos and videos on this device will show up here.",
            )
            is PhotosUiState.Content -> PhotosGrid(
                timeline = state.timeline,
                columns = columns,
                onColumnsChange = onColumnsChange,
                onMediaClick = onMediaClick,
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
private fun PhotosGrid(
    timeline: PhotosTimeline,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val tileShape = JGalleryDimens.tileRadius(columns)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gridPinchColumns(currentColumns = { columns }, onColumnsChange = onColumnsChange),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.value),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            verticalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            modifier = Modifier.fillMaxSize().testTag("photos_grid"),
        ) {
            items(
                count = timeline.cells.size,
                key = { index -> timeline.cells[index].key },
                span = { index ->
                    when (timeline.cells[index]) {
                        is PhotosCell.DateHeader -> GridItemSpan(maxLineSpan)
                        is PhotosCell.Tile -> GridItemSpan(1)
                    }
                },
                contentType = { index ->
                    when (timeline.cells[index]) {
                        is PhotosCell.DateHeader -> "date_header"
                        is PhotosCell.Tile -> "media_tile"
                    }
                },
            ) { index ->
                when (val cell = timeline.cells[index]) {
                    is PhotosCell.DateHeader -> Text(
                        text = cell.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = JGalleryColors.Text,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
                    is PhotosCell.Tile -> MediaTile(
                        item = cell.item,
                        shape = tileShape,
                        onClick = { onMediaClick(cell.item) },
                    )
                }
            }
        }

        GridFastScroller(
            gridState = gridState,
            sectionStarts = timeline.sectionStarts,
            bubbleLabel = timeline::bubbleLabel,
        )
    }
}

/**
 * One square grid tile. The model is a [thumbnailRequest] — never a raw uri/path — so the load is
 * boundary-routed, size-capped, and served from the E4 caches (memory hit = zero IO, zero decode).
 */
@Composable
private fun MediaTile(item: MediaItem, shape: RoundedCornerShape, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(JGalleryColors.TilePlaceholder)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.thumbnailRequest(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.type == MediaType.VIDEO) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = formatDuration(item.durationMillis),
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
