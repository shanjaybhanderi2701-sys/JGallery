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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.grid.rememberGridZoomState
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

private val AlbumDetailColumns = ColumnCount(3)

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
        selection = selection,
        bulk = bulk,
        destinations = destinations,
        onBack = onBack,
        onMediaClick = onMediaClick,
        onToggle = viewModel::toggleSelection,
        onBeginSelect = viewModel::beginSelection,
        onDragSelect = viewModel::dragSelectTo,
        onSelectAll = viewModel::selectAll,
        onClearSelection = viewModel::clearSelection,
        onRunBulk = viewModel::runBulk,
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
    selection: SelectionState<MediaId> = SelectionState(),
    bulk: BulkOperationUiState = BulkOperationUiState.Idle,
    destinations: List<Album> = emptyList(),
    onToggle: (MediaId) -> Unit = {},
    onBeginSelect: (MediaId) -> Unit = {},
    onDragSelect: (MediaId, List<MediaId>) -> Unit = { _, _ -> },
    onSelectAll: (Collection<MediaId>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    onCancelBulk: () -> Unit = {},
    onDismissResult: () -> Unit = {},
    onAddPhotos: () -> Unit = {},
    onOpenCamera: () -> Unit = {},
) {
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
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    when (state) {
        AlbumDetailUiState.Loading -> Column(modifier.fillMaxSize().testTag("album_detail_screen")) {
            header(); SkeletonGrid(columns = AlbumDetailColumns)
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
                onCancel = onCancelBulk,
                onDismissResult = onDismissResult,
                modifier = modifier.testTag("album_detail_screen"),
            ) {
                AlbumDetailGrid(
                    items = state.items,
                    orderedIds = ids,
                    selection = selection,
                    onMediaClick = onMediaClick,
                    onToggle = onToggle,
                    onBeginSelect = onBeginSelect,
                    onDragSelect = onDragSelect,
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailGrid(
    items: List<MediaItem>,
    orderedIds: List<MediaId>,
    selection: SelectionState<MediaId>,
    onMediaClick: (MediaItem) -> Unit,
    onToggle: (MediaId) -> Unit,
    onBeginSelect: (MediaId) -> Unit,
    onDragSelect: (MediaId, List<MediaId>) -> Unit,
) {
    val zoom = rememberGridZoomState(initialColumns = AlbumDetailColumns)
    val gridState = zoom.gridState
    val tileShape = JGalleryDimens.tileRadius(zoom.columns)
    val idAtCell: (Int) -> MediaId? = { index -> items.getOrNull(index)?.id }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gridPinchColumns(zoom)
            .selectableGridDrag(
                gridState = gridState,
                onSelectStart = { index -> idAtCell(index)?.let(onBeginSelect) },
                onDragOverIndex = { index -> idAtCell(index)?.let { onDragSelect(it, orderedIds) } },
            ),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns.value),
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
                        VideoOverlay(durationMillis = item.durationMillis, columns = zoom.columns.value)
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
