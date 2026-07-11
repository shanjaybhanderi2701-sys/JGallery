package com.appblish.jgallery.feature.albums

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
    onAlbumClick: (Album) -> Unit = {},
    viewModel: AlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val albumOp by viewModel.albumOp.collectAsStateWithLifecycle()
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
        onDeleteAlbum = viewModel::deleteAlbum,
        onTogglePin = viewModel::togglePin,
        onAlbumOpCancel = viewModel::cancelAlbumOp,
        onAlbumOpDone = viewModel::dismissAlbumOp,
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
    filter: MediaFilter = MediaFilter.ALL,
    onFilterChange: (MediaFilter) -> Unit = {},
    destinations: List<Album> = emptyList(),
    albumOp: AlbumOpUiState? = null,
    onRenameAlbum: (Album, String) -> Unit = { _, _ -> },
    onCopyAlbum: (Album, String) -> Unit = { _, _ -> },
    onMoveAlbum: (Album, String) -> Unit = { _, _ -> },
    onDeleteAlbum: (Album) -> Unit = {},
    onTogglePin: (Album) -> Unit = {},
    onAlbumOpCancel: () -> Unit = {},
    onAlbumOpDone: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
) {
    var showColumnSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    // The album whose per-album action menu / dialog is open, plus which dialog (null = closed).
    var actionTarget by remember { mutableStateOf<Album?>(null) }
    var openDialog by remember { mutableStateOf<AlbumDialog?>(null) }

    Column(modifier = modifier.fillMaxSize().testTag("albums_screen")) {
        GalleryTabHeader(title = "Albums") {
            AlbumsOverflowMenu(
                onSortBy = { showSortSheet = true },
                onColumnCount = { showColumnSheet = true },
                onCreateAlbum = { showCreateDialog = true },
            )
        }

        // Item 3 (design C1-06): the same format filter row as the Photos tab — one mental model
        // across both. Shown once the library has albums; filters which albums surface.
        if (state is AlbumsUiState.Content) {
            FormatFilterChips(selected = filter, onSelect = onFilterChange)
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
                    onAlbumClick = onAlbumClick,
                    onAlbumLongClick = { actionTarget = it },
                )
            }
        }
    }

    // Per-album action menu (spec §7, §11): long-press an album → Rename / Copy / Move / Delete.
    // The menu shows until an action selects a dialog; the target survives so the dialog can use it.
    actionTarget?.let { target ->
        if (openDialog == null) {
            AlbumActionMenu(
                album = target,
                onTogglePin = { onTogglePin(target); actionTarget = null },
                onRename = { openDialog = AlbumDialog.Rename },
                onCopy = { openDialog = AlbumDialog.Copy },
                onMove = { openDialog = AlbumDialog.Move },
                onDelete = { openDialog = AlbumDialog.Delete },
                onDismiss = { actionTarget = null },
            )
        }
    }

    val target = actionTarget
    when (openDialog) {
        AlbumDialog.Rename -> if (target != null) NameInputDialog(
            title = "Rename album",
            label = "Album name",
            confirmLabel = "Rename",
            initialValue = target.name,
            onConfirm = { name -> onRenameAlbum(target, name); openDialog = null; actionTarget = null },
            onDismiss = { openDialog = null; actionTarget = null },
        )
        AlbumDialog.Copy -> if (target != null) DestinationPickerSheet(
            title = "Copy “${target.name}” to",
            albums = destinations,
            excludeBucketId = target.bucketId,
            onPick = { dest -> onCopyAlbum(target, dest); openDialog = null; actionTarget = null },
            onDismiss = { openDialog = null; actionTarget = null },
        )
        AlbumDialog.Move -> if (target != null) DestinationPickerSheet(
            title = "Move “${target.name}” to",
            albums = destinations,
            excludeBucketId = target.bucketId,
            onPick = { dest -> onMoveAlbum(target, dest); openDialog = null; actionTarget = null },
            onDismiss = { openDialog = null; actionTarget = null },
        )
        AlbumDialog.Delete -> if (target != null) DeleteAlbumDialog(
            album = target,
            onConfirm = { onDeleteAlbum(target); openDialog = null; actionTarget = null },
            onDismiss = { openDialog = null; actionTarget = null },
        )
        null -> Unit
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

/** Title for the filter-scoped empty Albums state (design C1-06 callout 5). */
private fun MediaFilter.albumsEmptyTitle(): String = when (this) {
    MediaFilter.ALL -> "No albums yet"
    MediaFilter.PHOTOS -> "No photo albums"
    MediaFilter.VIDEOS -> "No video albums"
    MediaFilter.GIFS -> "No GIF albums"
}

/** Which per-album dialog is open (spec §7, §11 album-entity ops). */
private enum class AlbumDialog { Rename, Copy, Move, Delete }

/**
 * Long-press action sheet for a single album. Every album can be pinned/unpinned (spec C4 item 6);
 * the entity ops Rename / Copy / Move / Delete (spec §7, §11) apply only to real device folders — the
 * synthetic Recent/Video smart albums expose Pin only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumActionMenu(
    album: Album,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("album_action_menu")) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleLarge,
                color = JGalleryColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            AlbumActionRow(
                label = if (album.pinned) "Unpin" else "Pin",
                tag = "album_action_pin",
                onClick = onTogglePin,
            )
            if (album.kind == AlbumKind.DEVICE_FOLDER) {
                AlbumActionRow("Rename", "album_action_rename", onRename)
                AlbumActionRow("Copy", "album_action_copy", onCopy)
                AlbumActionRow("Move", "album_action_move", onMove)
                AlbumActionRow("Delete", "album_action_delete", onDelete)
            }
        }
    }
}

@Composable
private fun AlbumActionRow(label: String, tag: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = JGalleryColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(tag),
    )
}

/** Confirm moving a whole album to the Trash (spec §7.5 — restorable, so a single confirm). */
@Composable
private fun DeleteAlbumDialog(album: Album, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_album_dialog"),
        title = { Text("Delete album?") },
        text = { Text("“${album.name}” and its ${album.itemCount} item(s) will be moved to the Trash. You can restore them within 30 days.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_album_confirm")) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
