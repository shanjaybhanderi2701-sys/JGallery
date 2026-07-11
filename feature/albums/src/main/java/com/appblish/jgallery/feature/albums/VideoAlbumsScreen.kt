package com.appblish.jgallery.feature.albums

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.component.EmptyTabState
import com.appblish.jgallery.core.ui.grid.SkeletonGrid
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * The Video smart album's folder-wise grouping (spec C4 items 4 & 5): an "All Videos" card followed by
 * one card per folder that holds videos. Tapping a card opens the album-detail media grid scoped to
 * videos — All Videos across the whole library, or one folder's videos. Reuses the shared
 * [AlbumCoverGrid] so density/pinch behave exactly like the Albums tab.
 */
@Composable
fun VideoAlbumsScreen(
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoAlbumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val columns by viewModel.columns.collectAsStateWithLifecycle()
    VideoAlbumsScreen(
        state = state,
        columns = columns,
        onColumnsChange = viewModel::setColumns,
        onBack = onBack,
        onAlbumClick = onAlbumClick,
        modifier = modifier,
    )
}

/** Stateless body — drivable without Hilt for instrumented tests. */
@Composable
fun VideoAlbumsScreen(
    state: VideoAlbumsUiState,
    columns: ColumnCount,
    onColumnsChange: (ColumnCount) -> Unit,
    onBack: () -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().testTag("video_albums_screen")) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("video_albums_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JGalleryColors.Text)
            }
            Text(
                text = AlbumsCatalog.VIDEO_NAME,
                color = JGalleryColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        when (state) {
            VideoAlbumsUiState.Loading -> SkeletonGrid(columns = columns)
            VideoAlbumsUiState.Empty -> EmptyTabState(
                icon = Icons.Outlined.Videocam,
                title = "No videos yet",
                caption = "Videos on your device will be grouped here.",
            )
            is VideoAlbumsUiState.Content -> AlbumCoverGrid(
                albums = state.albums,
                columns = columns,
                onColumnsChange = onColumnsChange,
                onAlbumClick = onAlbumClick,
                gridTestTag = "video_albums_grid",
            )
        }
    }
}
