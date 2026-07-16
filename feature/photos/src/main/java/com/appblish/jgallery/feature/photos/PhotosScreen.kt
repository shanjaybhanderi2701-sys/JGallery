package com.appblish.jgallery.feature.photos

import android.widget.Toast
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
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.model.formatBadge
import com.appblish.jgallery.core.model.isPanorama
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.format.MediaDecodeBox
import com.appblish.jgallery.core.ui.format.MediaDecodeTilePlaceholder
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.FormatBadgeChip
import com.appblish.jgallery.core.ui.component.EmptyTabState
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
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
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
    val context = LocalContext.current

    // Create-album outcomes (design G1-D7 §2 / C1-09): on success route into the new album's empty
    // "Add photos" prompt so it gets a cover; failures stay a toast. Mirrors the Albums tab.
    LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            when (result) {
                is PhotosCreateAlbumResult.Success -> onAlbumCreated(result.name)
                is PhotosCreateAlbumResult.Failure ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        onRefresh = viewModel::refresh,
        onColumnsChange = viewModel::setColumns,
        onFilterChange = viewModel::setFilter,
        onGroupChange = viewModel::setGroupBy,
        onSortChange = viewModel::setSort,
        onMediaClick = onMediaClick,
        onOpenSearch = onOpenSearch,
        onOpenTrash = onOpenTrash,
        onCreateAlbum = viewModel::createAlbum,
        onToggle = viewModel::toggleSelection,
        onBeginSelect = viewModel::beginSelection,
        onDragSelect = viewModel::dragSelectTo,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRunBulk = viewModel::runBulk,
        onRunBulkToNewAlbum = viewModel::runBulkToNewAlbum,
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
    onRefresh: () -> Unit = {},
    onMediaClick: (MediaItem) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    onCreateAlbum: (String) -> Unit = {},
    onToggle: (MediaId) -> Unit = {},
    onBeginSelect: (MediaId) -> Unit = {},
    onDragSelect: (MediaId, List<MediaId>) -> Unit = { _, _ -> },
    onSelectAll: (Collection<MediaId>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    onRunBulkToNewAlbum: (BulkAction, String) -> Unit = { _, _ -> },
    onCancelBulk: () -> Unit = {},
    onDismissResult: () -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // Collapse-on-scroll for the filter chip row (design G1-D8 item 4): reads scroll direction off the
    // grid's nested scroll and hides the chips on scroll-up, restores them on scroll-down.
    val filterBarCollapse = rememberCollapseOnScrollState()
    // Leaving selection mode restores the bar so it never re-appears already collapsed.
    LaunchedEffect(selection.isActive) {
        if (!selection.isActive) filterBarCollapse.reveal()
    }

    // Canonical top bar (design G1-D7 §1/§2): Search + 3-dot overflow only — the loose Photos
    // grid/group icons are gone. The overflow adopts the Albums item set (Sort / Column count /
    // Create album / Recycle bin), plus Group by (a Photos-only stream control from G1-10 that no
    // longer has a home on the bar). Sort/Column/Group open their sheets; the rest route out.
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
            ),
        )
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
            val tileItems = remember(state.timeline) {
                state.timeline.cells.mapNotNull { (it as? PhotosCell.Tile)?.item }
            }
            val tileIds = remember(tileItems) { tileItems.map { it.id } }
            // Item 11: aggregate Details for the current media selection (multi-safe, any N ≥ 1).
            val details = remember(selection.selected, tileItems) {
                if (selection.isActive) {
                    mediaSelectionDetails(tileItems.filter { selection.isSelected(it.id) })
                } else {
                    null
                }
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
                coverFor = { it.coverRequest() },
                onCreateNew = onRunBulkToNewAlbum,
                onCancel = onCancelBulk,
                onDismissResult = onDismissResult,
                details = details,
                modifier = modifier.testTag("photos_screen"),
            ) {
                // The nested-scroll connection sits on the ancestor of the grid so it sees each scroll
                // delta before the grid consumes it (it consumes nothing itself), driving the chip bar.
                Column(Modifier.fillMaxSize().nestedScroll(filterBarCollapse.connection)) {
                    // Item 3 (design C1-06): the format filter row, directly under the header. Hidden
                    // while selecting (the selection top bar owns that space). Collapse-on-scroll
                    // (design G1-D8 item 4): slides/fades away on scroll-up, returns on scroll-down.
                    if (!selection.isActive) {
                        CollapsibleContent(visible = filterBarCollapse.visible) {
                            FormatFilterChips(selected = state.filter, onSelect = onFilterChange)
                        }
                    }
                    if (tileIds.isEmpty()) {
                        // Non-empty library, empty filtered result → filter-scoped empty state (callout 5).
                        EmptyTabState(
                            icon = Icons.Outlined.Photo,
                            title = state.filter.emptyTitle(),
                            caption = "Nothing here for this filter. Switch to All to see everything.",
                        )
                    } else {
                        // Pull-to-refresh (design G1-D7 item 13): shared wrapper so every grid behaves
                        // identically. Wraps the scrollable directly so its nested-scroll drives the pull.
                        GalleryPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
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

/** Title for the filter-scoped empty state (design C1-06 callout 5). */
private fun MediaFilter.emptyTitle(): String = when (this) {
    MediaFilter.ALL -> "No photos yet"
    MediaFilter.PHOTOS -> "No photos"
    MediaFilter.VIDEOS -> "No videos"
    MediaFilter.GIFS -> "No GIFs"
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

    // Fix 4 — warm the next viewport ahead of the scroll so cold tiles are already decoded/cached by
    // the time they enter view (renders nothing itself).
    ThumbnailPrefetch(gridState = gridState, cells = timeline.cells)

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
                    is PhotosCell.DateHeader -> GroupHeaderContent(label = cell.label)
                    is PhotosCell.Tile -> MediaTile(
                        item = cell.item,
                        shape = tileShape,
                        columns = columns.value,
                        selectionActive = selection.isActive,
                        selected = selection.isSelected(cell.item.id),
                        onClick = {
                            if (selection.isActive) onToggle(cell.item.id) else onMediaClick(cell.item)
                        },
                    )
                }
            }
        }

        // Sticky section header (design G1-10): the current section's label pinned at the top, pushed
        // up by the next header as it arrives. Draws nothing for GroupBy.NONE (no sections).
        StickyGroupHeader(gridState = gridState, timeline = timeline)

        GridFastScroller(
            gridState = gridState,
            sectionStarts = timeline.sectionStarts,
            bubbleLabel = timeline::bubbleLabel,
        )

        // Item 2 (design C1-07): back-to-top FAB. Yields the corner to selection mode's bulk bar.
        ScrollToTopFab(gridState = gridState, enabled = !selection.isActive)
    }
}

