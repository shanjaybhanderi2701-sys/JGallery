package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The video affordance overlaid on a grid tile (design C1-08, item 8): a crisp centred play disc plus
 * a bottom-end duration pill, so a video is instantly distinguishable from a photo in any grid —
 * legible over both light and busy frames without dimming the whole tile. It replaces the flat W1
 * `.play` badge everywhere a video tile is drawn.
 *
 * Everything here is *drawn over* the thumbnail (never baked into it), and both inputs — duration and
 * "is this a video" — come from the cached index, so scrolling never probes a frame:
 *
 * - **Contrast scrim**: a soft radial darken under the disc (≈34 % at centre → 0 at 62 % radius,
 *   redline callout 3) that guarantees the glyph reads without flat-tinting the whole thumbnail.
 * - **Play disc**: a semi-transparent dark disc (rgba 18,18,20,.42) with a 1.6dp white ring and a
 *   white triangle. It is sized as ≈34 % of the tile ([DiscTileFraction]) so it stays proportional
 *   from a 2-column grid up to a 6-column grid, and the ring keeps it crisp when downscaled.
 * - **Duration pill**: bottom-end, rgba(15,17,22,.66) / 11sp-700 white, present so a still frame reads
 *   as a video even before the eye finds the disc. Hidden at the densest ([hideDurationAtColumns])
 *   grids to avoid clutter — the disc alone still signals "video".
 *
 * @param durationMillis clip length from the index; the pill renders it as "m:ss" / "h:mm:ss".
 * @param columns the current grid column count — drives whether the duration pill is shown.
 */
@Composable
fun VideoOverlay(
    durationMillis: Long,
    columns: Int,
    modifier: Modifier = Modifier,
    hideDurationAtColumns: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = "Video" }
            // Radial contrast scrim under the disc: darkens the centre just enough to guarantee the
            // glyph is legible, fading fully to transparent well before the tile edges (callout 3).
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to ScrimCenter,
                            ScrimFadeStop to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxOf(size.width, size.height) / 2f,
                    ),
                )
            },
    ) {
        // Centred play disc, sized as a fraction of the tile so it scales with column density.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(DiscTileFraction)
                .aspectRatio(1f)
                .background(DiscFill, CircleShape)
                .border(DiscRing, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.fillMaxSize(0.62f),
            )
        }

        if (columns < hideDurationAtColumns) {
            Text(
                text = formatVideoDuration(durationMillis),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(DurationPill, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** "1:05" / "12:07" / "1:23:45" — the video duration badge readout (design C1-08). */
fun formatVideoDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

// C1-08 redline tokens. Disc ≈34 % of tile width; ring 1.6dp; fills from the redline rgba values.
private const val DiscTileFraction = 0.34f
private val DiscRing = 1.6.dp
private val DiscFill = Color(0x6B12121A)          // rgba(18,18,20,.42)
private val DurationPill = Color(0xA80F1116)      // rgba(15,17,22,.66)
private val ScrimCenter = Color(0x57000000)       // ≈34 % black at the centre of the radial scrim
private const val ScrimFadeStop = 0.62f           // fully transparent by 62 % of the radius
