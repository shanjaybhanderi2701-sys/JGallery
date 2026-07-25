package com.appblish.jgallery.feature.albums

import android.widget.Toast
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
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.thumbs.thumbnailRequest
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.component.VideoOverlay
import com.appblish.jgallery.core.ui.grid.GridFastScroller
import com.appblish.jgallery.core.ui.grid.GridReflowPlacementSpec
import com.appblish.jgallery.core.ui.grid.ScrollToTopFab
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.grid.gridPinchColumns
import com.appblish.jgallery.core.ui.grid.rememberGridZoomState
import com.appblish.jgallery.core.ui.selection.SelectionCheckBadge
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

private val AddToAlbumColumns = ColumnCount(3)

/**
 * The "Add photos" picker for a new album (design C1-09): a whole-library, newest-first grid where every
 * tap toggles selection, with a sticky "Add N" bar that copies the chosen items into the album via the
 * §1.6 create-and-fill seam. On confirm the copy runs and the screen pops back; the album then renders
 * on the Albums home with a cover.
 */
@Composable
fun AddToAlbumScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddToAlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.addEvents.collect { result ->
            val message = when {
                result.added > 0 && result.failed == 0 -> "Added ${result.added} to ${viewModel.albumName}"
                result.added > 0 -> "Added ${result.added}, ${result.failed} failed"
                else -> "Couldn't add photos"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    AddToAlbumScreen(
        title = viewModel.albumName,
        state = state,
        selected = selected,
        adding = adding,
        onBack = onDone,
        onToggle = viewModel::toggle,
        onConfirm = viewModel::confirm,
        modifier = modifier,
    )
}

/** Stateless body — instrumented/unit UI tests drive this without Hilt. */
@Composable
fun AddToAlbumScreen(
    title: String,
    state: AddToAlbumUiState,
    selected: Set<MediaId>,
    onBack: () -> Unit,
    onToggle: (MediaId) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    adding: Boolean = false,
) {
    val header: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("add_to_album_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = "Add to $title",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }

    Column(modifier.fillMaxSize().testTag("add_to_album_screen")) {
        header()
        when (state) {
            AddToAlbumUiState.Loading -> SkeletonGrid(columns = AddToAlbumColumns)
            AddToAlbumUiState.Empty -> EmptyTabState(
                icon = Icons.Outlined.PhotoLibrary,
                title = "No photos to add",
                caption = "There are no photos or videos on this device yet.",
                modifier = Modifier.testTag("add_to_album_empty"),
            )
            is AddToAlbumUiState.Content -> Box(Modifier.fillMaxSize()) {
                // FAB yields the bottom corner to the sticky "Add N" bar once anything is picked.
                AddToAlbumGrid(
                    items = state.items,
                    selected = selected,
                    onToggle = onToggle,
                    fabEnabled = selected.isEmpty(),
                )
                // Sticky "Add N" bar (design C1-09): appears once >=1 item is picked; drives the copy.
                if (selected.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = onConfirm,
                            enabled = !adding,
                            modifier = Modifier.testTag("add_to_album_confirm"),
                        ) {
                            Icon(
                                Icons.Outlined.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.height(18.dp),
                            )
                            Text(
                                text = if (adding) "Adding…" else "Add ${selected.size}",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToAlbumGrid(
    items: List<MediaItem>,
    selected: Set<MediaId>,
    onToggle: (MediaId) -> Unit,
    fabEnabled: Boolean,
) {
    // APP-466: the picker is a whole-library grid, so it gets the full shared set too — pinch-zoom
    // columns, the flat-grid fast-scroller (position bubble), and the back-to-top FAB.
    val zoom = rememberGridZoomState(initialColumns = AddToAlbumColumns)
    val gridState = zoom.gridState
    val tileShape = JGalleryDimens.tileRadius(zoom.columns)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(zoom.columns.value),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            verticalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
            modifier = Modifier.fillMaxSize().gridPinchColumns(zoom).testTag("add_to_album_grid"),
        ) {
            items(items, key = { it.id.value }) { item ->
                val isSelected = item.id in selected
                Box(
                    modifier = Modifier
                        // Pinch-release column swap slides each tile to its new slot (APP-519).
                        .animateItem(placementSpec = GridReflowPlacementSpec)
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.primaryContainer, tileShape)
                        .clickable { onToggle(item.id) },
                ) {
                    AsyncImage(
                        model = item.thumbnailRequest(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(tileShape)
                            .background(JGalleryColors.TilePlaceholder),
                    )
                    if (item.type == MediaType.VIDEO) {
                        VideoOverlay(durationMillis = item.durationMillis, columns = zoom.columns.value)
                    }
                    SelectionCheckBadge(selected = isSelected, active = true)
                }
            }
        }

        GridFastScroller(gridState = gridState, itemCount = items.size)
        ScrollToTopFab(gridState = gridState, enabled = fabEnabled)
    }
}
