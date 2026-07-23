package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How long the thumb lingers after the last scroll/drag activity (design §3: auto-hide 1.5s). */
private const val AUTO_HIDE_MS = 1_500L

// C1-05 (item 14) grabbable pill: the old 1-line thumb failed device testing (too thin to seize), so
// the handle is a rounded pill big enough to hit — a fixed 34×56dp footprint. Grab feedback (accent
// fill + a subtle scale-up) is drawn WITHOUT changing the layout size: APP-496 root-caused the drag
// jank partly to the old grow-on-grab (56→62dp) shifting the travel range `track - thumbHeight`
// mid-drag, which made the thumb jump under a steady finger. The size is now constant so the
// finger→fraction map ([FastScrollMath.thumbTravelFraction]) and the render offset stay exact inverses.
private val ThumbWidth = 34.dp
private val ThumbHeight = 56.dp
private const val ThumbGrabScale = 1.12f
private val TouchTarget = 48.dp

/** Dark date/position bubble (C1-05 callout #4): dark surface, white text, sits left of the handle. */
private val BubbleSurface = Color(0xFF16181D)

/**
 * Custom fast-scroll thumb + context bubble for a [LazyVerticalGrid] (design W1-07). Overlay this in
 * a [BoxScope] on top of the grid. Appears while scrolling once content is deeper than 4 viewports;
 * dragging maps the finger (thumb centre) linearly onto the scrollable index
 * ([FastScrollMath.thumbTravelFraction] → [FastScrollMath.targetIndex]) with an instant `scrollToItem`
 * per frame — no smooth-scroll catch-up, which is what makes 10k-item jumps free. Release **lands
 * exactly where dragged** (no section snap — APP-496 item 3, CalcVault parity); the bubble text comes
 * from [bubbleLabel] (collapsed = high drag velocity → terse form, design a14).
 *
 * Reads of [LazyGridState.layoutInfo] are wrapped in [derivedStateOf], so scrolling recomposes only
 * this overlay — never the grid content.
 */
@Composable
fun BoxScope.GridFastScroller(
    gridState: LazyGridState,
    bubbleLabel: (index: Int, collapsed: Boolean) -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var bubbleCollapsed by remember { mutableStateOf(false) }
    var lastDragAtMs by remember { mutableLongStateOf(0L) }

    val deepEnough by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            FastScrollMath.deepEnough(info.totalItemsCount, info.visibleItemsInfo.size)
        }
    }
    val totalItems by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.totalItemsCount }
    }
    // Visible-tile count feeds the drag→index map so it inverts [thumbFraction] over the same range
    // (item 7): a release settles the thumb back at the fraction the finger left it on.
    val visibleItems by remember(gridState) {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.size }
    }
    val scrollFraction by remember(gridState) {
        derivedStateOf {
            val info = gridState.layoutInfo
            FastScrollMath.thumbFraction(
                firstVisibleIndex = gridState.firstVisibleItemIndex,
                visibleItems = info.visibleItemsInfo.size,
                totalItems = info.totalItemsCount,
            )
        }
    }

    // Visible while there is activity; lingers AUTO_HIDE_MS after the last scroll/drag frame.
    var visible by remember { mutableStateOf(false) }
    val active = dragging || gridState.isScrollInProgress
    LaunchedEffect(active, deepEnough) {
        if (!deepEnough) {
            visible = false
        } else if (active) {
            visible = true
        } else {
            delay(AUTO_HIDE_MS)
            visible = false
        }
    }

    val fraction = if (dragging) dragFraction else scrollFraction

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(TouchTarget)
                .onSizeChanged { trackHeightPx = it.height }
                .testTag("fast_scroll_track")
                .pointerInput(gridState) {
                    // Fixed thumb height in px — the same basis the render offset uses, so the
                    // finger→fraction map is the exact inverse of `fraction * (track - thumb)`.
                    val thumbPx = ThumbHeight.toPx()

                    // Scroll the grid so the drag [fraction] lands exactly under the thumb (item 3):
                    // targetIndex maps over `[0, total - visible]`, the same range the thumb mirrors
                    // out of, so on release the thumb settles at the released fraction — no snap.
                    fun jumpTo(fraction: Float) {
                        val info = gridState.layoutInfo
                        scope.launch {
                            gridState.scrollToItem(
                                FastScrollMath.targetIndex(
                                    fraction, info.totalItemsCount, info.visibleItemsInfo.size,
                                ),
                            )
                        }
                    }

                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            bubbleCollapsed = false
                            lastDragAtMs = 0L
                            // Press anywhere on the track jumps there immediately (CalcVault parity).
                            dragFraction = FastScrollMath.thumbTravelFraction(offset.y, size.height.toFloat(), thumbPx)
                            jumpTo(dragFraction)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val previous = dragFraction
                            dragFraction = FastScrollMath.thumbTravelFraction(
                                change.position.y, size.height.toFloat(), thumbPx,
                            )
                            val now = change.uptimeMillis
                            if (lastDragAtMs > 0L && now > lastDragAtMs) {
                                val speed = (dragFraction - previous) / (now - lastDragAtMs)
                                bubbleCollapsed = FastScrollMath.bubbleCollapsed(speed)
                            }
                            lastDragAtMs = now
                            jumpTo(dragFraction)
                        },
                        // Land exactly where released — no section snap (APP-496 item 3, CalcVault
                        // parity). The grid already sits at the dragged index from the last frame, so
                        // there is nothing more to do; snapping to the nearest month boundary was the
                        // recurring "lands on the wrong position" report.
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            // Hairline track the pill is parked on (C1-05 callout #1).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(1.dp))
                    .background(JGalleryColors.TextSecondary.copy(alpha = 0.20f)),
            )
            Thumb(
                fraction = fraction,
                grabbed = dragging,
                trackHeightPx = trackHeightPx,
            )
        }
    }

    // Context bubble — APP-592 root-cause fix. Previously drawn INSIDE the 48dp-wide touch column
    // above, where DateBubble's `padding(end = TouchTarget + 8.dp)` (56dp) exceeded the 48dp parent
    // width and collapsed the label to zero width, so the pill never appeared on device. APP-496 only
    // JVM-tested the label string, never the rendered layout, and the pill is not adb-scriptable, so it
    // slipped through. It now lives in the full BoxScope (the screen-width Box that hosts the scroller),
    // so it has room to lay out and sits to the LEFT of the handle (C1-05 callout #4). Its own
    // AnimatedVisibility is driven by [dragging] so it fades in on grab and FADES OUT on release
    // (design §3), independent of the track's AUTO_HIDE_MS linger.
    val bubbleText = if (dragging && totalItems > 0) {
        bubbleLabel(FastScrollMath.targetIndex(dragFraction, totalItems, visibleItems), bubbleCollapsed)
    } else {
        null
    }
    // Retain the last non-null label so the text stays legible through the release fade-out, when
    // `dragging` has already flipped false and `bubbleText` is null again.
    var lastBubbleText by remember { mutableStateOf<String?>(null) }
    if (bubbleText != null) lastBubbleText = bubbleText
    AnimatedVisibility(
        visible = bubbleText != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DateBubble(
                label = lastBubbleText.orEmpty(),
                // dragFraction retains its last value after release, so the bubble holds position while
                // it fades rather than jumping.
                fraction = dragFraction,
                trackHeightPx = trackHeightPx,
            )
        }
    }
}

