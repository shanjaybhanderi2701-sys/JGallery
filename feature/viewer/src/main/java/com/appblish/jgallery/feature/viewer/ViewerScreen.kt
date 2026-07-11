package com.appblish.jgallery.feature.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.View
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.selection.DestinationPickerSheet
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryViewerTheme
import kotlinx.coroutines.launch

/** The single-item file actions the viewer runs, bundled so the pager takes one param, not six. */
internal data class ViewerActionHandlers(
    val onCopyTo: (id: MediaId, bucketId: String) -> Unit,
    val onMoveTo: (id: MediaId, bucketId: String) -> Unit,
    val onRename: (id: MediaId, newName: String) -> Unit,
    val onDelete: (id: MediaId) -> Unit,
    val onSetAs: (id: MediaId) -> Unit,
    /** Hand an undecodable video to another app (W3-05 "Open with", §8). Resolves via §1.6 viewUri. */
    val onOpenWith: (id: MediaId) -> Unit,
    val onResultShown: () -> Unit,
)

/** Which flavour of the shared destination picker is open. */
private enum class PickerMode { COPY, MOVE }

/**
 * Full-screen viewer (spec §5, design W1-08/09/10): swipe pager across the launch scope, image
 * zoom with pager-safe gesture priority, Media3 video playback, dark viewer-only chrome. The overflow
 * + bottom-bar file actions (Copy/Move/Rename/Set-as/Delete/Info) run through the §7 E8 core via the
 * `:core:index` operations facade (W2-E12). Favourite / Rotate / Share / Edit stay deferred stubs.
 */
@Composable
internal fun ViewerRoute(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val actionState by viewModel.action.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        // A resolved boundary uri → launch the system "Set as" (ACTION_ATTACH_DATA) chooser (spec §7.4).
        viewModel.setAsUri.collect { uri -> context.launchSetAs(uri) }
    }
    LaunchedEffect(viewModel) {
        // Resolved boundary uri for an unplayable video → hand it to another app (§8 W3-05 "Open with").
        viewModel.openWithUri.collect { uri -> context.launchOpenWith(uri) }
    }
    ViewerScreen(
        state = state,
        playback = viewModel.playback,
        destinations = destinations,
        actionState = actionState,
        handlers = ViewerActionHandlers(
            onCopyTo = viewModel::copyTo,
            onMoveTo = viewModel::moveTo,
            onRename = viewModel::rename,
            onDelete = viewModel::delete,
            onSetAs = viewModel::setAs,
            onOpenWith = viewModel::openWith,
            onResultShown = viewModel::dismissActionResult,
        ),
        onBack = onBack,
    )
}

@Composable
internal fun ViewerScreen(
    state: ViewerUiState,
    playback: PlaybackSources,
    destinations: List<Album>,
    actionState: ViewerActionUiState,
    handlers: ViewerActionHandlers,
    onBack: () -> Unit,
) {
    JGalleryViewerTheme {
        // C1-02 (item 11): the viewer is an immersive, distraction-free canvas for as long as this
        // route is on screen — dark status bar (light icons over media) + hidden system nav bar.
        ImmersiveViewerEffect()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(JGalleryColors.ViewerCanvas)
                .testTag("viewer_screen"),
        ) {
            when (state) {
                ViewerUiState.Loading -> Unit // black canvas for the (near-instant) first index read
                ViewerUiState.Empty -> EmptyViewer(onBack)
                is ViewerUiState.Ready ->
                    ViewerPager(state, playback, destinations, actionState, handlers, onBack)
            }
        }
    }
}

/**
 * Immersive window setup for the viewer route (C1-02, item 11). Scoped to the viewer only via
 * [DisposableEffect]: on enter it draws the media edge-to-edge behind a **dark status bar** (light
 * icons, `isAppearanceLightStatusBars = false`) and **hides the system navigation bar** sticky-
 * immersive (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`); on exit it restores the app's light status bar
 * and re-shows the nav bar in one step, no flicker. The redlines (callouts #1/#4) keep the status bar
 * visible with light icons — only the nav bar is hidden — so every other screen keeps the light bar.
 * Edge-to-edge itself is already on from `enableEdgeToEdge()` in the Activity.
 */
@Composable
private fun ImmersiveViewerEffect() {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.findActivity()?.window ?: return
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false // light (white) icons over the dark media
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.navigationBars())
            controller.isAppearanceLightStatusBars = previousLightStatusBars
        }
    }
}

