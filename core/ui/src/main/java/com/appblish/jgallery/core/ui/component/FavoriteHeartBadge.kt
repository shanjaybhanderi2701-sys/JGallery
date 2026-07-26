package com.appblish.jgallery.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A small, non-interactive "favorited" indicator overlaid on a media tile (Favorites rework · APP-670,
 * spec `favorites-rework-spec` §3). It is a **badge, not a control**: there is no tap target, no toggle,
 * and it renders **only** on favorited items — the board forbids a per-tile favorite control, so the sole
 * way to favorite from a grid is the multi-select overflow (spec §2b/§5) or the viewer header heart.
 *
 * The badge binds directly to the item's live favorite membership, so it pops in/out (fade + scale) when
 * the item is starred/un-starred anywhere else — the viewer heart, the selection bar, another surface —
 * without any per-tile gesture (spec §6, board item 6 "instant reflection").
 *
 * A filled [FavoriteRed] heart on a subtle circular scrim ([HeartScrim]) so it reads over any frame. Sits
 * in the bottom-start corner — the one corner the format badge (top-start), select badge (top-end) and
 * duration pill (bottom-end) leave free. Decorative for a11y: it carries no contentDescription and is not
 * a focusable node; the owning tile merges ", Favorited" into its own label instead (spec §6).
 */
@Composable
fun BoxScope.FavoriteHeartBadge(
    favorite: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = favorite,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
            .align(Alignment.BottomStart)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(HeartBadgeSize)
                .background(HeartScrim, CircleShape)
                // A single stable tag for tests; the badge carries no contentDescription so TalkBack
                // never announces it as its own node (the tile owns the "Favorited" label — spec §6).
                .testTag("tile_favorited"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = FavoriteRed,
                modifier = Modifier.size(HeartBadgeGlyph),
            )
        }
    }
}

/** The universally-legible "favorited" red — used for the filled heart on the tile badge and the viewer. */
val FavoriteRed = Color(0xFFFF4D6D)

private val HeartBadgeSize = 22.dp // subtle indicator chip (down from the old 30dp control)
private val HeartBadgeGlyph = 14.dp
private val HeartScrim = Color(0x66000000) // solid ~40% black disc — reads a red heart on bright frames
