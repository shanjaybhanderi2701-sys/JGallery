package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.model.FormatBadge

/**
 * The single corner badge a grid tile shows for a special format (design W3-02/03/04). At most one
 * badge ever renders (the caller passes [com.appblish.jgallery.core.model.formatBadge]) so tiles
 * never get noisy. Colours are inlined from the signed-off W3 tokens rather than reaching into the
 * shared `JGalleryColors` object, to keep this addition self-contained.
 *
 * Positioning is the caller's job (a `Box` with `Modifier.align`); the video play-glyph lives at the
 * bottom-end, so format badges are placed at the TOP-start to avoid collision.
 */
@Composable
fun FormatBadgeChip(badge: FormatBadge, modifier: Modifier = Modifier) {
    val (label, bg) = when (badge) {
        FormatBadge.GIF -> "GIF" to BADGE_ACCENT
        FormatBadge.SVG -> "SVG" to BADGE_SUCCESS
        FormatBadge.RAW -> "RAW" to BADGE_DARK
        FormatBadge.PANO -> "PANO" to BADGE_DARK
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics { contentDescription = "$label format" },
    )
}

// W3 design tokens (spec §"design language"): accent #2D6FF7 (GIF), success #23A55A (SVG),
// neutral-dark translucent (RAW / PANO — matches the video duration badge scrim).
private val BADGE_ACCENT = Color(0xFF2D6FF7)
private val BADGE_SUCCESS = Color(0xFF23A55A)
private val BADGE_DARK = Color(0x99000000)
