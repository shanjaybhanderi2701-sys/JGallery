package com.appblish.jgallery.feature.albums

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.AlbumKind
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.appblish.jgallery.core.ui.component.CollapsibleContent
import com.appblish.jgallery.core.ui.component.FormatFilterChips
import com.appblish.jgallery.core.ui.component.rememberCollapseOnScrollState
import com.appblish.jgallery.core.ui.component.GalleryMenuItem
import com.appblish.jgallery.core.ui.component.GalleryTopBar
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.component.SortBySheet
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.ui.selection.AlbumOpProgressDialog
import com.appblish.jgallery.core.ui.selection.AlbumOpUiState
import com.appblish.jgallery.core.ui.selection.AlbumOpVerb
import com.appblish.jgallery.core.ui.selection.MoveDestinationSheet
import com.appblish.jgallery.core.ui.selection.SelectionAction
import com.appblish.jgallery.core.ui.selection.SelectionActionBar
import com.appblish.jgallery.core.ui.selection.SelectionDetails
import com.appblish.jgallery.core.ui.selection.SelectionDetailsDialog
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.selection.SelectionTopBar
import com.appblish.jgallery.core.ui.selection.formatDateRange
import com.appblish.jgallery.core.ui.grid.GalleryPullToRefresh
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import kotlinx.coroutines.flow.flowOf

/**
 * Albums tab — the default tab (spec §2/§3, design a04): album grid with cover thumbnails + item
 * counts from the cached index. The overflow (spec §3) hosts Sort By, Column count and Create album
 * (spec §6). Covers are coverRequest models, so they ride the same E4 cache as grid tiles. Same
 * structural perf properties as the Photos grid: stable keys, fixed geometry, precomputed state,
 * pinch column morph 2–6 persisted per tab.
 */
