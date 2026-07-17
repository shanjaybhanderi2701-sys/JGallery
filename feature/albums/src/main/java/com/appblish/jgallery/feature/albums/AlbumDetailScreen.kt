package com.appblish.jgallery.feature.albums

import android.content.ActivityNotFoundException
import android.widget.Toast
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.ui.platform.LocalContext
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
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.GalleryMenuItem
import com.appblish.jgallery.core.ui.component.GalleryOverflowMenu
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
import com.appblish.jgallery.core.ui.grouping.MediaCell
import com.appblish.jgallery.core.ui.grouping.MediaSections
import com.appblish.jgallery.core.ui.grouping.GroupSectionHeader
import com.appblish.jgallery.core.ui.grouping.StickyMediaHeader
import com.appblish.jgallery.core.ui.grouping.buildMediaSections
import java.time.LocalDate
import java.time.ZoneId
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.BulkOperationUiState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
import com.appblish.jgallery.core.ui.selection.SelectionScaffold
import com.appblish.jgallery.core.ui.share.MediaShareRequest
import com.appblish.jgallery.core.ui.share.ShareIntents
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
    onOpenTrash: () -> Unit = {},
    onAlbumCreated: (name: String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bulk by viewModel.bulk.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val viewSettings by viewModel.viewSettings.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    // Create-album from the shared 3-dot menu (APP-499): route success into the new album's empty
    // "Add photos" prompt (identical to the Photos/Albums tabs); a failure stays a toast.
    LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            when (result) {
                is CreateAlbumResult.Success -> onAlbumCreated(result.name)
                is CreateAlbumResult.Failure ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Share outcomes (G2 · APP-541): fire the system share sheet for the resolved content uris, or
    // toast when nothing shareable remained. Identical to the Photos tab.
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { request ->
            when (request) {
                is MediaShareRequest.Ready -> context.launchShareSheet(request.uris, request.mimeType)
                MediaShareRequest.Empty ->
                    Toast.makeText(context, "Nothing left to share", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // "Save a copy" folder pick (G2 · APP-549, Security gate APP-542 §5): the SAF tree picker returns a
    // transient, user-scoped grant — stream into it immediately, never persist. Null = user backed out
    // (no-op; selection preserved). Identical to the Photos tab.
    val exportFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri -> if (treeUri != null) viewModel.exportSelected(treeUri) }

    AlbumDetailScreen(
        title = viewModel.title,
        sourceBucketId = viewModel.bucketId,
        state = state,
        viewSettings = viewSettings,
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        onMediaClick = onMediaClick,
        onSortChange = viewModel::setSort,
        onColumnsChange = viewModel::setColumns,
        onGroupChange = viewModel::setGroupBy,
        onScopeChange = viewModel::setScope,
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
        onOpenCamera = { viewModel.requestCapture(CaptureKind.PHOTO) },
        onShare = viewModel::shareSelected,
        onExport = { exportFolderPicker.launch(null) },
        modifier = modifier,
    )
}

/**
 * Fire the system share sheet for [uris] (G2 · APP-541) — the shared launch path with the Photos tab.
 * The uris are the §1.6-sanctioned MediaStore `content://` uris; [ShareIntents] grants each read-only
 * + temporary via ClipData. Guarded by `runCatching` so a device with no share target no-ops.
 */
private fun android.content.Context.launchShareSheet(uris: List<android.net.Uri>, mimeType: String) {
    if (uris.isEmpty()) return
    val intent = ShareIntents.buildSendIntent(uris, mimeType)
    runCatching { startActivity(android.content.Intent.createChooser(intent, "Share")) }
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
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSortChange: (SortSpec) -> Unit = {},
    onColumnsChange: (ColumnCount) -> Unit = {},
    onGroupChange: (GroupBy) -> Unit = {},
    onScopeChange: (ViewScope) -> Unit = {},
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
    onAddPhotos: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
    onShare: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    var showSortSheet by remember { mutableStateOf(false) }
    var showColumnSheet by remember { mutableStateOf(false) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
            // Shared 3-dot context menu (APP-499): the album's overflow is the SAME styled
            // GalleryOverflowMenu as the home/Photos tab, with the identical option set (Sort by /
            // Group by / Column count / Create album / Recycle Bin) so it can't drift. The in-album
            // "apply to this album only vs all albums" scope toggle (G1-9) renders in the same styled
            // surface via the menu's footer slot.
            GalleryOverflowMenu(
                testTag = "album_detail_overflow_action",
                items = listOf(
                    GalleryMenuItem(
                        label = "Sort by",
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        testTag = "album_detail_menu_sort_by",
                        onClick = { showSortSheet = true },
                    ),
                    GalleryMenuItem(
                        label = "Group by",
                        icon = Icons.Outlined.DateRange,
                        testTag = "album_detail_menu_group_by",
                        onClick = { showGroupSheet = true },
                    ),
                    GalleryMenuItem(
                        label = "Column count",
                        icon = Icons.Outlined.GridView,
                        testTag = "album_detail_menu_column_count",
                        onClick = { showColumnSheet = true },
                    ),
                    GalleryMenuItem(
                        label = "Create album",
                        icon = Icons.Outlined.CreateNewFolder,
                        testTag = "album_detail_menu_create_album",
                        onClick = { showCreateDialog = true },
                    ),
                    GalleryMenuItem(
                        label = "Recycle Bin",
                        icon = Icons.Outlined.Delete,
                        testTag = "album_detail_menu_recycle_bin",
                        onClick = onOpenTrash,
                        dividerBefore = true,
                    ),
                ),
                footer = { dismiss ->
                    ScopeSection(
                        scope = viewSettings.scope,
                        onScopeChange = { onScopeChange(it); dismiss() },
                    )
                },
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
                // Share (G2 · APP-541): multi-safe overflow entry, identical to the Photos tab.
                onShare = onShare,
                // Save a copy (G2 · APP-549): multi-safe overflow entry → SAF folder pick then export.
                onExport = onExport,
                modifier = modifier.testTag("album_detail_screen"),
            ) {
                // Pull-to-refresh (design G1-D7 item 13): shared wrapper, identical to the other grids.
                GalleryPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                    AlbumDetailGrid(
                        items = state.items,
                        orderedIds = ids,
                        columns = viewSettings.columns,
                        groupBy = viewSettings.groupBy,
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
    // Group by (APP-499): the same sheet the Photos tab opens, so Day/Month/Year/None reads identically.
    if (showGroupSheet) {
        GroupBySheet(
            current = viewSettings.groupBy,
            onSelect = onGroupChange,
            onDismiss = { showGroupSheet = false },
        )
    }
    // Create album (APP-499): the same NameInputDialog the Photos/Albums tabs use.
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

/**
 * The "apply to this album only vs. all albums" scope toggle (G1-9, design APP-465 TB-03), rendered
 * inside the shared overflow menu's footer slot (APP-499) so it inherits the styled menu surface. This
 * is the one album-specific control the flat home menu has no equivalent for; everything above it in
 * the menu is the identical shared item set.
 */
@Composable
private fun ScopeSection(
    scope: ViewScope,
    onScopeChange: (ViewScope) -> Unit,
) {
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
        onClick = { onScopeChange(ViewScope.THIS_ALBUM) },
        testTag = "album_detail_scope_this",
    )
    ScopeRow(
        label = "All albums",
        selected = scope == ViewScope.ALL_ALBUMS,
        onClick = { onScopeChange(ViewScope.ALL_ALBUMS) },
        testTag = "album_detail_scope_all",
    )
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
    groupBy: GroupBy,
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

    // Group-by sectioning (APP-499): the shared core:ui grouping, identical to the Photos timeline, so
    // the album grid grows sticky Day/Month/Year headers when the shared menu's Group-by is set. NONE
    // yields a flat run with no headers — byte-identical to the pre-APP-499 flat grid.
    val zone = remember { ZoneId.systemDefault() }
    val sections = remember(items, groupBy) {
        buildMediaSections(items = items, groupBy = groupBy, zone = zone, today = LocalDate.now(zone))
    }
    val cells = sections.cells
    // Resolve a grid adapter index to the media id under it (null for section headers), so range-drag
    // and long-press only fire on tiles even with headers interleaved.
    val idAtCell: (Int) -> MediaId? = { index ->
        (cells.getOrNull(index) as? MediaCell.Tile)?.item?.id
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
            modifier = Modifier.fillMaxSize().testTag("album_detail_grid"),
        ) {
            items(
                count = cells.size,
                key = { index -> cells[index].key },
                span = { index ->
                    when (cells[index]) {
                        is MediaCell.Header -> GridItemSpan(maxLineSpan)
                        is MediaCell.Tile -> GridItemSpan(1)
                    }
                },
                contentType = { index ->
                    when (cells[index]) {
                        is MediaCell.Header -> "section_header"
                        is MediaCell.Tile -> "media_tile"
                    }
                },
            ) { index ->
                // Pinch-release column swap slides every cell to its new slot over the shared settle
                // spring (APP-519) — a real layout animation instead of the old instant reposition.
                val reflow = Modifier.animateItem(placementSpec = GridReflowPlacementSpec)
                when (val cell = cells[index]) {
                    is MediaCell.Header -> GroupSectionHeader(label = cell.label, modifier = reflow)
                    is MediaCell.Tile -> {
                        val item = cell.item
                        val scale = rememberTileSelectScale(selection.isSelected(item.id))
                        Box(
                            modifier = reflow
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
                            // Item 8 (design C1-08): shared video play-icon overlay so videos are
                            // distinguishable in the album grid too — same disc/scrim/duration pill.
                            if (item.type == MediaType.VIDEO) {
                                VideoOverlay(durationMillis = item.durationMillis, columns = columns.value)
                            }
                            SelectionCheckBadge(
                                selected = selection.isSelected(item.id),
                                active = selection.isActive,
                            )
                        }
                    }
                }
            }
        }

        // Sticky section header (APP-499): the current group's label pinned at the top, identical to the
        // Photos tab. Draws nothing for GroupBy.NONE (no sections).
        StickyMediaHeader(gridState = gridState, sections = sections, testTag = "album_detail_sticky_header")

        // APP-466: flat-grid fast-scroller (position bubble) so a large album is grabbable — the last
        // of the shared grid set the in-album grid was missing.
        GridFastScroller(gridState = gridState, itemCount = cells.size)

        // Item 2 (design C1-07): back-to-top FAB; hidden while a selection is active.
        ScrollToTopFab(gridState = gridState, enabled = !selection.isActive)
    }
}
