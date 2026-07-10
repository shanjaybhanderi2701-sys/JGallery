package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
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
private val ThumbSize = 38.dp // accent circle (design §3)
private val TouchTarget = 48.dp

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
            Thumb(
                fraction = fraction,
                trackHeightPx = trackHeightPx,
            )
            if (dragging) {
                val label = bubbleLabel(FastScrollMath.targetIndex(dragFraction, totalItems), bubbleCollapsed)
                if (label != null) DateBubble(label = label, fraction = fraction, trackHeightPx = trackHeightPx)
            }
        }
    }
}

/** 38dp accent circle inside the 48dp touch column, vertically placed at [fraction] of the track. */
@Composable
private fun BoxScope.Thumb(fraction: Float, trackHeightPx: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbPx = with(density) { ThumbSize.toPx() }
    val y = (fraction * (trackHeightPx - thumbPx).coerceAtLeast(0f)).roundToInt()
    Box(
        modifier = Modifier
            .offset { IntOffset(0, y) }
            .size(ThumbSize)
            .align(Alignment.TopCenter)
            .shadow(3.dp, CircleShape)
            .background(JGalleryColors.Accent, CircleShape)
            .testTag("fast_scroll_thumb"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.UnfoldMore,
            contentDescription = null,
            tint = JGalleryColors.OnAccent,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Pill to the left of the thumb showing "Month YYYY" (or "YYYY" when collapsed at speed). */
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
            .shadow(6.dp, RoundedCornerShape(22.dp))
            .background(JGalleryColors.Accent, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("fast_scroll_bubble"),
        contentAlignment = Alignment.Center,
    ) {
        // At scale the label carries an absolute-position suffix ("March 2024 · item 8,412 of 61,908",
        // design W3-09); allow a second line so it never truncates. Terse "YYYY" fling labels stay one line.
        Text(
            text = label,
            color = JGalleryColors.OnAccent,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun fractionForY(y: Float, trackHeightPx: Int): Float =
    if (trackHeightPx <= 0) 0f else (y / trackHeightPx).coerceIn(0f, 1f)
