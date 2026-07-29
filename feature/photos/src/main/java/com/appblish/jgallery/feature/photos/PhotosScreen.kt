package com.appblish.jgallery.feature.photos

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.FileNames
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.formatBadge
import com.appblish.jgallery.core.model.isPanorama
import com.appblish.jgallery.core.thumbs.ThumbnailSizes
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.thumbs.memoryCacheKey
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.format.MediaDecodeBox
import com.appblish.jgallery.core.ui.format.MediaDecodeTilePlaceholder
import com.appblish.jgallery.core.ui.grid.GridThumbnailPrefetch
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.FavoriteHeartBadge
import com.appblish.jgallery.core.ui.component.FormatBadgeChip
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.transition.photoSharedElement
import com.appblish.jgallery.core.ui.window.GridContent
import com.appblish.jgallery.core.ui.window.adaptiveColumns
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.appblish.jgallery.core.ui.component.CollapsibleContent
import com.appblish.jgallery.core.ui.component.FormatFilterChips
import com.appblish.jgallery.core.ui.component.rememberCollapseOnScrollState
import com.appblish.jgallery.core.ui.component.GalleryMenuItem
import com.appblish.jgallery.core.ui.component.GalleryTopBar
import com.appblish.jgallery.core.ui.component.GroupBySheet
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.component.SortBySheet
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GalleryPullToRefresh
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.GridReflowPlacementSpec
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.BulkOperationUiState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
import com.appblish.jgallery.core.ui.selection.mediaSelectionDetails
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.appblish.jgallery.core.ui.selection.SelectionScaffold
import com.appblish.jgallery.core.ui.share.MediaShareRequest
import com.appblish.jgallery.core.ui.share.ShareIntents
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.selection.rememberTileSelectScale
import com.appblish.jgallery.core.ui.selection.selectableGridDrag
import com.appblish.jgallery.core.ui.selection.tileSelectScale
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * Photos tab (spec §4, design a05/a06/a07/a13/a14): the time-grouped stream over the whole cached
 * index. Windowed loading (APP-700 L2) keeps O(#sections) memory at rest; only [WINDOW_SIZE] items
 * per page are ever materialized.
 *
 * Multi-select + bulk ops (spec §7.6) layer on via the shared [SelectionScaffold]: long-press enters
 * selection, tap toggles, drag range-selects, Select All; the bulk bar drives E8 copy/move/trash.
 */
@Composable
fun PhotosScreen(
    modifier: Modifier = Modifier,
    onMediaClick: (MediaItem) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onAlbumCreated: (name: String) -> Unit = {},
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val groupBy by viewModel.groupBy.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val allOrderedIds by viewModel.allOrderedIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            when (result) {
                is PhotosCreateAlbumResult.Success -> onAlbumCreated(result.name)
                is PhotosCreateAlbumResult.Failure ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.renameEvents.collect { result ->
            val message = when (result) {
                is PhotosRenameResult.Success -> "Renamed"
                is PhotosRenameResult.Failure -> result.reason
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { request ->
            when (request) {
                is MediaShareRequest.Ready -> context.launchShareSheet(request.uris, request.mimeType)
                MediaShareRequest.Empty ->
                    Toast.makeText(context, "Nothing left to share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri -> if (treeUri != null) viewModel.exportSelected(treeUri) }

    PhotosScreen(
        state = state,
        columns = columns,
        filter = filter,
        groupBy = groupBy,
        sort = sort,
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        isRefreshing = isRefreshing,
        allOrderedIds = allOrderedIds,
        onRefresh = viewModel::refresh,
        onColumnsChange = viewModel::setColumns,
        onFilterChange = viewModel::setFilter,
        onGroupChange = viewModel::setGroupBy,
        onSortChange = viewModel::setSort,
        onMediaClick = onMediaClick,
        onOpenSearch = onOpenSearch,
        onOpenTrash = onOpenTrash,
        onOpenSettings = onOpenSettings,
        onCreateAlbum = viewModel::createAlbum,
        favorites = favorites,
        onSetFavorites = viewModel::setFavorites,
        onToggle = viewModel::toggleSelection,
        onBeginSelect = viewModel::beginSelection,
        onDragSelect = viewModel::dragSelectTo,
        onSelectAll = viewModel::selectAllFiltered,
        onClearSelection = viewModel::clearSelection,
        onRunBulk = viewModel::runBulk,
        onRunBulkToNewAlbum = viewModel::runBulkToNewAlbum,
        onCancelBulk = viewModel::cancelBulk,
        onDismissResult = viewModel::dismissBulkResult,
        onRenameSelected = viewModel::renameSelected,
        onShare = viewModel::shareSelected,
        onExport = { exportFolderPicker.launch(null) },
        onLoadWindow = viewModel::loadWindowFor,
        modifier = modifier,
    )
}

private fun android.content.Context.launchShareSheet(uris: List<android.net.Uri>, mimeType: String) {
    if (uris.isEmpty()) return
    val intent = ShareIntents.buildSendIntent(uris, mimeType)
    runCatching { startActivity(android.content.Intent.createChooser(intent, "Share")) }
}

/** Stateless body — instrumented tests drive this without Hilt (10k-item fixture, no device index). */
@Composable
fun PhotosScreen(
    state: PhotosUiState,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    modifier: Modifier = Modifier,
    filter: MediaFilter = MediaFilter.ALL,
    onFilterChange: (MediaFilter) -> Unit = {},
    groupBy: GroupBy = GroupBy.DAY,
    onGroupChange: (GroupBy) -> Unit = {},
    sort: SortSpec = SortSpec(),
    onSortChange: (SortSpec) -> Unit = {},
    selection: SelectionState<MediaId> = SelectionState(),
    bulk: BulkOperationUiState = BulkOperationUiState.Idle,
    destinations: List<Album> = emptyList(),
    isRefreshing: Boolean = false,
    /** Ordered ids used for range-select drag; populated after [onBeginSelect] is called. */
    allOrderedIds: List<MediaId> = emptyList(),
    onRefresh: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    favorites: Set<MediaId> = emptySet(),
    // Favorites rework (APP-670): the grid no longer toggles per tile; bulk Add/Remove flows through the
    // selection overflow (§5). The badge is a read-only indicator bound to [favorites].
    onSetFavorites: (Set<MediaId>, Boolean) -> Unit = { _, _ -> },
    onToggle: (MediaId) -> Unit = {},
    onBeginSelect: (MediaId) -> Unit = {},
    onDragSelect: (MediaId, List<MediaId>) -> Unit = { _, _ -> },
    /** Select-all callback; no collection arg — the ViewModel loads ids via mediaIdsMatching. */
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    onRunBulkToNewAlbum: (BulkAction, String) -> Unit = { _, _ -> },
    onCancelBulk: () -> Unit = {},
    onDismissResult: () -> Unit = {},
    onRenameSelected: (MediaId, String) -> Unit = { _, _ -> },
    onShare: () -> Unit = {},
    onExport: () -> Unit = {},
    /** Triggers a window load for the given item ordinal (called by the grid on scroll). */
    onLoadWindow: (Int) -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val filterBarCollapse = rememberCollapseOnScrollState()
    LaunchedEffect(selection.isActive) {
        if (!selection.isActive) filterBarCollapse.reveal()
    }

    val header: @Composable () -> Unit = {
        GalleryTopBar(
            title = "Photos",
            onSearch = onOpenSearch,
            searchTestTag = "photos_search_action",
            overflowTestTag = "photos_overflow_action",
            overflowItems = listOf(
                GalleryMenuItem(
                    label = "Sort by",
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    testTag = "photos_menu_sort_by",
                    onClick = { showSortSheet = true },
                ),
                GalleryMenuItem(
                    label = "Group by",
                    icon = Icons.Outlined.DateRange,
                    testTag = "photos_menu_group_by",
                    onClick = { showGroupSheet = true },
                ),
                GalleryMenuItem(
                    label = "Column count",
                    icon = Icons.Outlined.GridView,
                    testTag = "photos_menu_column_count",
                    onClick = { showColumnSheet = true },
                ),
                GalleryMenuItem(
                    label = "Create album",
                    icon = Icons.Outlined.CreateNewFolder,
                    testTag = "photos_menu_create_album",
                    onClick = { showCreateDialog = true },
                ),
                GalleryMenuItem(
                    label = "Recycle Bin",
                    icon = Icons.Outlined.Delete,
                    testTag = "photos_menu_recycle_bin",
                    onClick = onOpenTrash,
                    dividerBefore = true,
                ),
                GalleryMenuItem(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    testTag = "photos_menu_settings",
                    onClick = onOpenSettings,
                    dividerBefore = true,
                ),
            ),
        )
    }

    when (state) {
        PhotosUiState.Loading -> Column(modifier.fillMaxSize().testTag("photos_screen")) {
            header(); SkeletonGrid(columns = adaptiveColumns(columns, GridContent.MEDIA))
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
            // Details + single-selected: look up items from the windowed cache. Items are available
            // as soon as their window page is loaded, which happens before selection is possible.
            val details = remember(selection.selected, state.timeline) {
                if (selection.isActive) {
                    val selectedItems = selection.selected.mapNotNull { state.timeline.itemById(it) }
                    mediaSelectionDetails(selectedItems)
                } else null
            }
            val singleSelected = remember(selection.selected, state.timeline) {
                if (selection.count == 1) state.timeline.itemById(selection.selected.first()) else null
            }
            SelectionScaffold(
                selection = selection,
                bulk = bulk,
                albums = destinations,
                allIds = allOrderedIds,
                tabHeader = header,
                onSelectAll = onSelectAll,
                onClearSelection = onClearSelection,
                onRun = onRunBulk,
                coverFor = { it.coverRequest() },
                onCreateNew = onRunBulkToNewAlbum,
                onCancel = onCancelBulk,
                onDismissResult = onDismissResult,
                details = details,
                onRename = { showRenameDialog = true },
                onShare = onShare,
                onExport = onExport,
                // Favorites Add/Remove (APP-670, spec §5): the composition rule + snackbar/undo live in
                // the scaffold; the badge on each tile reflects [favorites] reactively.
                favoriteIds = favorites,
                onSetFavorites = onSetFavorites,
                modifier = modifier.testTag("photos_screen"),
            ) {
                Column(Modifier.fillMaxSize().nestedScroll(filterBarCollapse.connection)) {
                    if (!selection.isActive) {
                        CollapsibleContent(visible = filterBarCollapse.visible) {
                            FormatFilterChips(selected = state.filter, onSelect = onFilterChange)
                        }
                    }
                    if (state.timeline.itemCount == 0) {
                        // Non-empty library, empty filtered result → filter-scoped empty state.
                        EmptyTabState(
                            icon = Icons.Outlined.Photo,
                            title = state.filter.emptyTitle(),
                            caption = "Nothing here for this filter. Switch to All to see everything.",
                        )
                    } else {
                        GalleryPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                            PhotosGrid(
                                timeline = state.timeline,
                                columns = columns,
                                selection = selection,
                                orderedIds = allOrderedIds,
                                favorites = favorites,
                                onColumnsChange = onColumnsChange,
                                onMediaClick = onMediaClick,
                                onToggle = onToggle,
                                onBeginSelect = onBeginSelect,
                                onDragSelect = onDragSelect,
                                onLoadWindow = onLoadWindow,
                            )
                        }
                    }
                }
            }

            if (showRenameDialog && singleSelected != null) {
                NameInputDialog(
                    title = "Rename",
                    label = "Name",
                    confirmLabel = "Rename",
                    initialValue = singleSelected.displayName,
                    validate = { FileNames.renameError(it, singleSelected.displayName) },
                    onConfirm = { name ->
                        onRenameSelected(singleSelected.id, name)
                        showRenameDialog = false
                        onClearSelection()
                    },
                    onDismiss = { showRenameDialog = false },
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

    if (showGroupSheet) {
        GroupBySheet(
            current = groupBy,
            onSelect = onGroupChange,
            onDismiss = { showGroupSheet = false },
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

private fun MediaFilter.emptyTitle(): String = when (this) {
    MediaFilter.ALL -> "No photos yet"
    MediaFilter.PHOTOS -> "No photos"
    MediaFilter.VIDEOS -> "No videos"
    MediaFilter.GIFS -> "No GIFs"
}

@Composable
private fun PhotosGrid(
    timeline: WindowedPhotosTimeline,
    columns: ColumnCount,
    selection: SelectionState<MediaId>,
    orderedIds: List<MediaId>,
    favorites: Set<MediaId>,
    onColumnsChange: (ColumnCount) -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    onToggle: (MediaId) -> Unit,
    onBeginSelect: (MediaId) -> Unit,
    onDragSelect: (MediaId, List<MediaId>) -> Unit,
    onLoadWindow: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val renderColumns = adaptiveColumns(columns, GridContent.MEDIA)
    val tileShape = JGalleryDimens.tileRadius(renderColumns)

    ThumbnailPrefetch(gridState = gridState, timeline = timeline)

    // Resolve a grid cell index to the media id under it (null for date headers or unloaded tiles).
    val idAtCell: (Int) -> MediaId? = { index ->
        timeline.tileItemAt(index)?.id
    }

    // Trigger window loads as the user scrolls. Also pre-fetch the next window ahead.
    val currentTimeline by rememberUpdatedState(timeline)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstCell ->
                val ordinal = currentTimeline.itemOrdinalAt(firstCell)
                    ?: currentTimeline.itemOrdinalAt(firstCell + 1)
                    ?: return@collect
                onLoadWindow(ordinal)
                // Pre-fetch the next window ahead of the scroll direction.
                onLoadWindow(ordinal + WindowedPhotosTimeline.WINDOW_SIZE)
            }
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
            columns = GridCells.Fixed(renderColumns.value),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            verticalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            modifier = Modifier.fillMaxSize().testTag("photos_grid"),
        ) {
            items(
                count = timeline.totalCells,
                key = { index -> timeline.keyAt(index) },
                span = { index ->
                    if (timeline.contentTypeAt(index) == "date_header") GridItemSpan(maxLineSpan)
                    else GridItemSpan(1)
                },
                contentType = { index -> timeline.contentTypeAt(index) },
            ) { index ->
                val reflow = Modifier.animateItem(placementSpec = GridReflowPlacementSpec)
                when (val cell = timeline.cellAt(index)) {
                    is PhotosCell.DateHeader -> GroupHeaderContent(label = cell.label, modifier = reflow)
                    is PhotosCell.Tile -> MediaTile(
                        modifier = reflow,
                        item = cell.item,
                        shape = tileShape,
                        columns = renderColumns.value,
                        selectionActive = selection.isActive,
                        selected = selection.isSelected(cell.item.id),
                        favorite = cell.item.id in favorites,
                        onClick = {
                            if (selection.isActive) onToggle(cell.item.id) else onMediaClick(cell.item)
                        },
                    )
                    null -> {
                        // Unloaded window — placeholder tile at tile dimensions.
                        Box(
                            modifier = reflow
                                .aspectRatio(1f)
                                .background(JGalleryColors.TilePlaceholder, tileShape),
                        )
                    }
                }
            }
        }

        StickyGroupHeader(gridState = gridState, timeline = timeline)

        GridFastScroller(
            gridState = gridState,
            bubbleLabel = timeline::bubbleLabel,
        )

        ScrollToTopFab(gridState = gridState, enabled = !selection.isActive)
    }
}

@Composable
private fun GroupHeaderContent(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun BoxScope.StickyGroupHeader(
    gridState: LazyGridState,
    timeline: WindowedPhotosTimeline,
) {
    val sectionStarts = timeline.sectionStarts
    if (sectionStarts.isEmpty()) return

    var headerHeightPx by remember { mutableIntStateOf(0) }

    val pinned by remember(timeline) {
        derivedStateOf {
            val firstIndex = gridState.firstVisibleItemIndex
            val currentHeaderCell = sectionStarts.lastOrNull { it <= firstIndex }
                ?: return@derivedStateOf null
            val label = (timeline.cellAt(currentHeaderCell) as? PhotosCell.DateHeader)?.label
                ?: return@derivedStateOf null
            val nextHeaderCell = sectionStarts.firstOrNull { it > firstIndex }
            val nextTop = nextHeaderCell?.let { cell ->
                gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == cell }?.offset?.y
            }
            val push = if (nextTop != null && nextTop < headerHeightPx) nextTop - headerHeightPx else 0
            PinnedHeader(label, push)
        }
    }

    pinned?.let { header ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = header.pushPx.toFloat() }
                .onSizeChanged { headerHeightPx = it.height }
                .background(MaterialTheme.colorScheme.background)
                .testTag("photos_sticky_header"),
        ) {
            GroupHeaderContent(label = header.label)
        }
    }
}

private data class PinnedHeader(val label: String, val pushPx: Int)

/**
 * Photos' adapter onto the shared windowed-grid prefetch (APP-722 P2). The two-pass crisp+coarse warm,
 * the fling gate and the bounded directional lookahead all live in [GridThumbnailPrefetch] now — Photos
 * consumes the same one pipeline every other grid does, no private copy. The windowed timeline resolves
 * a grid-adapter index to the tile item via its window cache ([WindowedPhotosTimeline.tileItemAt]);
 * indices whose window isn't loaded yet (or section headers) yield null and are skipped. The coarse
 * ring is enabled here (progressive two-pass) via [ThumbnailSizes.coarsePreviewEdgePx].
 */
@Composable
private fun ThumbnailPrefetch(
    gridState: LazyGridState,
    timeline: WindowedPhotosTimeline,
) {
    GridThumbnailPrefetch(
        gridState = gridState,
        itemCount = timeline.totalCells,
        coarseEdgePx = ThumbnailSizes.coarsePreviewEdgePx,
        modelAt = { index -> timeline.tileItemAt(index)?.thumbnailRequest() },
    )
}

@Composable
private fun MediaTile(
    item: MediaItem,
    shape: RoundedCornerShape,
    columns: Int,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
) {
    val scale = rememberTileSelectScale(selected)
    val isPano = item.isPanorama
    val badge = item.formatBadge
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.primaryContainer, shape)
            .clickable(onClick = onClick)
            // The clickable tile is the semantic element: it announces (and is addressable by) the
            // file name regardless of decode state. The inner preview drops its own description when
            // it falls back to a placeholder (APP-364), so anchoring the name here keeps the tile
            // stable for a11y and for interaction tests (APP-446). The favorite indicator is decorative,
            // so its state is merged into the tile's own label instead (APP-670, spec §6).
            .semantics {
                contentDescription = if (favorite) "${item.displayName}, Favorited" else item.displayName
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Shared-element "popup" open (APP-691 §1): the grid side of the photo that grows into
                // the full-screen viewer on tap and shrinks back here on close. Keyed on the media id so
                // it matches the viewer's image; a no-op until the app provides the transition scopes.
                .photoSharedElement(item.id.value)
                .tileSelectScale(scale)
                .clip(shape)
                .background(if (isPano) Color.Black else JGalleryColors.TilePlaceholder),
        ) {
            val thumbnailRequest = item.thumbnailRequest()
            MediaDecodeBox(
                model = thumbnailRequest,
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                contentDescription = null,
                contentScale = if (isPano) ContentScale.FillWidth else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                crossfade = false,
                // APP-709 progressive two-pass: if a warm coarse entry exists under this (size-agnostic)
                // key — seeded by the idle coarse-ring warm — paint it instantly while the crisp decode
                // lands. Only reads the memory cache, so the hot fling path pays no extra decode.
                placeholderMemoryCacheKey = thumbnailRequest.memoryCacheKey(),
                loadingColor = if (isPano) Color.Black else JGalleryColors.TilePlaceholder,
                placeholder = { MediaDecodeTilePlaceholder(it) },
            )
            if (badge != null) {
                FormatBadgeChip(
                    badge = badge,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            if (item.type == MediaType.VIDEO) {
                VideoOverlay(durationMillis = item.durationMillis, columns = columns)
            }
        }
        SelectionCheckBadge(selected = selected, active = selectionActive)
        // Favorited indicator (APP-670, spec §3): a passive corner badge — no control. Suppressed while
        // selection is active so the corner reads cleanly against the selection scrim/tick.
        if (!selectionActive) {
            FavoriteHeartBadge(favorite = favorite)
        }
    }
}