/**
 * Flat-grid convenience overload for grids that have no date sections — folder/in-album grids, the
 * whole-library picker, and the Recycle Bin (APP-466: the shared set on *every* grid). Lands release
 * on the exact dragged item and shows the absolute-position bubble ("item 3,120 of 8,412", design
 * W3-09) instead of a month label. [itemCount] is the total tile count; the `deepEnough` gate still
 * hides the thumb until the grid is more than 4 viewports deep, so short folders/pickers never grow a
 * handle.
 */
@Composable
fun BoxScope.GridFastScroller(
    gridState: LazyGridState,
    itemCount: Int,
    modifier: Modifier = Modifier,
) {
    GridFastScroller(
        gridState = gridState,
        // Grid index is 0-based; the position bubble is 1-based (design W3-09). No collapse — a bare
        // position never has a terser form, so the fling/slow label is identical.
        bubbleLabel = { index, _ -> FastScrollMath.formatItemPosition(index + 1, itemCount) },
        modifier = modifier,
    )
}

/**
 * Grabbable pill inside the 48dp touch column, vertically placed at [fraction] of the track (C1-05,
 * item 14). At rest it's a 34×56dp white pill with a 3-line grip; on [grabbed] it grows to 38×62dp and
 * fills accent-blue (grip flips to white). Sizes animate so the grab feedback reads as a smooth seize.
 */
@Composable
private fun BoxScope.Thumb(fraction: Float, grabbed: Boolean, trackHeightPx: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Fixed layout footprint — the travel range `track - thumbHeight` never changes, so the thumb
    // cannot jump under a steady finger (APP-496). Grab feedback is a graphicsLayer scale that does
    // not affect layout, plus the accent fill/shadow.
    val heightPx = with(density) { ThumbHeight.toPx() }
    val scale by animateFloatAsState(if (grabbed) ThumbGrabScale else 1f, label = "thumbScale")
    val y = (fraction * (trackHeightPx - heightPx).coerceAtLeast(0f)).roundToInt()
    val container = if (grabbed) JGalleryColors.Accent else JGalleryColors.Background
    val gripTint = if (grabbed) JGalleryColors.OnAccent else JGalleryColors.TextSecondary
    Box(
        modifier = Modifier
            .offset { IntOffset(0, y) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = ThumbWidth, height = ThumbHeight)
            .align(Alignment.TopCenter)
            .shadow(if (grabbed) 6.dp else 3.dp, RoundedCornerShape(percent = 50))
            .background(container, RoundedCornerShape(percent = 50))
            .testTag("fast_scroll_thumb"),
        contentAlignment = Alignment.Center,
    ) {
        // 3-line grip so the handle reads as something you can seize (device-test fix, C1-05 callout #1).
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(gripTint),
                )
            }
        }
    }
}

/**
 * Dark date/position bubble left of the handle while dragging (C1-05 callout #4): "Month YYYY" plus an
 * absolute-position suffix at scale ("March 2024 · item 3,120 of 8,412"), or a terse "YYYY" at fling
 * speed. Dark surface + white text so it stays legible over any grid content; fades on release.
 */
@Composable
private fun BoxScope.DateBubble(label: String, fraction: Float, trackHeightPx: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bubbleHeightPx = with(density) { 44.dp.toPx() }
    val thumbHeightPx = with(density) { ThumbHeight.toPx() }
    // Ride centred on the thumb: the thumb centre sits at `fraction * travel + thumb/2` over the same
    // travel range the handle uses, so the bubble tracks the handle exactly through the whole drag.
    val thumbCenter = fraction * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f) + thumbHeightPx / 2f
    val y = (thumbCenter - bubbleHeightPx / 2f).roundToInt().coerceAtLeast(0)
    Box(
        modifier = Modifier
            .offset { IntOffset(0, y) }
            .align(Alignment.TopEnd)
            .padding(end = TouchTarget + 8.dp)
            .heightIn(min = 44.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .background(BubbleSurface, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("fast_scroll_bubble"),
        contentAlignment = Alignment.Center,
    ) {
        // Allow a second line so the position suffix never truncates; terse "YYYY" fling labels stay one line.
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
