package com.appblish.jgallery.feature.albums

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.component.ColumnCountSheet
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.FormatFilterChips
import com.appblish.jgallery.core.ui.component.GalleryTabHeader
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.component.SortBySheet
import com.appblish.jgallery.core.ui.selection.AlbumOpProgressDialog
import com.appblish.jgallery.core.ui.selection.AlbumOpUiState
import com.appblish.jgallery.core.ui.selection.DestinationPickerSheet
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.theme.JGalleryColors

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
    val context = LocalContext.current

    val shownAlbums = (state as? AlbumsUiState.Content)?.albums.orEmpty()

    // Create-album outcomes (spec §6, design C1-09): on success route straight into the new album's
    // empty "Add photos" prompt so a fresh album gets a cover and appears on the Albums home once it
    // holds >=1 item (APP-416). Failures stay a toast.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.createAlbumEvents.collect { result ->
            when (result) {
                is CreateAlbumResult.Success -> onAlbumCreated(result.name)
                is CreateAlbumResult.Failure ->
                    Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Surface rename/delete-album outcomes (spec §7) as a toast. Copy/Move use the progress dialog.
    androidx.compose.runtime.LaunchedEffect(viewModel) {
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
        onColumnsChange = viewModel::setColumns,
        onSortChange = viewModel::setSort,
        onFilterChange = viewModel::setFilter,
        onCreateAlbum = viewModel::createAlbum,
        onRenameAlbum = viewModel::renameAlbum,
        onCopyAlbum = viewModel::copyAlbum,
        onMoveAlbum = viewModel::moveAlbum,
        onTogglePin = viewModel::togglePin,
        onAlbumOpCancel = viewModel::cancelAlbumOp,
        onAlbumOpDone = viewModel::dismissAlbumOp,
        // Carry the active format chip into album detail so opening a folder yields only matching media.
        onAlbumClick = { album -> onAlbumClick(album, filter) },
        onOpenSearch = onOpenSearch,
        onOpenTrash = onOpenTrash,
        albumSelection = albumSelection,
        onAlbumLongPress = { viewModel.beginAlbumSelection(it.bucketId) },
        onAlbumSelectToggle = { viewModel.toggleAlbumSelection(it.bucketId) },
        onSelectAllAlbums = viewModel::selectAllAlbums,
        onClearAlbumSelection = viewModel::clearAlbumSelection,
        onDeleteSelected = { viewModel.deleteSelectedAlbums(shownAlbums) },
        onPinSelected = { viewModel.pinSelectedAlbums(shownAlbums) },
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
    onRenameAlbum: (Album, String) -> Unit = { _, _ -> },
    onCopyAlbum: (Album, String) -> Unit = { _, _ -> },
    onMoveAlbum: (Album, String) -> Unit = { _, _ -> },
    onTogglePin: (Album) -> Unit = {},
    onAlbumOpCancel: () -> Unit = {},
    onAlbumOpDone: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenTrash: () -> Unit = {},
    // Multi-select (G1-8, APP-467): long-press enters, tap toggles; the batch ops that don't need the
    // single-op progress dialog (Delete → Trash, Pin) live in the selection bar, the rest come with the
    // G1-11 action bar. Single-album Rename/Copy/Move stay reachable when exactly one is selected.
    albumSelection: SelectionState<String> = SelectionState(),
    onAlbumLongPress: (Album) -> Unit = {},
    onAlbumSelectToggle: (Album) -> Unit = {},
    onSelectAllAlbums: (List<String>) -> Unit = {},
    onClearAlbumSelection: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onPinSelected: () -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // The album whose single-album dialog is open (only when exactly one is selected), plus which
    // dialog (null = closed). Delete goes through the batch confirm below instead.
    var openDialog by remember { mutableStateOf<AlbumDialog?>(null) }
    var showDeleteSelected by remember { mutableStateOf(false) }

    val selectedAlbums = (state as? AlbumsUiState.Content)?.albums.orEmpty()
        .filter { it.bucketId in albumSelection.selected }
    val singleSelected = selectedAlbums.singleOrNull()

    // Back exits selection first (spec §7.6 parity with the media grids).
    BackHandler(enabled = albumSelection.isActive) { onClearAlbumSelection() }

    Column(modifier = modifier.fillMaxSize().testTag("albums_screen")) {
        if (albumSelection.isActive) {
            AlbumSelectionBar(
                count = albumSelection.count,
                canActOnFolders = selectedAlbums.any { it.kind == AlbumKind.DEVICE_FOLDER },
                single = singleSelected,
                onClose = onClearAlbumSelection,
                onSelectAll = {
                    onSelectAllAlbums((state as? AlbumsUiState.Content)?.albums.orEmpty().map { it.bucketId })
                },
                onPin = onPinSelected,
                onDelete = { showDeleteSelected = true },
                onRename = { openDialog = AlbumDialog.Rename },
                onCopy = { openDialog = AlbumDialog.Copy },
                onMove = { openDialog = AlbumDialog.Move },
            )
        } else {
            GalleryTabHeader(title = "Albums") {
                // Search moved off the tab bar (C1-01 item 10) → header action on both tabs.
                IconButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.testTag("albums_search_action"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = JGalleryColors.Text,
                    )
                }
                AlbumsOverflowMenu(
                    onSortBy = { showSortSheet = true },
                    onColumnCount = { showColumnSheet = true },
                    onCreateAlbum = { showCreateDialog = true },
                    onOpenTrash = onOpenTrash,
                )
            }

            // Item 3 (design C1-06): the same format filter row as the Photos tab — one mental model
            // across both. Shown once the library has albums; filters which albums surface. Hidden while
            // selecting (the selection bar owns that space), mirroring the Photos grid.
            if (state is AlbumsUiState.Content) {
                FormatFilterChips(selected = filter, onSelect = onFilterChange)
            }
        }

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
                AlbumCoverGrid(
                    albums = state.albums,
                    columns = columns,
                    onColumnsChange = onColumnsChange,
                    // Long-press enters multi-select; a tap toggles once selecting, else opens (APP-467).
                    onAlbumClick = { album ->
                        if (albumSelection.isActive) onAlbumSelectToggle(album) else onAlbumClick(album)
                    },
                    onAlbumLongClick = onAlbumLongPress,
                    selectedBucketIds = albumSelection.selected,
                )
            }
        }
    }

    // Single-album Rename / Copy / Move from the selection bar (only offered when exactly one album is
    // selected). Delete uses the batch confirm below. Dismissing leaves the selection intact.
    when (openDialog) {
        AlbumDialog.Rename -> if (singleSelected != null) NameInputDialog(
            title = "Rename album",
            label = "Album name",
            confirmLabel = "Rename",
            initialValue = singleSelected.name,
            onConfirm = { name -> onRenameAlbum(singleSelected, name); openDialog = null; onClearAlbumSelection() },
            onDismiss = { openDialog = null },
        )
        AlbumDialog.Copy -> if (singleSelected != null) DestinationPickerSheet(
            title = "Copy “${singleSelected.name}” to",
            albums = destinations,
            excludeBucketId = singleSelected.bucketId,
            onPick = { dest -> onCopyAlbum(singleSelected, dest); openDialog = null; onClearAlbumSelection() },
            onDismiss = { openDialog = null },
        )
        AlbumDialog.Move -> if (singleSelected != null) DestinationPickerSheet(
            title = "Move “${singleSelected.name}” to",
            albums = destinations,
            excludeBucketId = singleSelected.bucketId,
            onPick = { dest -> onMoveAlbum(singleSelected, dest); openDialog = null; onClearAlbumSelection() },
            onDismiss = { openDialog = null },
        )
        AlbumDialog.Delete, null -> Unit
    }

    // Batch delete confirm (works for one or many selected folders → restorable Trash).
    if (showDeleteSelected) {
        DeleteSelectedAlbumsDialog(
            count = selectedAlbums.count { it.kind == AlbumKind.DEVICE_FOLDER },
            onConfirm = { showDeleteSelected = false; onDeleteSelected() },
            onDismiss = { showDeleteSelected = false },
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

/** Albums overflow (spec §3): Sort By, Column count, Create album. */
@Composable
private fun AlbumsOverflowMenu(
    onSortBy: () -> Unit,
    onColumnCount: () -> Unit,
    onCreateAlbum: () -> Unit,
    onOpenTrash: () -> Unit = {},
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
            // Recycle Bin re-homed here (C1-01 item 10): the Collections tab is now the Albums grid, so
            // the retired CollectionsScreen's only live utility (Trash) moves to this overflow.
            DropdownMenuItem(
                text = { Text("Recycle Bin") },
                onClick = { expanded = false; onOpenTrash() },
                modifier = Modifier.testTag("albums_menu_recycle_bin"),
            )
        }
    }
}

