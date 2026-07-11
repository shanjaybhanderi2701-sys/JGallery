package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * The top-bar format filter row (design C1-06, item 3): one consistent single-select chip row —
 * **All · Photos · Videos · GIFs** — that lives directly under the header on both the Photos and the
 * Collections/Albums tabs, giving one mental model across both. [MediaFilter.ALL] is the default; the
 * selected chip gets an accent fill + white glyph, the rest are quiet grey pills (38dp tall, 20dp
 * radius). Selecting a chip re-filters the in-memory index instantly — no rescan (the caller owns the
 * filtering).
 *
 * An optional [counts] map surfaces the "Videos 512" readout on a chip when the caller has cheap
 * counts to show; omitted chips render label-only.
 */
@Composable
fun FormatFilterChips(
    selected: MediaFilter,
    onSelect: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
    counts: Map<MediaFilter, Int>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("format_filter_chips"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaFilter.ORDER.forEach { filter ->
            FormatFilterChip(
                filter = filter,
                selected = filter == selected,
                count = counts?.get(filter),
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun FormatFilterChip(
    filter: MediaFilter,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
) {
    val background = if (selected) JGalleryColors.Accent else JGalleryColors.Surface
    val content = if (selected) JGalleryColors.OnAccent else JGalleryColors.Text
    val glyph = filter.glyph()

    Row(
        modifier = Modifier
            .height(38.dp)
            .defaultMinSize(minWidth = 48.dp)
            .background(background, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .semantics { this.selected = selected }
            .testTag("filter_chip_${filter.name}"),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = filter.label(),
            color = content,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        // Type-count readout ("Videos 512") makes the active filter unmistakable when the caller has it.
        if (count != null) {
            Text(
                text = count.toString(),
                color = content.copy(alpha = if (selected) 0.85f else 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** User-facing chip label. */
private fun MediaFilter.label(): String = when (this) {
    MediaFilter.ALL -> "All"
    MediaFilter.PHOTOS -> "Photos"
    MediaFilter.VIDEOS -> "Videos"
    MediaFilter.GIFS -> "GIFs"
}

/** Leading type glyph; All is label-only. */
private fun MediaFilter.glyph(): ImageVector? = when (this) {
    MediaFilter.ALL -> null
    MediaFilter.PHOTOS -> Icons.Outlined.Image
    MediaFilter.VIDEOS -> Icons.Outlined.Videocam
    MediaFilter.GIFS -> Icons.Outlined.Gif
}