/** Walk the [ContextWrapper] chain to the hosting [Activity] (Compose's context may be wrapped). */
private fun View.findActivity(): Activity? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Fire the system "Set as" chooser (wallpaper / contact photo). The receiver reads via a granted uri. */
private fun Context.launchSetAs(uri: Uri) {
    val attach = Intent(Intent.ACTION_ATTACH_DATA).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        setDataAndType(uri, "image/*")
        putExtra("mimeType", "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // No handler on this device (or a non-Activity context) shouldn't crash the viewer.
    runCatching { startActivity(Intent.createChooser(attach, "Set as")) }
}

/**
 * Fire the system "Open with" chooser for a video the on-device codecs can't play (W3-05, §8). The
 * uri is the §1.6-resolved `content://`; a granted read flag lets the chosen player stream it.
 */
private fun Context.launchOpenWith(uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { startActivity(Intent.createChooser(view, "Open with")) }
}

@Composable
private fun ViewerPager(
    state: ViewerUiState.Ready,
    playback: PlaybackSources,
    destinations: List<Album>,
    actionState: ViewerActionUiState,
    handlers: ViewerActionHandlers,
    onBack: () -> Unit,
) {
    val items by rememberUpdatedState(state.items)
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, state.items.lastIndex),
    ) { items.size }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var renaming by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val onStubAction: (String) -> Unit = { action ->
        scope.launch { snackbarHostState.showSnackbar("$action arrives in a later phase") }
    }

    // Surface each completed op's "done / reason" summary once, then clear it (spec §7.6).
    LaunchedEffect(actionState) {
        val finished = actionState as? ViewerActionUiState.Finished ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(finished.message())
        handlers.onResultShown()
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
                    onOpenWith = { handlers.onOpenWith(item.id) },
                    onInfo = { infoItem = item },
                    onDelete = { handlers.onDelete(item.id) },
                )
                MediaType.VIDEO -> VideoPage(
                    item = item,
                    createMediaSource = { playback.mediaSource(item) },
                    isSettledPage = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onChromeVisibleChange = { chromeVisible = it },
                    onOpenWith = { handlers.onOpenWith(item.id) },
                    onInfo = { infoItem = item },
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
                item = currentItem,
                onCopyTo = { picker = PickerMode.COPY },
                onMoveTo = { picker = PickerMode.MOVE },
                onRename = { renaming = true },
                onSetAs = { currentItem?.let { handlers.onSetAs(it.id) } },
                onDelete = { currentItem?.let { handlers.onDelete(it.id) } },
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

        picker?.let { mode ->
            DestinationPickerSheet(
                title = if (mode == PickerMode.COPY) "Copy to" else "Move to",
                albums = destinations,
                excludeBucketId = currentItem?.bucketId, // never offer the item's own album
                onPick = { bucketId ->
                    val id = currentItem?.id
                    picker = null
                    if (id != null) {
                        when (mode) {
                            PickerMode.COPY -> handlers.onCopyTo(id, bucketId)
                            PickerMode.MOVE -> handlers.onMoveTo(id, bucketId)
                        }
                    }
                },
                onDismiss = { picker = null },
            )
        }

        if (renaming) {
            currentItem?.let { item ->
                NameInputDialog(
                    title = "Rename",
                    confirmLabel = "Rename",
                    initialValue = item.displayName,
                    onConfirm = { name ->
                        renaming = false
                        handlers.onRename(item.id, name)
                    },
                    onDismiss = { renaming = false },
                )
            }
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
 * Bottom action bar (design W1-08/10). Delete (→ Trash) and Move to are live single-item actions;
 * Share and Edit render disabled at 38% — deferred phases with their slots reserved (design deviation
 * #2). More opens the Phase-G1 overflow subset (spec §5); "Set as" only shows for images (§7.4).
 */
@Composable
private fun ViewerActionBar(
    item: MediaItem?,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onRename: () -> Unit,
    onSetAs: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // Overflow subset (spec §5); deferred reference items are omitted entirely, not shown disabled
    // (design deviation #3). "Set as" is image-only — ACTION_ATTACH_DATA has no meaning for video.
    val overflow = buildList<Pair<String, () -> Unit>> {
        add("Copy to" to onCopyTo)
        add("Move to" to onMoveTo)
        add("Rename" to onRename)
        if (item?.type == MediaType.IMAGE) add("Set as" to onSetAs)
        add("Info" to onInfo)
    }

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
        ViewerAction(Icons.Outlined.Delete, "Delete", Modifier.weight(1f)) { onDelete() }
        ViewerAction(Icons.Outlined.DriveFileMove, "Move to", Modifier.weight(1f)) { onMoveTo() }
        ViewerAction(Icons.Filled.Edit, "Edit", Modifier.weight(1f), enabled = false) {}
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ViewerAction(Icons.Filled.MoreVert, "More") { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = JGalleryColors.ViewerSheet,
            ) {
                overflow.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color.White) },
                        modifier = Modifier.testTag("viewer_overflow_$label"),
                        onClick = {
                            menuOpen = false
                            action()
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
