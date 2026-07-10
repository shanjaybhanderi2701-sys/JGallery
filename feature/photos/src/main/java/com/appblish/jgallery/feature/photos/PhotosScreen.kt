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
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.format.MediaDecodeBox
import com.appblish.jgallery.core.ui.format.MediaDecodeTilePlaceholder
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.BulkOperationUiState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
import com.appblish.jgallery.core.ui.selection.SelectionScaffold
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.selection.rememberTileSelectScale
import com.appblish.jgallery.core.ui.selection.selectableGridDrag
import com.appblish.jgallery.core.ui.selection.tileSelectScale
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
 *
 * Multi-select + bulk ops (spec §7.6) layer on via the shared [SelectionScaffold]: long-press enters
 * selection, tap toggles, drag range-selects, Select All; the bulk bar drives E8 copy/move/trash.
 */
@Composable
fun PhotosScreen(
    modifier: Modifier = Modifier,
    onMediaClick: (MediaItem) -> Unit = {},
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    PhotosScreen(
        state = state,
        columns = columns,
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        onColumnsChange = viewModel::setColumns,
        onMediaClick = onMediaClick,
        onToggle = viewModel::toggleSelection,
        onBeginSelect = viewModel::beginSelection,
        onDragSelect = viewModel::dragSelectTo,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRunBulk = viewModel::runBulk,
        onCancelBulk = viewModel::cancelBulk,
        onDismissResult = viewModel::dismissBulkResult,
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
    selection: SelectionState<MediaId> = SelectionState(),
    bulk: BulkOperationUiState = BulkOperationUiState.Idle,
    destinations: List<Album> = emptyList(),
    onMediaClick: (MediaItem) -> Unit = {},
    onToggle: (MediaId) -> Unit = {},
    onBeginSelect: (MediaId) -> Unit = {},
    onDragSelect: (MediaId, List<MediaId>) -> Unit = { _, _ -> },
    onSelectAll: (Collection<MediaId>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    onCancelBulk: () -> Unit = {},
    onDismissResult: () -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }

    val header: @Composable () -> Unit = {
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
    }

    when (state) {
        PhotosUiState.Loading -> Column(modifier.fillMaxSize().testTag("photos_screen")) {
            header(); SkeletonGrid(columns = columns)
        }
        PhotosUiState.Empty -> Column(modifier.fillMaxSize().testTag("photos_screen")) {
            header()
            EmptyTabState(
                icon = Icons.Outlined.Photo,
                title = "No photos yet",
                caption = "Photos and videos on this device will show up here.",
            )
        }
        is PhotosUiState.Content -> {
            val tileIds = remember(state.timeline) {
                state.timeline.cells.mapNotNull { (it as? PhotosCell.Tile)?.item?.id }
            }
            SelectionScaffold(
                selection = selection,
                bulk = bulk,
                albums = destinations,
                allIds = tileIds,
                tabHeader = header,
                onSelectAll = { onSelectAll(tileIds) },
                onClearSelection = onClearSelection,
                onRun = onRunBulk,
                onCancel = onCancelBulk,
                onDismissResult = onDismissResult,
                modifier = modifier.testTag("photos_screen"),
            ) {
                PhotosGrid(
                    timeline = state.timeline,
                    columns = columns,
                    selection = selection,
                    orderedIds = tileIds,
                    onColumnsChange = onColumnsChange,
                    onMediaClick = onMediaClick,
                    onToggle = onToggle,
                    onBeginSelect = onBeginSelect,
                    onDragSelect = onDragSelect,
                )
            }
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
    selection: SelectionState<MediaId>,
    orderedIds: List<MediaId>,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onToggle: (MediaId) -> Unit,
    onBeginSelect: (MediaId) -> Unit,
    onDragSelect: (MediaId, List<MediaId>) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val tileShape = JGalleryDimens.tileRadius(columns)

    // Resolve a grid adapter index to the media id under it (null for date headers).
    val idAtCell: (Int) -> MediaId? = { index ->
        (timeline.cells.getOrNull(index) as? PhotosCell.Tile)?.item?.id
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gridPinchColumns(currentColumns = { columns }, onColumnsChange = onColumnsChange)
            .selectableGridDrag(
                gridState = gridState,
                onSelectStart = { index -> idAtCell(index)?.let(onBeginSelect) },
                onDragOverIndex = { index -> idAtCell(index)?.let { onDragSelect(it, orderedIds) } },
            ),
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
                        selectionActive = selection.isActive,
                        selected = selection.isSelected(cell.item.id),
                        onClick = {
                            if (selection.isActive) onToggle(cell.item.id) else onMediaClick(cell.item)
                        },
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
 * In selection mode it insets (revealing the accent-tinted gap) and shows a check badge (spec §7.6).
 */
@Composable
private fun MediaTile(
    item: MediaItem,
    shape: RoundedCornerShape,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale = rememberTileSelectScale(selected)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(JGalleryColors.AccentSoft, shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .tileSelectScale(scale)
                .clip(shape)
                .background(JGalleryColors.TilePlaceholder),
        ) {
            // Central §8 degrade hook: renders the cached thumbnail, or the D3 placeholder for a
            // format we can't decode — corrupt/zero-byte/unknown types never crash the grid (APP-364).
            MediaDecodeBox(
                model = item.thumbnailRequest(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = { MediaDecodeTilePlaceholder(it) },
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
        SelectionCheckBadge(selected = selected, active = selectionActive)
    }
}
