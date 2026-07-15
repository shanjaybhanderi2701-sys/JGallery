package com.appblish.jgallery.feature.albums

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.CaptureKind
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.SortBySheet
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.BulkOperationUiState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
import com.appblish.jgallery.core.ui.selection.SelectionScaffold
import com.appblish.jgallery.core.ui.selection.mediaSelectionDetails
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.selection.rememberTileSelectScale
import com.appblish.jgallery.core.ui.selection.selectableGridDrag
import com.appblish.jgallery.core.ui.selection.tileSelectScale
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Album detail (spec §3): one bucket's media as a flat grid, with the SAME E11 multi-select + bulk
 * chrome as Photos — long-press to select, drag range-select, Select All, and the Copy/Move/Delete
 * bulk bar (spec §7.6). The whole selection layer is the shared [SelectionScaffold]; only the grid
 * body differs from the Photos time-grid.
 */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val viewSettings by viewModel.viewSettings.collectAsStateWithLifecycle()

    // Delegated capture (APP-424): the system camera writes the photo into this album's folder via the
    // EXTRA_OUTPUT uri the ViewModel mints — TakePicture returns only a success Boolean and ignores the
    // camera's echoed result payload, and JGallery declares no CAMERA permission (Security gate APP-426).
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCaptureResult(success)
    }
    LaunchedEffect(viewModel) {
        viewModel.launchCapture.collect { capture ->
            try {
                takePicture.launch(capture.outputUri)
            } catch (_: ActivityNotFoundException) {
                // No camera app to handle the intent → discard the pending row and no-op gracefully
                // (never request CAMERA to "recover" — Security gate APP-426).
                viewModel.onCaptureResult(false)
            }
        }
    }

    AlbumDetailScreen(
        title = viewModel.title,
        sourceBucketId = viewModel.bucketId,
        state = state,
        viewSettings = viewSettings,
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        onBack = onBack,
        onMediaClick = onMediaClick,
        onSortChange = viewModel::setSort,
        onColumnsChange = viewModel::setColumns,
        onScopeChange = viewModel::setScope,
        onToggle = viewModel::toggleSelection,
        onBeginSelect = viewModel::beginSelection,
        onDragSelect = viewModel::dragSelectTo,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRunBulk = viewModel::runBulk,
        onRunBulkToNewAlbum = viewModel::runBulkToNewAlbum,
        onCancelBulk = viewModel::cancelBulk,
        onDismissResult = viewModel::dismissBulkResult,
        onOpenCamera = { viewModel.requestCapture(CaptureKind.PHOTO) },
        modifier = modifier,
    )
}

/** Stateless body — drivable without Hilt for instrumented tests. */
@Composable
fun AlbumDetailScreen(
    title: String,
    sourceBucketId: String?,
    state: AlbumDetailUiState,
    onBack: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewSettings: AlbumViewSettings = AlbumViewSettings(),
    selection: SelectionState<MediaId> = SelectionState(),
    bulk: BulkOperationUiState = BulkOperationUiState.Idle,
    destinations: List<Album> = emptyList(),
    onSortChange: (SortSpec) -> Unit = {},
    onColumnsChange: (ColumnCount) -> Unit = {},
    onScopeChange: (ViewScope) -> Unit = {},
    onToggle: (MediaId) -> Unit = {},
    onBeginSelect: (MediaId) -> Unit = {},
    onDragSelect: (MediaId, List<MediaId>) -> Unit = { _, _ -> },
    onSelectAll: (Collection<MediaId>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    onRunBulkToNewAlbum: (BulkAction, String) -> Unit = { _, _ -> },
    onCancelBulk: () -> Unit = {},
    onDismissResult: () -> Unit = {},
    onAddPhotos: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
    var showSortSheet by remember { mutableStateOf(false) }
    var showColumnSheet by remember { mutableStateOf(false) }

    val header: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("album_detail_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JGalleryColors.Text)
            }
            Text(
                text = title,
                color = JGalleryColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            // In-album control cluster (G1-9, design APP-465 TB-03): Sort + Grid size + scope toggle,
            // same structure as the Albums-tab overflow so the surface reads identically.
            AlbumViewMenu(
                scope = viewSettings.scope,
                onSortBy = { showSortSheet = true },
                onGridSize = { showColumnSheet = true },
                onScopeChange = onScopeChange,
            )
        }
    }

    when (state) {
        AlbumDetailUiState.Loading -> Column(modifier.fillMaxSize().testTag("album_detail_screen")) {
            header(); SkeletonGrid(columns = viewSettings.columns)
        }
        AlbumDetailUiState.Empty -> Column(modifier.fillMaxSize().testTag("album_detail_screen")) {
            header()
            EmptyAlbumState(
                onAddPhotos = onAddPhotos,
                onOpenCamera = onOpenCamera,
            )
        }
        is AlbumDetailUiState.Content -> {
            val ids = state.items.map { it.id }
            // Item 11: aggregate Details for the current media selection (multi-safe, any N ≥ 1).
            val details = remember(selection.selected, state.items) {
                if (selection.isActive) {
                    mediaSelectionDetails(state.items.filter { selection.isSelected(it.id) })
                } else {
                    null
                }
            }
            SelectionScaffold(
                selection = selection,
                bulk = bulk,
                albums = destinations,
                allIds = ids,
                sourceBucketId = sourceBucketId,
                tabHeader = header,
                onSelectAll = { onSelectAll(ids) },
                onClearSelection = onClearSelection,
                onRun = onRunBulk,
                coverFor = { it.coverRequest() },
                onCreateNew = onRunBulkToNewAlbum,
                onCancel = onCancelBulk,
                onDismissResult = onDismissResult,
                details = details,
                modifier = modifier.testTag("album_detail_screen"),
            ) {
                AlbumDetailGrid(
                    items = state.items,
                    orderedIds = ids,
                    columns = viewSettings.columns,
                    selection = selection,
                    onColumnsChange = onColumnsChange,
                    onMediaClick = onMediaClick,
                    onToggle = onToggle,
                    onBeginSelect = onBeginSelect,
                    onDragSelect = onDragSelect,
                )
            }
        }
    }

    // Sort + Grid-size sheets (G1-9): the D4 sheet family, shared with the Photos/Albums surfaces. Every
    // change persists immediately into the album's current scope via [onSortChange] / [onColumnsChange].
    if (showSortSheet) {
        SortBySheet(
            current = viewSettings.sort,
            onSelect = onSortChange,
            onDismiss = { showSortSheet = false },
        )
    }
    if (showColumnSheet) {
        ColumnCountSheet(
            current = viewSettings.columns,
            onSelect = onColumnsChange,
            onDismiss = { showColumnSheet = false },
        )
    }
}

