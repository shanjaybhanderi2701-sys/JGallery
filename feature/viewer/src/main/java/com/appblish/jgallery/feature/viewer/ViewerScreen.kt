package com.appblish.jgallery.feature.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.FileNames
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.RotationDirection
import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.thumbs.coverRequest
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.appblish.jgallery.core.ui.component.FavoriteRed
import com.appblish.jgallery.core.ui.component.NameInputDialog
import com.appblish.jgallery.core.ui.selection.AlbumOpVerb
import com.appblish.jgallery.core.ui.selection.MoveDestinationSheet
import com.appblish.jgallery.core.ui.share.MediaShareRequest
import com.appblish.jgallery.core.ui.share.ShareIntents
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryViewerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The single-item file actions the viewer runs, bundled so the pager takes one param, not six. */
internal data class ViewerActionHandlers(
    val onCopyTo: (id: MediaId, bucketId: String) -> Unit,
    val onMoveTo: (id: MediaId, bucketId: String) -> Unit,
    /** Create-and-fill: make album [name] and copy [id] into it as its first item (C1-03 New-album tile). */
    val onCopyToNewAlbum: (id: MediaId, name: String) -> Unit,
    /** Create-and-fill: make album [name] and move [id] into it (C1-03 New-album tile, Move verb). */
    val onMoveToNewAlbum: (id: MediaId, name: String) -> Unit,
    val onRename: (id: MediaId, newName: String) -> Unit,
    /** Rotate the image 90° left/right, persisting orientation to the file (G3-1 · APP-639). */
    val onRotate: (id: MediaId, direction: RotationDirection) -> Unit,
    val onDelete: (id: MediaId) -> Unit,
    /** Fire the system share sheet for the single on-screen item (APP-641). [mimeType] narrows the chooser. */
    val onShare: (id: MediaId, mimeType: String) -> Unit,
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
 * + bottom-bar file actions (Copy/Move/Rename/Set-as/Delete/Details) run through the §7 E8 core via the
 * `:core:index` operations facade (W2-E12). Favourite (APP-543), Rotate (G3-1 · APP-639) and Share
 * (APP-641, system share sheet) are live; Edit stays a deferred stub.
 */
@Composable
internal fun ViewerRoute(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val actionState by viewModel.action.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val slideshowIntervalMs by viewModel.slideshowIntervalMs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        // A resolved boundary uri → launch the system "Set as" (ACTION_ATTACH_DATA) chooser (spec §7.4).
        viewModel.setAsUri.collect { uri -> context.launchSetAs(uri) }
    }
    LaunchedEffect(viewModel) {
        // Resolved boundary uri for an unplayable video → hand it to another app (§8 W3-05 "Open with").
        viewModel.openWithUri.collect { uri -> context.launchOpenWith(uri) }
    }
    LaunchedEffect(viewModel) {
        // Resolved single-item share (APP-641) → fire the system share sheet, or toast if it's gone.
        viewModel.shareEvents.collect { request ->
            when (request) {
                is MediaShareRequest.Ready -> context.launchShareSheet(request.uris, request.mimeType)
                MediaShareRequest.Empty ->
                    Toast.makeText(context, "Nothing left to share", Toast.LENGTH_SHORT).show()
            }
        }
    }
    ViewerScreen(
        state = state,
        playback = viewModel.playback,
        destinations = destinations,
        actionState = actionState,
        handlers = ViewerActionHandlers(
            onCopyTo = viewModel::copyTo,
            onMoveTo = viewModel::moveTo,
            onCopyToNewAlbum = viewModel::copyToNewAlbum,
            onMoveToNewAlbum = viewModel::moveToNewAlbum,
            onRename = viewModel::rename,
            onRotate = viewModel::rotate,
            onDelete = viewModel::delete,
            onShare = viewModel::share,
            onSetAs = viewModel::setAs,
            onOpenWith = viewModel::openWith,
            onResultShown = viewModel::dismissActionResult,
        ),
        favorites = favorites,
        onToggleFavorite = viewModel::toggleFavorite,
        slideshowIntervalMs = slideshowIntervalMs,
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
    favorites: Set<MediaId> = emptySet(),
    onToggleFavorite: (MediaId) -> Unit = {},
    slideshowIntervalMs: Long = ViewDefaults.DEFAULT_SLIDESHOW_INTERVAL_MS,
    onBack: () -> Unit,
) {
    JGalleryViewerTheme {
        // Chrome visibility is hoisted here so the immersive window effect can toggle the *system*
        // bars in lock-step with the app chrome (APP-643 #4). Survives config changes so a rotation
        // mid-view doesn't pop the bars back. Default visible: chrome (and the status bar) show on entry.
        var chromeVisible by rememberSaveable { mutableStateOf(true) }
        // C1-02 (item 11) + APP-643 (#4): the viewer is an immersive, distraction-free canvas for as
        // long as this route is on screen — light icons over the dark media, nav bar hidden throughout,
        // and (new) the status bar hides together with the chrome for true sticky immersive on tap.
        ImmersiveViewerEffect(chromeVisible = chromeVisible)
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
                    ViewerPager(
                        state, playback, destinations, actionState, handlers,
                        favorites, onToggleFavorite, slideshowIntervalMs, onBack,
                        chromeVisible = chromeVisible,
                        onChromeVisibleChange = { chromeVisible = it },
                    )
            }
        }
    }
}

