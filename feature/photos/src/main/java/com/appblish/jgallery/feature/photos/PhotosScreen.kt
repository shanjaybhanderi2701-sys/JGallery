package com.appblish.jgallery.feature.photos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.formatBadge
import com.appblish.jgallery.core.model.isPanorama
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.format.MediaDecodeBox
import com.appblish.jgallery.core.ui.format.MediaDecodeTilePlaceholder
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.FormatBadgeChip
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.FormatFilterChips
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.BulkOperationUiState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
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
    viewModel: PhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    PhotosScreen(
        state = state,
        columns = columns,
        filter = filter,
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        onColumnsChange = viewModel::setColumns,
        onFilterChange = viewModel::setFilter,
        onMediaClick = onMediaClick,
        onOpenSearch = onOpenSearch,
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
    filter: MediaFilter = MediaFilter.ALL,
    onFilterChange: (MediaFilter) -> Unit = {},
    selection: SelectionState<MediaId> = SelectionState(),
    bulk: BulkOperationUiState = BulkOperationUiState.Idle,
    destinations: List<Album> = emptyList(),
    onMediaClick: (MediaItem) -> Unit = {},
    onOpenSearch: () -> Unit = {},
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
            // Search moved off the tab bar (C1-01 item 10) → header action on both tabs.
            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier.testTag("photos_search_action"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = JGalleryColors.Text,
                )
            }
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
                Column(Modifier.fillMaxSize()) {
                    // Item 3 (design C1-06): the format filter row, directly under the header. Hidden
                    // while selecting (the selection top bar owns that space).
                    if (!selection.isActive) {
                        FormatFilterChips(selected = state.filter, onSelect = onFilterChange)
                    }
                    if (tileIds.isEmpty()) {
                        // Non-empty library, empty filtered result → filter-scoped empty state (callout 5).
                        EmptyTabState(
                            icon = Icons.Outlined.Photo,
                            title = state.filter.emptyTitle(),
                            caption = "Nothing here for this filter. Switch to All to see everything.",
                        )
                    } else {
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

    if (showColumnSheet) {
        ColumnCountSheet(
            current = columns,
            onSelect = onColumnsChange,
            onDismiss = { showColumnSheet = false },
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
                    is PhotosCell.DateHeader -> Text(
                        text = cell.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = JGalleryColors.Text,
                        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
                    )
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

        GridFastScroller(
            gridState = gridState,
            sectionStarts = timeline.sectionStarts,
            bubbleLabel = timeline::bubbleLabel,
        )

        // Item 2 (design C1-07): back-to-top FAB. Yields the corner to selection mode's bulk bar.
        ScrollToTopFab(gridState = gridState, enabled = !selection.isActive)
    }
}

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
