package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Corner heart affordance overlaid on a media tile (G2 · APP-543). Tapping toggles the item's favorite
 * state in place — the badge is its own clickable, so it consumes the tap and it never falls through to
 * the tile's open / select click. Filled red when [favorite], hollow white otherwise, both on a soft
 * dark scrim so the glyph reads over any frame.
 *
 * [visible] is passed `false` while selection mode is active: the tile's tap then belongs to selection
 * and a competing hit target on the tile would be confusing. Sits in the bottom-start corner — the one
 * corner the format badge (top-start), select badge (top-end) and duration pill (bottom-end) leave free.
 */
@Composable
fun BoxScope.FavoriteHeartBadge(
    favorite: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .align(Alignment.BottomStart)
            .padding(4.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0x33000000))
            .clickable(onClick = onClick)
            .testTag(if (favorite) "tile_favorited" else "tile_unfavorited"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (favorite) "Unfavorite" else "Favorite",
            tint = if (favorite) FavoriteRed else Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The universally-legible "favorited" red — used for the filled heart on both the tile and the viewer. */
val FavoriteRed = Color(0xFFFF4D6D)
