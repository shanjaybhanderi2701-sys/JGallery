package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
// the handle is a rounded pill big enough to hit — 34×56dp at rest, growing to 38×62dp accent-blue on
// grab for unmistakable "you've got it" feedback.
private val ThumbIdleWidth = 34.dp
private val ThumbIdleHeight = 56.dp
private val ThumbGrabbedWidth = 38.dp
private val ThumbGrabbedHeight = 62.dp
private val TouchTarget = 48.dp

/** Dark date/position bubble (C1-05 callout #4): dark surface, white text, sits left of the handle. */
private val BubbleSurface = Color(0xFF16181D)

/**
 * Custom fast-scroll thumb + date bubble for a [LazyVerticalGrid] (design W1-07). Overlay this in a
 * [BoxScope] on top of the grid. Appears while scrolling once content is deeper than 4 viewports;
 * dragging maps linearly onto the FULL index ([FastScrollMath.targetIndex]) with an instant
 * `scrollToItem` per frame — no smooth-scroll catch-up, which is what makes 10k-item jumps free.
 * Release snaps to the nearest entry in [sectionStarts]; the bubble text comes from [bubbleLabel]
 * (collapsed = high drag velocity → year-only, design a14).
 *
 * Reads of [LazyGridState.layoutInfo] are wrapped in [derivedStateOf], so scrolling recomposes only
 * this overlay — never the grid content.
 */
@Composable
fun BoxScope.GridFastScroller(
    gridState: LazyGridState,
    sectionStarts: List<Int>,
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
                .pointerInput(sectionStarts) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            bubbleCollapsed = false
                            lastDragAtMs = 0L
                            dragFraction = fractionForY(offset.y, trackHeightPx)
                        },
                        onVerticalDrag = { change, _ ->
                            val previous = dragFraction
                            dragFraction = fractionForY(change.position.y, trackHeightPx)
                            val now = change.uptimeMillis
                            if (lastDragAtMs > 0L && now > lastDragAtMs) {
                                val speed = (dragFraction - previous) / (now - lastDragAtMs)
                                bubbleCollapsed = FastScrollMath.bubbleCollapsed(speed)
                            }
                            lastDragAtMs = now
                            val total = gridState.layoutInfo.totalItemsCount
                            scope.launch {
                                gridState.scrollToItem(FastScrollMath.targetIndex(dragFraction, total))
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            val total = gridState.layoutInfo.totalItemsCount
                            val landed = FastScrollMath.targetIndex(dragFraction, total)
                            scope.launch {
                                gridState.scrollToItem(FastScrollMath.nearestSectionStart(landed, sectionStarts))
                            }
                        },
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
            if (dragging) {
                val label = bubbleLabel(FastScrollMath.targetIndex(dragFraction, totalItems), bubbleCollapsed)
                if (label != null) DateBubble(label = label, fraction = fraction, trackHeightPx = trackHeightPx)
            }
        }
    }
}

/**
 * Grabbable pill inside the 48dp touch column, vertically placed at [fraction] of the track (C1-05,
 * item 14). At rest it's a 34×56dp white pill with a 3-line grip; on [grabbed] it grows to 38×62dp and
 * fills accent-blue (grip flips to white). Sizes animate so the grab feedback reads as a smooth seize.
 */
@Composable
private fun BoxScope.Thumb(fraction: Float, grabbed: Boolean, trackHeightPx: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val width by animateDpAsState(if (grabbed) ThumbGrabbedWidth else ThumbIdleWidth, label = "thumbWidth")
    val height by animateDpAsState(if (grabbed) ThumbGrabbedHeight else ThumbIdleHeight, label = "thumbHeight")
    val heightPx = with(density) { height.toPx() }
    val y = (fraction * (trackHeightPx - heightPx).coerceAtLeast(0f)).roundToInt()
    val container = if (grabbed) JGalleryColors.Accent else JGalleryColors.Background
    val gripTint = if (grabbed) JGalleryColors.OnAccent else JGalleryColors.TextSecondary
    Box(
        modifier = Modifier
            .offset { IntOffset(0, y) }
            .size(width = width, height = height)
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
    val y = (fraction * (trackHeightPx - bubbleHeightPx).coerceAtLeast(0f)).roundToInt()
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

private fun fractionForY(y: Float, trackHeightPx: Int): Float =
    if (trackHeightPx <= 0) 0f else (y / trackHeightPx).coerceIn(0f, 1f)