/**
 * Immersive window setup for the viewer route (C1-02, item 11). Scoped to the viewer only via
 * [DisposableEffect]: on enter it draws the media edge-to-edge behind a **dark status bar** (light
 * icons, `isAppearanceLightStatusBars = false`) and **hides the system navigation bar** for the whole
 * session, sticky-immersive (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` — a swipe reveals a transient bar
 * that auto-hides). On exit it restores the app's status bar, icon appearance and re-shows both system
 * bars in one step, no flicker. Edge-to-edge itself is already on from `enableEdgeToEdge()` in the
 * Activity, and APP-643 gives the viewer the full window (no Scaffold inset padding) so the media truly
 * fills behind the bars.
 *
 * APP-643 (#4): the **status bar now hides and shows together with [chromeVisible]** — tapping the media
 * to dismiss the app chrome also hides the status bar for a true full-screen canvas; tapping again brings
 * both back. The nav bar stays hidden throughout (the bottom action bar is the viewer's own chrome).
 *
 * APP-593: the effect also paints the real status-bar plane the viewer canvas colour. The viewer only
 * flipped the status-bar *icons* to light but inherited the app's opaque **light (white)** status-bar
 * background, so on this dark canvas the status bar rendered white with invisible white icons above the
 * black-gradient top bar (board finding on the 1.0.0 build). Setting [android.view.Window.setStatusBarColor]
 * to the canvas colour at the **window** level makes the tint reach past the shell's inset handling — the
 * same window-level technique the grid selection bar uses ([com.appblish.jgallery.core.ui.selection]) —
 * so the status bar is flush with the viewer chrome instead of white. Contrast enforcement is disabled so
 * the opaque tint isn't scrimmed; the colour, contrast flag and icon appearance are all restored on exit.
 */