/**
 * Album-detail control cluster menu (G1-9, design APP-465 TB-03): Sort by / Grid size plus the
 * "apply to this album only vs. all albums" scope toggle. Kept as an overflow to mirror the Albums-tab
 * overflow (`AlbumsOverflowMenu`) so both grid surfaces expose the same organize/view controls.
 */
@Composable
private fun AlbumViewMenu(
    scope: ViewScope,
    onSortBy: () -> Unit,
    onGridSize: () -> Unit,
    onScopeChange: (ViewScope) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag("album_detail_overflow_action"),
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "View options", tint = JGalleryColors.Text)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sort by") },
                leadingIcon = { Icon(Icons.Outlined.SortByAlpha, contentDescription = null) },
                onClick = { expanded = false; onSortBy() },
                modifier = Modifier.testTag("album_detail_menu_sort_by"),
            )
            DropdownMenuItem(
                text = { Text("Grid size") },
                leadingIcon = { Icon(Icons.Outlined.GridView, contentDescription = null) },
                onClick = { expanded = false; onGridSize() },
                modifier = Modifier.testTag("album_detail_menu_grid_size"),
            )
            Text(
                text = "APPLY TO",
                color = JGalleryColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp),
            )
            ScopeRow(
                label = "This album only",
                selected = scope == ViewScope.THIS_ALBUM,
                onClick = { expanded = false; onScopeChange(ViewScope.THIS_ALBUM) },
                testTag = "album_detail_scope_this",
            )
            ScopeRow(
                label = "All albums",
                selected = scope == ViewScope.ALL_ALBUMS,
                onClick = { expanded = false; onScopeChange(ViewScope.ALL_ALBUMS) },
                testTag = "album_detail_scope_all",
            )
        }
    }
}

@Composable
private fun ScopeRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    DropdownMenuItem(
        text = { Text(label, color = if (selected) JGalleryColors.Accent else JGalleryColors.Text) },
        trailingIcon = {
            Icon(
                Icons.Outlined.Check,
                contentDescription = if (selected) "Selected" else null,
                tint = if (selected) JGalleryColors.Accent else Color.Transparent,
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    )
}

@Composable
private fun AlbumDetailGrid(
    items: List<MediaItem>,
    orderedIds: List<MediaId>,
    columns: ColumnCount,
    selection: SelectionState<MediaId>,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onToggle: (MediaId) -> Unit,
    onBeginSelect: (MediaId) -> Unit,
    onDragSelect: (MediaId, List<MediaId>) -> Unit,
) {
    // G1-9: the column count is the persisted per-album Grid-size (via [onColumnsChange]); pinch-zoom
    // writes to the same source of truth, so pinch and the Grid-size sheet stay in lock-step and both
    // survive process death. Same callback-driven pinch primitive the Photos tab uses.
    val gridState = rememberLazyGridState()
    val tileShape = JGalleryDimens.tileRadius(columns)
    val idAtCell: (Int) -> MediaId? = { index -> items.getOrNull(index)?.id }

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
            modifier = Modifier.fillMaxSize().testTag("album_detail_grid"),
        ) {
            items(items, key = { it.id.value }) { item ->
                val scale = rememberTileSelectScale(selection.isSelected(item.id))
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .background(JGalleryColors.AccentSoft, tileShape)
                        .clickable {
                            if (selection.isActive) onToggle(item.id) else onMediaClick(item)
                        },
                ) {
                    AsyncImage(
                        model = item.thumbnailRequest(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().tileSelectScale(scale).clip(tileShape)
                            .background(JGalleryColors.TilePlaceholder),
                    )
                    // Item 8 (design C1-08): shared video play-icon overlay so videos are distinguishable
                    // in the album grid too — same disc/scrim/duration pill as the Photos tab.
                    if (item.type == MediaType.VIDEO) {
                        VideoOverlay(durationMillis = item.durationMillis, columns = columns.value)
                    }
                    SelectionCheckBadge(selected = selection.isSelected(item.id), active = selection.isActive)
                }
            }
        }

        // APP-466: flat-grid fast-scroller (position bubble) so a large album is grabbable — the last
        // of the shared grid set the in-album grid was missing.
        GridFastScroller(gridState = gridState, itemCount = items.size)

        // Item 2 (design C1-07): back-to-top FAB; hidden while a selection is active.
        ScrollToTopFab(gridState = gridState, enabled = !selection.isActive)
    }
}