/**
 * A full-width group header (design G1-10). Shared by the inline header cells and the pinned
 * [StickyGroupHeader] overlay so they render identically as one scrolls under the other.
 */
@Composable
private fun GroupHeaderContent(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.headlineSmall,
        color = JGalleryColors.Text,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/**
 * The sticky section header: the label of the section currently under the top of the viewport, pinned
 * there over an opaque background so tiles scroll beneath it. As the next section's inline header
 * climbs into the sticky band it pushes the pinned label up 1:1, so the swap reads as one continuous
 * header rather than a pop. Renders nothing when the stream carries no sections (GroupBy.NONE) or
 * before the grid has laid out.
 *
 * Reads [LazyGridState.layoutInfo] inside a [derivedStateOf] (the recommended pattern) so it only
 * recomposes when the pinned label or its push offset actually changes, not on every scroll pixel.
 */
@Composable
private fun BoxScope.StickyGroupHeader(
    gridState: LazyGridState,
    timeline: PhotosTimeline,
) {
    val sectionStarts = timeline.sectionStarts
    if (sectionStarts.isEmpty()) return

    // Measured height of the pinned header; the next header is only allowed to push once it enters
    // this band. Zero until first layout, which harmlessly means "no push yet".
    var headerHeightPx by remember { mutableIntStateOf(0) }

    val pinned by remember(timeline) {
        derivedStateOf {
            val firstIndex = gridState.firstVisibleItemIndex
            val currentHeaderCell = sectionStarts.lastOrNull { it <= firstIndex } ?: return@derivedStateOf null
            val label = (timeline.cells.getOrNull(currentHeaderCell) as? PhotosCell.DateHeader)?.label
                ?: return@derivedStateOf null
            // The next header, if it is currently on screen and within the sticky band, drives the push.
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
                .background(JGalleryColors.Background)
                .testTag("photos_sticky_header"),
        ) {
            GroupHeaderContent(label = header.label)
        }
    }
}

/** The pinned sticky-header snapshot: its label and how far up the incoming header is pushing it. */
private data class PinnedHeader(val label: String, val pushPx: Int)

/** The visible-viewport snapshot that drives [ThumbnailPrefetch]; deduped so only real scrolls fire. */
private data class PrefetchWindow(
    val firstVisible: Int,
    val lastVisible: Int,
    val visibleCount: Int,
    val tilePx: Int,
)

/**
 * Cold-cache first-load prefetch (APP-456, from JD device finding 2). Warms Coil's memory+disk caches
 * ahead of the grid so a large library's first pass is not decode-bound, while keeping visible tiles
 * ahead of prefetch on Fix 5's bounded, no-priority decode dispatcher. Two phases, driven off
 * [LazyGridState] and split by scroll state:
 *
 * - **During a scroll** — a bounded, direction-aware, nearest-first lookahead ([PrefetchPlanner.ahead])
 *   in the scroll direction. It stays modest ([PREFETCH_AHEAD_MAX]) so tiles actually entering view
 *   keep winning decode slots, and the prior batch is disposed on every new window, so decodes for
 *   tiles flung past are cancelled (a direction flip or a fresh viewport makes them stale).
 * - **Once the scroll settles** — a wider symmetric warm ([PrefetchPlanner.idleWarm]) in both
 *   directions. Aggressive is safe when idle because no visible tile competes for slots; the warm is
 *   disposed the instant scrolling resumes, so it never steals a decode from the next fling.
 *
 * Requests carry the visible tile pixel size so they hit the exact bucketed cache keys the grid asks
 * for. Renders nothing — enqueued requests only populate Coil's caches; they need no draw target. The
 * disk cache itself persists across launches (Coil `DiskCache` in `cacheDir`, 256 MB LRU), so this
 * cold pass is paid once per library, not once per launch.
 */
@Composable
private fun ThumbnailPrefetch(
    gridState: LazyGridState,
    cells: List<PhotosCell>,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val currentCells by rememberUpdatedState(cells)

    LaunchedEffect(gridState, imageLoader) {
        fun enqueueTiles(indices: List<Int>, tilePx: Int): List<Disposable> {
            val cellList = currentCells
            return indices.mapNotNull { index ->
                val tile = cellList.getOrNull(index) as? PhotosCell.Tile ?: return@mapNotNull null
                val request = ImageRequest.Builder(context)
                    .data(tile.item.thumbnailRequest())
                    .size(tilePx, tilePx)
                    .build()
                imageLoader.enqueue(request)
            }
        }

        coroutineScope {
            // Phase 1 — bounded directional lookahead while scrolling.
            launch {
                var lastFirstVisible = gridState.firstVisibleItemIndex
                var inFlight: List<Disposable> = emptyList()
                snapshotFlow {
                    val visible = gridState.layoutInfo.visibleItemsInfo
                    PrefetchWindow(
                        firstVisible = visible.firstOrNull()?.index ?: 0,
                        lastVisible = visible.lastOrNull()?.index ?: -1,
                        visibleCount = visible.size,
                        // Square tile edge — ignore full-span headers (their min dimension is row height).
                        tilePx = visible.maxOfOrNull { minOf(it.size.width, it.size.height) } ?: 0,
                    )
                }
                    .distinctUntilChanged()
                    .collect { window ->
                        if (window.lastVisible < 0 || window.tilePx <= 0) return@collect
                        val goingDown = window.firstVisible >= lastFirstVisible
                        lastFirstVisible = window.firstVisible

                        // Cancel the prior batch; those tiles are now on-screen or flung past.
                        inFlight.forEach { it.dispose() }

                        // ~1.5 viewports ahead, bounded — aggressive enough to stay ahead of a steady
                        // scroll without saturating the decode pool the visible tiles need.
                        val aheadCount = (window.visibleCount * 3 / 2)
                            .coerceIn(1, PREFETCH_AHEAD_MAX)
                        val indices = PrefetchPlanner.ahead(
                            firstVisible = window.firstVisible,
                            lastVisible = window.lastVisible,
                            goingDown = goingDown,
                            aheadCount = aheadCount,
                            itemCount = currentCells.size,
                        )
                        inFlight = enqueueTiles(indices, window.tilePx)
                    }
            }

            // Phase 2 — wider symmetric background warm once the scroll settles.
            launch {
                var warming: List<Disposable> = emptyList()
                snapshotFlow { gridState.isScrollInProgress }
                    .distinctUntilChanged()
                    .collect { scrolling ->
                        // Resuming a scroll cancels the idle warm so visible tiles reclaim the slots.
                        warming.forEach { it.dispose() }
                        warming = emptyList()
                        if (scrolling) return@collect

                        val visible = gridState.layoutInfo.visibleItemsInfo
                        val first = visible.firstOrNull()?.index ?: return@collect
                        val last = visible.lastOrNull()?.index ?: return@collect
                        val tilePx = visible.maxOfOrNull { minOf(it.size.width, it.size.height) } ?: 0
                        if (tilePx <= 0) return@collect

                        val radius = (visible.size * 2).coerceIn(1, IDLE_WARM_MAX)
                        val indices = PrefetchPlanner.idleWarm(
                            firstVisible = first,
                            lastVisible = last,
                            radius = radius,
                            itemCount = currentCells.size,
                        )
                        warming = enqueueTiles(indices, tilePx)
                    }
            }
        }
    }
}

// Bounded lookahead depths (tile counts). The active window stays small so foreground decodes win
// slots; the idle window is wider because nothing visible competes when the grid is at rest.
private const val PREFETCH_AHEAD_MAX = 60
private const val IDLE_WARM_MAX = 80

/**
 * One square grid tile. The model is a [thumbnailRequest] — never a raw uri/path — so the load is
 * boundary-routed, size-capped, and served from the E4 caches (memory hit = zero IO, zero decode).
 * In selection mode it insets (revealing the accent-tinted gap) and shows a check badge (spec §7.6).
 */
@Composable
private fun MediaTile(
    item: MediaItem,
    shape: RoundedCornerShape,
    columns: Int,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale = rememberTileSelectScale(selected)
    // Panoramas letterbox the full horizon on a dark cell instead of cropping to a square (W3-03);
    // every other format still crops to fill the tile so the grid geometry never reflows (§1).
    val isPano = item.isPanorama
    val badge = item.formatBadge
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(JGalleryColors.AccentSoft, shape)
            .clickable(onClick = onClick)
            // The clickable tile is the semantic element: it announces (and is addressable by) the
            // file name regardless of decode state. The inner preview drops its own description when
            // it falls back to a placeholder (APP-364), so anchoring the name here keeps the tile
            // stable for a11y and for interaction tests (APP-446).
            .semantics { contentDescription = item.displayName },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .tileSelectScale(scale)
                .clip(shape)
                .background(if (isPano) Color.Black else JGalleryColors.TilePlaceholder),
        ) {
            // Central §8 degrade hook: renders the cached thumbnail, or the D3 placeholder for a
            // format we can't decode — corrupt/zero-byte/unknown types never crash the grid (APP-364).
            MediaDecodeBox(
                model = item.thumbnailRequest(),
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                // Decorative: the enclosing clickable tile owns the file-name description (see above).
                contentDescription = null,
                // Panoramas letterbox the horizon (FillWidth) on the dark cell; all else crops (W3-03).
                contentScale = if (isPano) ContentScale.FillWidth else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Fix 3 — grid tiles pop in (no per-tile fade); the viewer keeps its crossfade.
                crossfade = false,
                // Plain neutral load surface (APP-457); panoramas letterbox on black so the fill
                // matches their dark cell instead of the neutral grey.
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
            // Item 8 (design C1-08): the shared centred play disc + radial scrim + duration pill,
            // drawn over the thumbnail so a video is unmistakable in the grid. Replaces the flat W1 pill.
            if (item.type == MediaType.VIDEO) {
                VideoOverlay(durationMillis = item.durationMillis, columns = columns)
            }
        }
        SelectionCheckBadge(selected = selected, active = selectionActive)
    }
}
