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
 * the tile's open / select click. Filled red when [favorite], hollow white otherwise, both on a solid
 * dark scrim so the glyph reads over any frame.
 *
 * [visible] is passed `false` while selection mode is active: the tile's tap then belongs to selection
 * and a competing hit target on the tile would be confusing. Sits in the bottom-start corner — the one
 * corner the format badge (top-start), select badge (top-end) and duration pill (bottom-end) leave free.
 *
 * UX sign-off redlines (APP-543 · issue bc4bc409) baked in here:
 * - **Hit target**: the tap area is [HeartHitTarget] (≥48dp) even though the visible chip stays 30dp,
 *   so the heart clears the accessibility minimum without moving or growing the chrome.
 * - **Scrim legibility**: a solid ~40% black disc ([HeartScrim]) instead of the old 20% wash, so the
 *   hollow white heart reads over bright / high-key frames.
 * - **Density**: [columns] drives [favoriteHeartVisible] — the hollow "unfavorited" heart is suppressed
 *   on the densest grids where the fixed badge would crowd shrunken tiles; the filled heart always shows.
 */
@Composable
fun BoxScope.FavoriteHeartBadge(
    favorite: Boolean,
    visible: Boolean,
    columns: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    // Redline 3: the hollow heart is discovery chrome — drop it on dense grids (mirrors VideoOverlay
    // hiding its duration pill). The filled red heart carries state, so it must never vanish.
    if (!favoriteHeartVisible(favorite = favorite, columns = columns)) return
    Box(
        // Redline 1: a transparent ≥48dp hit target owns the tap. The visible 30dp chip is pinned to the
        // bottom-start corner (unchanged 4dp inset); the extra hit area only grows inward — toward the
        // tile centre, where mis-taps against neighbouring chrome actually happen.
        modifier = modifier
            .align(Alignment.BottomStart)
            .size(HeartHitTarget)
            .clickable(onClick = onClick)
            .testTag(if (favorite) "tile_favorited" else "tile_unfavorited"),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .size(HeartChipSize)
                .clip(CircleShape)
                // Redline 2: a solid, stronger scrim disc so the white hollow heart reads over any frame.
                .background(HeartScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (favorite) "Unfavorite" else "Favorite",
                tint = if (favorite) FavoriteRed else Color.White,
                modifier = Modifier.size(HeartGlyphSize),
            )
        }
    }
}

/**
 * Pure visibility rule for the tile heart (UX redline 3). A *favorited* item always shows its heart — the
 * filled glyph is state, not chrome. An *unfavorited* item's hollow heart is a discovery affordance, so it
 * is hidden once the grid reaches [hideUnfavoritedAtColumns] columns, where the fixed-size badge would
 * crowd the shrunken tiles. Extracted so the density rule is unit-testable without a Compose harness.
 */
fun favoriteHeartVisible(favorite: Boolean, columns: Int, hideUnfavoritedAtColumns: Int = 6): Boolean =
    favorite || columns < hideUnfavoritedAtColumns

/** The universally-legible "favorited" red — used for the filled heart on both the tile and the viewer. */
val FavoriteRed = Color(0xFFFF4D6D)

private val HeartHitTarget = 48.dp
private val HeartChipSize = 30.dp
private val HeartGlyphSize = 18.dp
private val HeartScrim = Color(0x66000000) // solid ~40% black disc — reads a white heart on bright frames
