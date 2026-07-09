package com.appblish.jgallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryViewerTheme
import kotlinx.coroutines.launch

/**
 * Full-screen viewer (spec §5, design W1-08/09/10): swipe pager across the launch scope, image
 * zoom with pager-safe gesture priority, Media3 video playback, dark viewer-only chrome. All file
 * actions are Phase-G1 stubs — the real operations arrive in Wave 2 and only replace callbacks.
 */
@Composable
internal fun ViewerRoute(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ViewerScreen(state = state, playback = viewModel.playback, onBack = onBack)
}

@Composable
internal fun ViewerScreen(
    state: ViewerUiState,
    playback: PlaybackSources,
    onBack: () -> Unit,
) {
    JGalleryViewerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JGalleryColors.ViewerCanvas)
                .testTag("viewer_screen"),
        ) {
            when (state) {
                ViewerUiState.Loading -> Unit // black canvas for the (near-instant) first index read
                ViewerUiState.Empty -> EmptyViewer(onBack)
                is ViewerUiState.Ready -> ViewerPager(state, playback, onBack)
            }
        }
    }
}

@Composable
private fun ViewerPager(
    state: ViewerUiState.Ready,
    playback: PlaybackSources,
    onBack: () -> Unit,
) {
    val items by rememberUpdatedState(state.items)
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, state.items.lastIndex),
    ) { items.size }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onStubAction: (String) -> Unit = { action ->
        scope.launch { snackbarHostState.showSnackbar("$action arrives with file operations in Wave 2") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1, // neighbours pre-decode so swipes land on pixels, not blanks
            pageSpacing = 12.dp,
            key = { index -> items.getOrNull(index)?.id?.value ?: index },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            when (item.type) {
                MediaType.IMAGE -> ImagePage(
                    item = item,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
                MediaType.VIDEO -> VideoPage(
                    item = item,
                    createMediaSource = { playback.mediaSource(item) },
                    isSettledPage = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onChromeVisibleChange = { chromeVisible = it },
                )
            }
        }

        val currentItem = items.getOrNull(pagerState.currentPage)
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ViewerHeader(item = currentItem, onBack = onBack, onStubAction = onStubAction)
        }
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ViewerActionBar(
                onStubAction = onStubAction,
                onInfo = { currentItem?.let { infoItem = it } },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = JGalleryColors.ViewerSheet,
                contentColor = Color.White,
            )
        }

        infoItem?.let { item ->
            MediaInfoDialog(item = item, onDismiss = { infoItem = null })
        }
    }
}

/** Header (design W1-08): back, filename, favorite, rotate — the latter two are Wave-2 stubs. */
@Composable
private fun ViewerHeader(
    item: MediaItem?,
    onBack: () -> Unit,
    onStubAction: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .height(56.dp)
            .testTag("viewer_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = item?.displayName.orEmpty(),
            modifier = Modifier.weight(1f),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { onStubAction("Favorite") }) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Favorite", tint = Color.White)
        }
        IconButton(onClick = { onStubAction("Rotate") }) {
            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate", tint = Color.White)
        }
    }
}

/**
 * Bottom action bar (design W1-08/10). Share and Edit render disabled at 38% — deferred phases
 * with their slots reserved (design deviation #2). More opens the exact Phase-G1 overflow subset.
 */
@Composable
private fun ViewerActionBar(onStubAction: (String) -> Unit, onInfo: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .height(76.dp)
            .testTag("viewer_actions"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerAction(Icons.Filled.Share, "Share", Modifier.weight(1f), enabled = false) {}
        ViewerAction(Icons.Outlined.Delete, "Delete", Modifier.weight(1f)) { onStubAction("Delete") }
        ViewerAction(Icons.Outlined.DriveFileMove, "Move to", Modifier.weight(1f)) { onStubAction("Move to") }
        ViewerAction(Icons.Filled.Edit, "Edit", Modifier.weight(1f), enabled = false) {}
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ViewerAction(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = JGalleryColors.ViewerSheet,
            ) {
                // Exact Phase-G1 overflow subset (spec §5); deferred reference items are omitted
                // entirely, not shown disabled (design deviation #3).
                listOf("Copy to", "Move to", "Rename", "Set as", "Info").forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action, color = Color.White) },
                        modifier = Modifier.testTag("viewer_overflow_$action"),
                        onClick = {
                            menuOpen = false
                            // Info is fully wired (spec §5.1); the rest land with the E8-backed
                            // file-operation actions (APP-312 copy/move/rename/set-as).
                            if (action == "Info") onInfo() else onStubAction(action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Color.White else Color.White.copy(alpha = 0.38f)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(text = label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyViewer(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .testTag("viewer_empty"),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "This item is no longer available",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
