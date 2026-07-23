package com.appblish.jgallery.core.ui.selection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Corner select badge overlaid on a media tile while selection mode is active. Filled accent check
 * when [selected], hollow ring otherwise — the affordance that tells the user tapping toggles.
 */
@Composable
fun BoxScope.SelectionCheckBadge(selected: Boolean, active: Boolean) {
    if (!active) return
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0x66000000)
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(bg)
            .testTag(if (selected) "tile_selected" else "tile_unselected"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.Check else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (selected) "Selected" else "Not selected",
            tint = Color.White,
            modifier = Modifier.size(if (selected) 16.dp else 22.dp),
        )
    }
}

/** Inset-scale factor for a selected tile (design: selected item shrinks slightly to reveal a gap). */
@Composable
fun rememberTileSelectScale(selected: Boolean): Float =
    animateFloatAsState(if (selected) 0.86f else 1f, label = "tileSelectScale").value

/** Convenience: apply the animated inset scale to a tile. */
fun Modifier.tileSelectScale(scale: Float): Modifier = this.scale(scale)
