package com.appblish.jgallery.core.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Floating back-to-top affordance for a long grid (design C1-07, item 2). It gives a fast way back to
 * the top without ever fighting the content:
 *
 * - **Appear** once the user has scrolled past ~1.5 screens ([SCREENS_BEFORE_OFFER]) *and* is scrolling
 *   up (the intent to go back) — so it never covers content during normal downward browsing.
 * - **Hide** at the very top, while scrolling down, and whenever [enabled] is false (selection mode
 *   yields the corner to the bulk bar — callout 3).
 * - On first appearance it shows a brief **"Top"** label pill for discoverability, then collapses to
 *   an icon-only FAB after [LABEL_DURATION_MS].
 *
 * Visibility is derived state off the [gridState] — no scroll listener, no per-frame work — and the
 * component is a full-size transparent overlay that only draws the FAB itself, so it can be dropped
 * into any grid's `Box` (Photos, Collections, folder grids, pickers) without disturbing layout.
 *
 * @param enabled master gate; pass `false` to force-hide (e.g. while a selection is active).
 */
@Composable
fun ScrollToTopFab(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()

    // Scroll-direction tracker: compare the (index, offset) of the first visible item against the
    // previous frame. Ties (no movement) preserve the last known direction so a momentary settle
    // doesn't flip the FAB. `enabled` is applied OUTSIDE the derived state so toggling selection mode
    // hides the FAB immediately, without waiting for the next scroll.
    var previousIndex by remember(gridState) { mutableIntStateOf(gridState.firstVisibleItemIndex) }
    var previousOffset by remember(gridState) { mutableIntStateOf(gridState.firstVisibleItemScrollOffset) }
    var scrollingUp by remember(gridState) { mutableStateOf(false) }

    val offerVisible by remember(gridState) {
        derivedStateOf {
            val index = gridState.firstVisibleItemIndex
            val offset = gridState.firstVisibleItemScrollOffset
            if (index != previousIndex || offset != previousOffset) {
                scrollingUp = if (index != previousIndex) index < previousIndex else offset < previousOffset
                previousIndex = index
                previousOffset = offset
            }
            scrollingUp && scrolledPastOffer(index, gridState.layoutInfo.visibleItemsInfo.size)
        }
    }
    val visible = enabled && offerVisible

    // The label pill shows only for the first moment of each visibility episode, then collapses to an
    // icon. `expanded` is reset every time the FAB (re)appears.
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            expanded = true
            delay(LABEL_DURATION_MS)
            expanded = false
        } else {
            expanded = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                expanded = expanded,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                    )
                },
                text = { Text("Top") },
                containerColor = JGalleryColors.Background,
                contentColor = JGalleryColors.Accent,
                modifier = Modifier.testTag("scroll_to_top_fab"),
            )
        }
    }
}

/**
 * The appear threshold (design C1-07 callout 2): offer the FAB once the first visible item is more
 * than ~1.5 screens down. [itemsPerViewport] is how many cells currently fill the viewport; a
 * zero/unknown viewport never offers. Pure so the rule is unit-testable.
 */
fun scrolledPastOffer(firstVisibleItemIndex: Int, itemsPerViewport: Int): Boolean {
    if (itemsPerViewport <= 0) return false
    val threshold = (itemsPerViewport * SCREENS_BEFORE_OFFER_NUM) / SCREENS_BEFORE_OFFER_DEN
    return firstVisibleItemIndex > threshold
}

// ~1.5 screens, expressed as an exact integer ratio so the pure helper needs no floating point.
private const val SCREENS_BEFORE_OFFER_NUM = 3
private const val SCREENS_BEFORE_OFFER_DEN = 2
private const val LABEL_DURATION_MS = 2_000L