@Composable
private fun ImmersiveViewerEffect(chromeVisible: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.findActivity()?.window ?: return
    val statusBarTint = JGalleryColors.ViewerCanvas.toArgb()
    // One-time window styling for the viewer session + restore-on-exit. Keyed on the tint (constant),
    // so it runs once on enter and once on dispose regardless of chrome toggles.
    DisposableEffect(statusBarTint) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previousLightStatusBars = controller.isAppearanceLightStatusBars
        val previousColor = window.statusBarColor
        val previousContrast = window.isStatusBarContrastEnforced
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false // light (white) icons over the dark media
        window.isStatusBarContrastEnforced = false
        window.statusBarColor = statusBarTint // dark plane behind the light icons — no white status bar
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = previousLightStatusBars
            window.statusBarColor = previousColor
            window.isStatusBarContrastEnforced = previousContrast
        }
    }
    // Toggle the status bar in lock-step with the app chrome (APP-643 #4). The nav bar is already hidden
    // for the whole session, so hiding the status bar here yields a true full-screen immersive canvas.
    DisposableEffect(chromeVisible) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (chromeVisible) {
            controller.isAppearanceLightStatusBars = false // re-assert light icons whenever it reappears
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
        onDispose {}
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
 * Fire the system share sheet for the single on-screen item (APP-641). [uris] holds the one
 * §1.6-sanctioned MediaStore `content://` uri; [ShareIntents] builds the read-only + temporary
 * `ACTION_SEND` intent (the identical construction the grid / album multi-select share uses,
 * APP-541/549), and `runCatching` degrades to a no-op if the device has no share target instead of
 * crashing the viewer.
 */
private fun Context.launchShareSheet(uris: List<Uri>, mimeType: String) {
    val intent = ShareIntents.buildSendIntent(uris, mimeType)
    runCatching { startActivity(Intent.createChooser(intent, "Share")) }
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

/**
 * System animation scale (`Settings.Global.ANIMATOR_DURATION_SCALE`). `0f` means the user has turned
 * animations off (developer options / accessibility) → the viewer honours reduced motion (motion-spec §5):
 * dismiss keeps its 1:1 translation but drops the scale morph and springs. Defaults to `1f` if unreadable.
 */
private fun animatorDurationScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)

@Composable
private fun ViewerPager(
    state: ViewerUiState.Ready,
    playback: PlaybackSources,
    destinations: List<Album>,
    actionState: ViewerActionUiState,
    handlers: ViewerActionHandlers,
    favorites: Set<MediaId>,
    onToggleFavorite: (MediaId) -> Unit,
    slideshowIntervalMs: Long,
    onBack: () -> Unit,
    chromeVisible: Boolean,
    onChromeVisibleChange: (Boolean) -> Unit,
) {
    val items by rememberUpdatedState(state.items)
    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceIn(0, state.items.lastIndex),
    ) { items.size }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var renaming by remember { mutableStateOf(false) }
    // Slideshow / auto-play (APP-544, trigger APP-594): `on` gates the lean-back mode; `paused` halts
    // the timer without leaving it. Both survive config changes so a rotation mid-slideshow doesn't
    // drop the user out. The dwell interval is read live off Settings via [slideshowIntervalMs].
    var slideshowOn by rememberSaveable { mutableStateOf(false) }
    var slideshowPaused by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Swipe-down-to-dismiss (APP-691 §2, motion-spec APP-692). The state is hoisted here (arbiter §6:
    // dismiss translates/fades the WHOLE viewer + scrim, not one page); the per-page arbiter reports the
    // raw drag up. Recreated once the container height is known so the fraction-based threshold is exact
    // (motion-spec §0), and once we learn whether the system has animations disabled (reduced motion, §5).
    val density = LocalDensity.current
    val context = LocalContext.current
    val reducedMotion = remember { animatorDurationScale(context) == 0f }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    val dismissState = remember(containerHeightPx, reducedMotion) {
        with(density) {
            DismissState(
                thresholdPx = ViewerMotion.thresholdDistancePx(
                    containerHeightPx = containerHeightPx,
                    minPx = ViewerMotion.ThresholdDistanceMin.toPx(),
                    maxPx = ViewerMotion.ThresholdDistanceMax.toPx(),
                ),
                thresholdVelocityPx = ViewerMotion.ThresholdVelocityDpPerSec.dp.toPx(),
                containerHeightPx = containerHeightPx,
                reducedMotion = reducedMotion,
            )
        }
    }
    // Dismiss must not fire while a slideshow runs (arbiter §7): back-press stops the slideshow first.
    val dismissEnabled = !slideshowOn

    // Shared-element close (motion-spec APP-711 §3): the toolbar back AND hardware back both route through
    // one idempotent closeViewer(). Chrome fades out first (§3.1, 100 ms) then the nav pop runs, letting the
    // grid↔viewer `sharedBounds` morph (PhotoSharedTransition, 240 ms EmphasizedAccel) animate the photo home
    // to its source tile — no hard cut. The `closing` latch makes a second back-press while the close is in
    // flight a no-op (§3.1 re-entrancy). A committed swipe-dismiss also sets it (below) so the two can't race.
    var closing by remember { mutableStateOf(false) }
    val closeViewer: () -> Unit = closeViewer@{
        if (closing) return@closeViewer
        closing = true
        onChromeVisibleChange(false)
        scope.launch {
            delay(ViewerMotion.ChromeFadeOutMs.toLong())
            onBack()
        }
    }

    // Surface each completed op's "done / reason" summary once, then clear it (spec §7.6).
    LaunchedEffect(actionState) {
        val finished = actionState as? ViewerActionUiState.Finished ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(finished.message())
        handlers.onResultShown()
    }

    // While a slideshow runs, back-press stops it and returns to normal viewing rather than exiting
    // the viewer — one predictable "escape" that matches the on-screen Stop button (APP-594 DoD: exit).
    BackHandler(enabled = slideshowOn) {
        slideshowOn = false
        slideshowPaused = false
        onChromeVisibleChange(true)
    }

    // Hardware back closes the viewer through the shared-element morph (motion-spec §3.2) instead of a hard
    // pop. Enabled only when no slideshow runs — the slideshow BackHandler above keeps priority (back stops
    // the slideshow, it does not close the viewer), and the two `enabled` flags are mutually exclusive.
    BackHandler(enabled = !slideshowOn) { closeViewer() }

    // Auto-advance driver (APP-544, video-dwell fix APP-548, configured interval APP-594). Runs only
    // while the slideshow is on and not paused. It dwells [slideshowIntervalMs] per image, then advances
    // by the pure [Slideshow.nextPage] rule (loop on). A video is given a longer, *bounded* dwell — long
    // enough to let the clip play through, but capped by [Slideshow.videoDwellMs] so a long/looping video
    // can never pin lean-back auto-play forever. Keyed on the interval too, so changing it in Settings
    // mid-run is picked up on the next tick.
    LaunchedEffect(slideshowOn, slideshowPaused, slideshowIntervalMs, items) {
        if (!slideshowOn || slideshowPaused) return@LaunchedEffect
        while (true) {
            val onScreen = items.getOrNull(pagerState.currentPage)
            val dwell = if (onScreen?.type == MediaType.VIDEO) {
                Slideshow.videoDwellMs(onScreen.durationMillis, slideshowIntervalMs)
            } else {
                slideshowIntervalMs
            }
            delay(dwell)
            // Re-read the page after the dwell so a manual swipe mid-slideshow advances from where the
            // user actually is, not from where the timer started.
            val next = Slideshow.nextPage(pagerState.currentPage, items.size, loop = true) ?: break
            pagerState.animateScrollToPage(next)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        // Black scrim behind the pager (motion-spec §2.1): fades 1.0 → 0.0 with the drag so the backdrop
        // dims out as the photo is pulled toward its tile. Reads dismiss state inside the graphicsLayer
        // lambda → draw-phase only, no recomposition per frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (dismissState.active) dismissState.scrimAlpha else 1f }
                .background(JGalleryColors.ViewerCanvas),
        )
        HorizontalPager(
            state = pagerState,
            // Drag-follow: the whole current page translates 1:1, scales & fades per the designer's `g`
            // mapping (motion-spec §2.1). Dismiss is a 1×-only gesture, so this layer and the per-page
            // zoom layer operate in disjoint zoom regimes and never fight (arbiter §6). Zero-cost at rest.
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (dismissState.active) {
                        translationX = dismissState.translationX
                        translationY = dismissState.translationY
                        scaleX = dismissState.pageScale
                        scaleY = dismissState.pageScale
                        alpha = dismissState.pageAlpha
                    }
                },
            beyondViewportPageCount = 1, // neighbours pre-decode so swipes land on pixels, not blanks
            pageSpacing = 12.dp,
            key = { index -> items.getOrNull(index)?.id?.value ?: index },
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            when (item.type) {
                MediaType.IMAGE -> ImagePage(
                    item = item,
                    onToggleChrome = { onChromeVisibleChange(!chromeVisible) },
                    onOpenWith = { handlers.onOpenWith(item.id) },
                    onInfo = { infoItem = item },
                    onDelete = { handlers.onDelete(item.id) },
                    dismissEnabled = dismissEnabled,
                    onDismissDrag = { delta ->
                        // First movement of a claimed dismiss hides the chrome so nothing obstructs the
                        // photo (motion-spec §2.1); the drag then follows the finger 1:1.
                        if (!dismissState.active && chromeVisible) onChromeVisibleChange(false)
                        dismissState.onDrag(delta)
                    },
                    onDismissRelease = { velocity ->
                        // A committed swipe runs DismissState's in-place fade (§2.3) then pops; guard the pop
                        // with the same `closing` latch closeViewer() uses so a back-press mid-fade can't
                        // double-pop. Chrome is already hidden by onDismissDrag, so no extra chrome fade here.
                        scope.launch {
                            dismissState.onRelease(velocity, onDismiss = {
                                if (!closing) {
                                    closing = true
                                    onBack()
                                }
                            })
                        }
                    },
                    onDismissCancel = { scope.launch { dismissState.cancelToSnapBack() } },
                )
                MediaType.VIDEO -> VideoPage(
                    item = item,
                    createMediaSource = { playback.mediaSource(item) },
                    isSettledPage = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onChromeVisibleChange = onChromeVisibleChange,
                    onOpenWith = { handlers.onOpenWith(item.id) },
                    onInfo = { infoItem = item },
                )
            }
        }

        val currentItem = items.getOrNull(pagerState.currentPage)
        // Chrome is suppressed entirely while a slideshow runs — the slideshow overlay is the only UI.
        AnimatedVisibility(
            visible = chromeVisible && !slideshowOn,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            ViewerHeader(
                item = currentItem,
                favorite = currentItem?.id in favorites,
                onToggleFavorite = { currentItem?.let { onToggleFavorite(it.id) } },
                onRotate = { dir -> currentItem?.let { handlers.onRotate(it.id, dir) } },
                // Toolbar back rides the shared-element close, same path as hardware back (motion-spec §3.2).
                onBack = closeViewer,
            )
        }
        AnimatedVisibility(
            visible = chromeVisible && !slideshowOn,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ViewerActionBar(
                item = currentItem,
                canSlideshow = items.size > 1,
                onShare = { currentItem?.let { handlers.onShare(it.id, it.mimeType) } },
                onCopyTo = { picker = PickerMode.COPY },
                onMoveTo = { picker = PickerMode.MOVE },
                onRename = { renaming = true },
                onSetAs = { currentItem?.let { handlers.onSetAs(it.id) } },
                onDelete = { currentItem?.let { handlers.onDelete(it.id) } },
                onInfo = { currentItem?.let { infoItem = it } },
                onStartSlideshow = {
                    slideshowPaused = false
                    onChromeVisibleChange(false)
                    slideshowOn = true
                },
            )
        }
        AnimatedVisibility(
            visible = slideshowOn,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            SlideshowControls(
                position = pagerState.currentPage + 1,
                count = items.size,
                paused = slideshowPaused,
                onTogglePause = { slideshowPaused = !slideshowPaused },
                onStop = {
                    slideshowOn = false
                    slideshowPaused = false
                    onChromeVisibleChange(true)
                },
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
            MediaInfoDialog(
                item = item,
                onDismiss = { infoItem = null },
                // Rename from "details" (APP-590): close the sheet and open the shared rename dialog,
                // which reads the same current item so the pre-filled name always matches the Name row.
                onRename = { infoItem = null; renaming = true },
            )
        }

        picker?.let { mode ->
            // C1-03 (item 12): cover-thumbnail destination grid + inline "New album" create-and-move.
            // coverFor stays a lambda so :core:ui never depends on :core:thumbs — the feature layer
            // supplies the model via Album.coverRequest().
            MoveDestinationSheet(
                verb = if (mode == PickerMode.COPY) AlbumOpVerb.COPY else AlbumOpVerb.MOVE,
                itemCount = 1, // the viewer acts on the single item on screen
                albums = destinations,
                coverFor = { it.coverRequest() },
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
                onCreateNew = { name ->
                    val id = currentItem?.id
                    picker = null
                    if (id != null && name.isNotEmpty()) {
                        when (mode) {
                            PickerMode.COPY -> handlers.onCopyToNewAlbum(id, name)
                            PickerMode.MOVE -> handlers.onMoveToNewAlbum(id, name)
                        }
                    }
                },
                onBrowseFolders = {
                    // W2-04 device-folder picker isn't built yet; keep the affordance honest.
                    picker = null
                    scope.launch { snackbarHostState.showSnackbar("Browsing other folders arrives in a later phase") }
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
                    validate = { FileNames.renameError(it, item.displayName) },
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

/** Header (design W1-08): back, filename, favorite (live G2 · APP-543), rotate L/R (live G3-1 · APP-639). */
@Composable
private fun ViewerHeader(
    item: MediaItem?,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRotate: (RotationDirection) -> Unit,
    onBack: () -> Unit,
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
        // "Like" pop on tap (APP-670, spec §6): a quick 1.0→1.2→1.0 scale bounce. Driven from the tap
        // (not from [favorite] changing) so paging between a favorited and non-favorited item doesn't
        // bounce — only a deliberate toggle does.
        val heartScale = remember { Animatable(1f) }
        val heartScope = rememberCoroutineScope()
        IconButton(
            onClick = {
                onToggleFavorite()
                heartScope.launch {
                    heartScale.snapTo(1f)
                    heartScale.animateTo(1.2f, tween(90))
                    heartScale.animateTo(1f, tween(90))
                }
            },
            enabled = item != null,
            modifier = Modifier
                .testTag(if (favorite) "viewer_favorited" else "viewer_unfavorited")
                // Announce the toggle state to TalkBack, not just a label (spec §6).
                .semantics {
                    role = Role.Switch
                    stateDescription = if (favorite) "Favorited" else "Not favorited"
                },
        ) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (favorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (favorite) FavoriteRed else Color.White,
                modifier = Modifier.graphicsLayer {
                    scaleX = heartScale.value
                    scaleY = heartScale.value
                },
            )
        }
        // Rotate is image-only — EXIF/pixel orientation has no meaning for a video (spec §7 · G3-1).
        if (item?.type == MediaType.IMAGE) {
            IconButton(
                onClick = { onRotate(RotationDirection.LEFT) },
                modifier = Modifier.testTag("viewer_rotate_left"),
            ) {
                Icon(Icons.Filled.RotateLeft, contentDescription = "Rotate left", tint = Color.White)
            }
            IconButton(
                onClick = { onRotate(RotationDirection.RIGHT) },
                modifier = Modifier.testTag("viewer_rotate_right"),
            ) {
                Icon(Icons.Filled.RotateRight, contentDescription = "Rotate right", tint = Color.White)
            }
        }
    }
}

/**
 * Bottom action bar (design W1-08/10). Share (APP-641), Delete (→ Trash) and Move to are live
 * single-item actions; Edit renders disabled at 38% — still a deferred phase with its slot reserved
 * (design deviation #2). More opens the Phase-G1 overflow subset (spec §5); "Set as" only shows for
 * images (§7.4).
 */
@Composable
private fun ViewerActionBar(
    item: MediaItem?,
    canSlideshow: Boolean,
    onShare: () -> Unit,
    onCopyTo: () -> Unit,
    onMoveTo: () -> Unit,
    onRename: () -> Unit,
    onSetAs: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onStartSlideshow: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // Overflow subset (spec §5); deferred reference items are omitted entirely, not shown disabled
    // (design deviation #3). "Set as" is image-only — ACTION_ATTACH_DATA has no meaning for video.
    val overflow = buildList<Pair<String, () -> Unit>> {
        if (canSlideshow) add("Slideshow" to onStartSlideshow) // G2 auto-play trigger (APP-544 / APP-594)
        add("Copy to" to onCopyTo)
        add("Move to" to onMoveTo)
        add("Rename" to onRename)
        if (item?.type == MediaType.IMAGE) add("Set as" to onSetAs)
        add("Details" to onInfo) // APP-643 (#6): single term "Details" everywhere (dialog is titled Details)
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
        ViewerAction(
            Icons.Filled.Share,
            "Share",
            Modifier.weight(1f).testTag("viewer_share"),
            enabled = item != null,
        ) { onShare() }
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
