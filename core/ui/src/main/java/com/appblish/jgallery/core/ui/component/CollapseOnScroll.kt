package com.appblish.jgallery.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Collapse-on-scroll controller (design G1-D8 item 4).
 *
 * Drives the visibility of a chrome element (here: the [FormatFilterChips] row) off the **direction**
 * of a nested scroll rather than an absolute offset threshold — offset thresholds feel twitchy near
 * the boundary, whereas reading the sign of each scroll delta is stable. Dragging the content up (finger
 * up, `available.y < 0`) hides the bar; dragging it down (`available.y > 0`, i.e. pulling the top back
 * into view) reveals it. Because it reveals on the very first downward delta, scrolling back to the top
 * always brings the bar back.
 *
 * Attach [connection] to a scroll container — or, more usefully, to an **ancestor** of one (e.g. the
 * `Column` that holds both the bar and the grid) — so it observes the grid's scroll before the grid
 * consumes it. It never consumes anything ([Offset.Zero] is always returned), so pull-to-refresh and
 * the grid keep working untouched. Gate the collapsible content behind [visible], ideally via
 * [CollapsibleContent] which animates the show/hide and collapses the layout smoothly so the grid
 * beneath slides — no jump.
 */
@Stable
class CollapseOnScrollState(initiallyVisible: Boolean) {

    /** True when the collapsible chrome should be shown. Driven by scroll direction. */
    var visible by mutableStateOf(initiallyVisible)
        private set

    /** Force the chrome visible again (e.g. when leaving selection mode). */
    fun reveal() {
        visible = true
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            // A small dead-zone so fling settle / sub-pixel jitter can't flip the bar back and forth.
            when {
                dy <= -DIRECTION_THRESHOLD -> visible = false // content moving up → hide
                dy >= DIRECTION_THRESHOLD -> visible = true // content moving down → reveal
            }
            return Offset.Zero
        }
    }

    private companion object {
        /** Minimum per-frame scroll delta (px) that counts as an intentional direction change. */
        const val DIRECTION_THRESHOLD = 1.5f
    }
}

/** Remembers a [CollapseOnScrollState] for the lifetime of the composition. */
@Composable
fun rememberCollapseOnScrollState(initiallyVisible: Boolean = true): CollapseOnScrollState =
    remember { CollapseOnScrollState(initiallyVisible) }

/**
 * Smooth collapse/expand for scroll-driven chrome (design G1-D8 item 4). Slides + fades the [content]
 * and, crucially, animates the vertical space it occupies to zero so the grid below eases up into the
 * freed space instead of jumping.
 */
@Composable
fun CollapsibleContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // expand/shrink from the top edge so the bar tucks up under the header; pair with a fade.
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        content()
    }
}