@Composable
fun AlbumsScreen(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album, MediaFilter) -> Unit = { _, _ -> },
    onAlbumCreated: (name: String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val albumOp by viewModel.albumOp.collectAsStateWithLifecycle()
    val albumSelection by viewModel.albumSelection.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val shownAlbums = (state as? AlbumsUiState.Content)?.albums.orEmpty()

    // The album whose media backs the "Set as cover" picker (null = picker closed). Loading the media
    // lazily off the chosen bucket keeps the tab itself off a per-album media query until it's needed.
    var coverBucket by remember { mutableStateOf<String?>(null) }
    val coverMedia by remember(coverBucket) {
        coverBucket?.let(viewModel::albumMedia) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(emptyList())

    // Create-album outcomes (spec §6, design C1-09): on success route straight into the new album's
    // empty "Add photos" prompt so a fresh album gets a cover and appears on the Albums home once it
    // holds >=1 item (APP-416). Failures stay a toast.
    LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            when (result) {
                is CreateAlbumResult.Success -> onAlbumCreated(result.name)
                is CreateAlbumResult.Failure ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Surface rename/delete-album outcomes (spec §7) as a toast. Copy/Move use the progress dialog.
    LaunchedEffect(viewModel) {
        viewModel.albumActionEvents.collect { result ->
            val message = when (result) {
                is AlbumActionResult.Success -> result.message
                is AlbumActionResult.Failure -> result.reason
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    AlbumsScreen(
        state = state,
        columns = columns,
        sort = sort,
        filter = filter,
        destinations = destinations,
        albumOp = albumOp,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onColumnsChange = viewModel::setColumns,
        onSortChange = viewModel::setSort,
        onFilterChange = viewModel::setFilter,
        onCreateAlbum = viewModel::createAlbum,
        onRenameAlbum = viewModel::renameAlbum,
        onTogglePin = viewModel::togglePin,
        onAlbumOpCancel = viewModel::cancelAlbumOp,
        onAlbumOpDone = viewModel::dismissAlbumOp,
        // Carry the active format chip into album detail so opening a folder yields only matching media.
        onAlbumClick = { album -> onAlbumClick(album, filter) },
        onOpenSearch = onOpenSearch,
        onOpenTrash = onOpenTrash,
        albumSelection = albumSelection,
        onAlbumLongPress = { viewModel.beginAlbumSelection(it.bucketId) },
        onAlbumDragSelect = viewModel::dragOverAlbum,
        onAlbumSelectToggle = { viewModel.toggleAlbumSelection(it.bucketId) },
        onSelectAllAlbums = viewModel::selectAllAlbums,
        onClearAlbumSelection = viewModel::clearAlbumSelection,
        onDeleteSelected = { viewModel.deleteSelectedAlbums(shownAlbums) },
        onPinSelected = { viewModel.pinSelectedAlbums(shownAlbums) },
        onCopySelected = { dest -> viewModel.copySelectedAlbums(shownAlbums, dest) },
        onMoveSelected = { dest -> viewModel.moveSelectedAlbums(shownAlbums, dest) },
        onCopySelectedToNew = { name -> viewModel.copySelectedAlbumsToNew(shownAlbums, name) },
        onMoveSelectedToNew = { name -> viewModel.moveSelectedAlbumsToNew(shownAlbums, name) },
        onSetAlbumCover = { album, id -> viewModel.setAlbumCover(album.bucketId, id) },
        coverPickerMedia = coverMedia,
        onCoverPickerOpen = { coverBucket = it.bucketId },
        onCoverPickerClose = { coverBucket = null },
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
    filter: MediaFilter = MediaFilter.ALL,
    onFilterChange: (MediaFilter) -> Unit = {},
    destinations: List<Album> = emptyList(),
    albumOp: AlbumOpUiState? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRenameAlbum: (Album, String) -> Unit = { _, _ -> },
    onTogglePin: (Album) -> Unit = {},
    onAlbumOpCancel: () -> Unit = {},
    onAlbumOpDone: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    // Multi-select action bar (G1-11, APP-471): the selection top bar swaps for the tab header while
    // selecting; a bottom action bar carries the multi-safe ops (Pin/Copy/Move/Delete) and a ⋮ overflow
    // the single-only ops (Rename/Set-cover/Details), disabled when >1 is selected.
    albumSelection: SelectionState<String> = SelectionState(),
    onAlbumLongPress: (Album) -> Unit = {},
    // Drag range-select (item 6): sweep from the anchor to the bucket under the finger within [ordered].
    onAlbumDragSelect: (bucketId: String, ordered: List<String>) -> Unit = { _, _ -> },
    onAlbumSelectToggle: (Album) -> Unit = {},
    onSelectAllAlbums: (List<String>) -> Unit = {},
    onClearAlbumSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onPinSelected: () -> Unit = {},
    onCopySelected: (destinationBucketId: String) -> Unit = {},
    onMoveSelected: (destinationBucketId: String) -> Unit = {},
    onCopySelectedToNew: (name: String) -> Unit = {},
    onMoveSelectedToNew: (name: String) -> Unit = {},
    onBrowseFolders: () -> Unit = {},
    onSetAlbumCover: (Album, MediaId) -> Unit = { _, _ -> },
    coverPickerMedia: List<MediaItem> = emptyList(),
    onCoverPickerOpen: (Album) -> Unit = {},
    onCoverPickerClose: () -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteSelected by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    // Which Copy/Move batch is waiting on a destination pick (null = none).
    var pendingBatch by remember { mutableStateOf<BatchOp?>(null) }

    // Collapse-on-scroll for the filter chip row (design G1-D8 item 4): scroll-up hides the chips,
    // scroll-down restores them — same behavior as the Photos tab, one shared primitive.
    val filterBarCollapse = rememberCollapseOnScrollState()
    LaunchedEffect(albumSelection.isActive) {
        if (!albumSelection.isActive) filterBarCollapse.reveal()
    }

    val allAlbums = (state as? AlbumsUiState.Content)?.albums.orEmpty()
    val selectedAlbums = allAlbums.filter { it.bucketId in albumSelection.selected }
    val singleSelected = selectedAlbums.singleOrNull()
    val selectedFolders = selectedAlbums.filter { it.kind == AlbumKind.DEVICE_FOLDER }
    val allSelected = allAlbums.isNotEmpty() && allAlbums.all { it.bucketId in albumSelection.selected }

    // Back exits selection first (spec §7.6 parity with the media grids).
    BackHandler(enabled = albumSelection.isActive) { onClearAlbumSelection() }

    // nestedScroll on the ancestor Column lets the collapse controller read the album grid's scroll
    // direction before the grid consumes it (it consumes nothing), driving the chip-bar show/hide.
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(filterBarCollapse.connection)
            .testTag("albums_screen"),
    ) {
        if (albumSelection.isActive) {
            SelectionTopBar(
                count = albumSelection.count,
                allSelected = allSelected,
                onClose = onClearAlbumSelection,
                onSelectAll = { onSelectAllAlbums(allAlbums.map { it.bucketId }) },
                onDeselectAll = onClearAlbumSelection,
            )
        } else {
            // Canonical top bar (design G1-D7 §1/§2): Search + styled 3-dot overflow, shared with the
            // Photos tab. Search moved off the tab bar (C1-01 item 10) → header action on both tabs.
            GalleryTopBar(
                title = "Albums",
                onSearch = onOpenSearch,
                searchTestTag = "albums_search_action",
                overflowTestTag = "albums_overflow_action",
                overflowItems = listOf(
                    GalleryMenuItem(
                        label = "Sort by",
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        testTag = "albums_menu_sort_by",
                        onClick = { showSortSheet = true },
                    ),
                    GalleryMenuItem(
                        label = "Column count",
                        icon = Icons.Outlined.GridView,
                        testTag = "albums_menu_column_count",
                        onClick = { showColumnSheet = true },
                    ),
                    GalleryMenuItem(
                        label = "Create album",
                        icon = Icons.Outlined.CreateNewFolder,
                        testTag = "albums_menu_create_album",
                        onClick = { showCreateDialog = true },
                    ),
                    // Recycle Bin re-homed here (C1-01 item 10): destructive-adjacent, so a divider above.
                    GalleryMenuItem(
                        label = "Recycle Bin",
                        icon = Icons.Outlined.Delete,
                        testTag = "albums_menu_recycle_bin",
                        onClick = onOpenTrash,
                        dividerBefore = true,
                    ),
                ),
            )

            // Item 3 (design C1-06): the same format filter row as the Photos tab — one mental model
            // across both. Shown once the library has albums; filters which albums surface. Hidden while
            // selecting (the selection bar owns that space), mirroring the Photos grid.
            if (state is AlbumsUiState.Content) {
                // Collapse-on-scroll (design G1-D8 item 4): slides/fades away on scroll-up, returns on
                // scroll-down. The weight(1f) grid box below eases into the freed space — no jump.
                CollapsibleContent(visible = filterBarCollapse.visible) {
                    FormatFilterChips(selected = filter, onSelect = onFilterChange)
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            when (state) {
                AlbumsUiState.Loading -> SkeletonGrid(columns = columns)
                AlbumsUiState.Empty -> EmptyTabState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "No albums yet",
                    caption = "Folders with photos or videos will show up here.",
                )
                is AlbumsUiState.Content -> if (state.albums.isEmpty()) {
                    // Non-empty library, no albums match the active filter (design C1-06 callout 5).
                    EmptyTabState(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = filter.albumsEmptyTitle(),
                        caption = "No albums have this kind of media. Switch to All to see everything.",
                    )
                } else {
                    // Pull-to-refresh (design G1-D7 item 13): shared wrapper, identical to the other grids.
                    GalleryPullToRefresh(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                        AlbumCoverGrid(
                            albums = state.albums,
                            columns = columns,
                            onColumnsChange = onColumnsChange,
                            // Long-press enters multi-select; a tap toggles once selecting, else opens (APP-467).
                            onAlbumClick = { album ->
                                if (albumSelection.isActive) onAlbumSelectToggle(album) else onAlbumClick(album)
                            },
                            selectedBucketIds = albumSelection.selected,
                            // Items 5 & 6: the grid container owns long-press + drag range-select, so a
                            // long-press holds (no click rollback) and a sweep extends the selection.
                            onBeginSelect = { bucketId ->
                                allAlbums.firstOrNull { it.bucketId == bucketId }?.let(onAlbumLongPress)
                            },
                            onDragSelect = onAlbumDragSelect,
                        )
                    }
                }
            }
        }

        if (albumSelection.isActive) {
            SelectionActionBar(
                selectionCount = albumSelection.count,
                // Pin acts on any selection; Copy/Move/Delete only make sense when it holds real folders.
                multiActions = buildList {
                    add(SelectionAction.PIN)
                    if (selectedFolders.isNotEmpty()) {
                        add(SelectionAction.COPY)
                        add(SelectionAction.MOVE)
                        add(SelectionAction.DELETE)
                    }
                },
                // Details is multi-safe (aggregate over the selection); Rename/Set-cover stay single-only.
                overflowActions = listOf(SelectionAction.DETAILS, SelectionAction.RENAME, SelectionAction.SET_COVER),
                // Rename/Set-cover are folder-entity ops; Details reads any selection (incl. smart albums).
                isSingleActionEnabled = { action ->
                    when (action) {
                        SelectionAction.RENAME, SelectionAction.SET_COVER ->
                            singleSelected?.kind == AlbumKind.DEVICE_FOLDER
                        else -> true
                    }
                },
                onAction = { action ->
                    when (action) {
                        SelectionAction.PIN -> onPinSelected()
                        SelectionAction.COPY -> pendingBatch = BatchOp.COPY
                        SelectionAction.MOVE -> pendingBatch = BatchOp.MOVE
                        SelectionAction.DELETE -> showDeleteSelected = true
                        SelectionAction.RENAME -> showRename = true
                        SelectionAction.SET_COVER -> singleSelected?.let { onCoverPickerOpen(it); showCoverPicker = true }
                        SelectionAction.DETAILS -> showDetails = true
                    }
                },
            )
        }
    }

    // Rename (single folder). Confirming renames the entity and exits selection; dismiss keeps it.
    if (showRename && singleSelected != null) {
        NameInputDialog(
            title = "Rename album",
            label = "Album name",
            confirmLabel = "Rename",
            initialValue = singleSelected.name,
            onConfirm = { name -> onRenameAlbum(singleSelected, name); showRename = false; onClearAlbumSelection() },
            onDismiss = { showRename = false },
        )
    }

    // Copy/Move the selected folders to a picked destination (works for one or many). D4-03: the last
    // DestinationPickerSheet consumer now uses the shared cover-thumbnail MoveDestinationSheet, so every
    // Copy/Move surface (viewer, bulk selection, whole-album) speaks one sheet. Its "New album" tile
    // FLATTENS the selected folders' media into one new album (Architect ruling APP-480), mirroring the
    // merge that picking an existing album already performs. The operand noun is "albums" and the count
    // is the selected-folder count — never a bucket-expanded item count (C1).
    pendingBatch?.let { op ->
        val verb = if (op == BatchOp.COPY) AlbumOpVerb.COPY else AlbumOpVerb.MOVE
        MoveDestinationSheet(
            verb = verb,
            itemCount = selectedFolders.size,
            itemNoun = if (selectedFolders.size == 1) "album" else "albums",
            createSubtitle = "The selected albums' media become its cover + contents",
            albums = destinations,
            coverFor = { it.coverRequest() },
            excludeBucketId = singleSelected?.bucketId,
            onPick = { dest ->
                pendingBatch = null
                if (op == BatchOp.COPY) onCopySelected(dest) else onMoveSelected(dest)
            },
            onCreateNew = { name ->
                pendingBatch = null
                if (op == BatchOp.COPY) onCopySelectedToNew(name) else onMoveSelectedToNew(name)
            },
            onBrowseFolders = {
                pendingBatch = null
                onBrowseFolders()
            },
            onDismiss = { pendingBatch = null },
        )
    }

    // Batch delete confirm (works for one or many selected folders → restorable Trash).
    if (showDeleteSelected) {
        DeleteSelectedAlbumsDialog(
            count = selectedFolders.size,
            onConfirm = { showDeleteSelected = false; onDeleteSelected() },
            onDismiss = { showDeleteSelected = false },
        )
    }

    // Album details (item 11): multi-safe — a read-only summary of the whole album selection (one album
    // → its name/type/updated; many → aggregate album + item counts and the updated range).
    if (showDetails && selectedAlbums.isNotEmpty()) {
        SelectionDetailsDialog(details = albumSelectionDetails(selectedAlbums), onDismiss = { showDetails = false })
    }

    // Set as cover (single folder): pick a member as the album's cover (G1-11).
    if (showCoverPicker && singleSelected != null) {
        CoverPickerSheet(
            albumName = singleSelected.name,
            media = coverPickerMedia,
            onPick = { id ->
                onSetAlbumCover(singleSelected, id)
                showCoverPicker = false
                onCoverPickerClose()
            },
            onDismiss = { showCoverPicker = false; onCoverPickerClose() },
        )
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

    // Item 13 (C1-04): a whole-album Copy/Move runs behind this determinate, cancellable progress
    // dialog and resolves to a success/partial/cancelled summary — no fire-and-forget spinner.
    albumOp?.let { op ->
        AlbumOpProgressDialog(
            state = op,
            onCancel = onAlbumOpCancel,
            onDone = onAlbumOpDone,
        )
    }
}

/** Title for the filter-scoped empty Albums state (design C1-06 callout 5). */
private fun MediaFilter.albumsEmptyTitle(): String = when (this) {
    MediaFilter.ALL -> "No albums yet"
    MediaFilter.PHOTOS -> "No photo albums"
    MediaFilter.VIDEOS -> "No video albums"
    MediaFilter.GIFS -> "No GIF albums"
}

/** Which Copy/Move batch is waiting on a destination pick (G1-11 multi Copy/Move). */
private enum class BatchOp { COPY, MOVE }

/**
 * Read-only album-selection summary (G1-D7 item 11, multi-safe): a single album shows its name / item
 * count / kind / last-updated date; a multi-selection aggregates album + item counts and the updated
 * date range across the selection.
 */
private fun albumSelectionDetails(albums: List<Album>): SelectionDetails {
    val single = albums.singleOrNull()
    if (single != null) {
        return SelectionDetails(
            title = single.name,
            rows = listOf(
                "Items" to single.itemCount.toString(),
                "Type" to single.kind.detailsLabel(),
                "Last updated" to formatDateRange(single.newestItemMillis, single.newestItemMillis),
            ),
        )
    }
    val totalItems = albums.sumOf { it.itemCount }
    val dated = albums.map { it.newestItemMillis }.filter { it > 0L }
    return SelectionDetails(
        title = "${albums.size} albums",
        rows = listOf(
            "Albums" to albums.size.toString(),
            "Total items" to totalItems.toString(),
            "Updated" to formatDateRange(dated.minOrNull() ?: 0L, dated.maxOrNull() ?: 0L),
        ),
    )
}

private fun AlbumKind.detailsLabel(): String = when (this) {
    AlbumKind.DEVICE_FOLDER -> "Folder"
    AlbumKind.RECENT -> "Recent (all media)"
    AlbumKind.VIDEO -> "Video (all videos)"
}

/** Confirm moving the selected albums to the Trash (spec §7.5 — restorable, so a single confirm). */
@Composable
private fun DeleteSelectedAlbumsDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val label = if (count == 1) "this album" else "these $count albums"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_album_dialog"),
        title = { Text(if (count == 1) "Delete album?" else "Delete albums?") },
        text = { Text("Moving $label and their items to the Trash. You can restore them within 30 days.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_album_confirm")) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