/** Title for the filter-scoped empty Albums state (design C1-06 callout 5). */
private fun MediaFilter.albumsEmptyTitle(): String = when (this) {
    MediaFilter.ALL -> "No albums yet"
    MediaFilter.PHOTOS -> "No photo albums"
    MediaFilter.VIDEOS -> "No video albums"
    MediaFilter.GIFS -> "No GIF albums"
}

/** Which single-album dialog is open, offered only when exactly one album is selected (spec §7, §11). */
private enum class AlbumDialog { Rename, Copy, Move, Delete }

/**
 * Album multi-select top bar (design C1-01, APP-467): replaces the tab header while selecting. Close
 * exits, "N selected" reads the count, Select-all grabs every shown album. The batch ops that don't
 * need the single-op progress dialog live here — **Pin** (toggles the selection) and **Delete** (→
 * restorable Trash, only when the selection contains real device folders). Single-album Rename / Copy /
 * Move are offered when exactly one folder is selected; the fuller action bar (Share, multi Copy/Move)
 * lands with G1-11.
 */
@Composable
private fun AlbumSelectionBar(
    count: Int,
    canActOnFolders: Boolean,
    single: Album?,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
) {
    val singleFolder = single?.takeIf { it.kind == AlbumKind.DEVICE_FOLDER }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(JGalleryColors.Surface)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .testTag("album_selection_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("album_selection_close")) {
            Icon(Icons.Outlined.Close, contentDescription = "Exit selection", tint = JGalleryColors.Text)
        }
        Text(
            text = "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = JGalleryColors.Text,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .testTag("album_selection_count"),
        )
        // Rename only makes sense for a single folder (renames one entity).
        if (singleFolder != null) {
            IconButton(onClick = onRename, modifier = Modifier.testTag("album_selection_rename")) {
                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename", tint = JGalleryColors.Text)
            }
            IconButton(onClick = onCopy, modifier = Modifier.testTag("album_selection_copy")) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = JGalleryColors.Text)
            }
            IconButton(onClick = onMove, modifier = Modifier.testTag("album_selection_move")) {
                Icon(Icons.Outlined.DriveFileMove, contentDescription = "Move", tint = JGalleryColors.Text)
            }
        }
        IconButton(onClick = onPin, modifier = Modifier.testTag("album_selection_pin")) {
            Icon(Icons.Outlined.PushPin, contentDescription = "Pin", tint = JGalleryColors.Text)
        }
        if (canActOnFolders) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("album_selection_delete")) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = JGalleryColors.Text)
            }
        }
        IconButton(onClick = onSelectAll, modifier = Modifier.testTag("album_selection_select_all")) {
            Icon(Icons.Outlined.SelectAll, contentDescription = "Select all", tint = JGalleryColors.Text)
        }
    }
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
