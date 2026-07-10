package com.appblish.jgallery.core.ui.format

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/** Stable test tags so the instrumented suite can assert the right placeholder renders (DoD §11). */
object MediaPlaceholderTags {
    const val UNSUPPORTED_TILE = "placeholder_unsupported_tile"
    const val CORRUPT_TILE = "placeholder_corrupt_tile"
    const val VIEWER_CARD = "placeholder_viewer_card"
}

/**
 * Grid-tile placeholder for a [MediaDecodeState] that is not [MediaDecodeState.Rendered]. Fills the
 * tile's square exactly like a real thumbnail so the grid never reflows around it (spec §1); the two
 * degraded states are colour-coded so the user can tell "we can't render this" (neutral) from
 * "this file is damaged" (warm-red) at a glance (spec §8). [MediaDecodeState.Rendered] /
 * [MediaDecodeState.BestEffort] draw nothing here — those show the real preview, handled upstream.
 */
@Composable
fun MediaDecodeTilePlaceholder(state: MediaDecodeState, modifier: Modifier = Modifier) {
    when (state) {
        is MediaDecodeState.Unsupported -> DegradedTile(
            fill = JGalleryColors.UnsupportedFill,
            icon = Icons.Outlined.InsertDriveFile,
            tint = JGalleryColors.TextSecondary,
            extension = state.extension,
            testTag = MediaPlaceholderTags.UNSUPPORTED_TILE,
            modifier = modifier,
        )
        is MediaDecodeState.Corrupt -> DegradedTile(
            fill = JGalleryColors.CorruptFill,
            icon = Icons.Outlined.BrokenImage,
            tint = JGalleryColors.Danger,
            extension = state.extension,
            testTag = MediaPlaceholderTags.CORRUPT_TILE,
            modifier = modifier,
        )
        else -> Unit
    }
}

@Composable
private fun DegradedTile(
    fill: Color,
    icon: ImageVector,
    tint: Color,
    extension: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fill)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // the extension label below carries the semantics
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            if (extension.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = extension.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Full-screen viewer card shared by the *Unsupported* (W3-01) and *Corrupt* (W3-06) states — the
 * spec has W3-06 explicitly reuse this card via the [MediaDecodeState.Corrupt] variant. Dark viewer
 * chrome: a glyph, an honest headline, `filename · format · size`, a state chip, and caller-supplied
 * primary/secondary actions. The bottom action bar (Delete/Move/Rename) stays live above this — this
 * only replaces the image canvas, so the file remains fully operable and nothing crashes (spec §8).
 *
 * The primary action differs by state (Open-with for unsupported, Delete for corrupt) so callers pass
 * [primaryLabel]/[onPrimary]; [onInfo] is the always-present secondary.
 */
@Composable
fun ViewerUnsupportedCard(
    state: MediaDecodeState,
    fileName: String,
    formatLabel: String,
    sizeLabel: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val corrupt = state is MediaDecodeState.Corrupt
    val accent = if (corrupt) JGalleryColors.Danger else JGalleryColors.TextSecondary
    val icon = if (corrupt) Icons.Outlined.BrokenImage else Icons.Outlined.InsertDriveFile
    val headline = if (corrupt) "This file appears to be damaged" else "Can't preview this file"
    val reassurance = if (corrupt) {
        "The file couldn't be read — it may be truncated or empty."
    } else {
        "This format can't be shown here. Your file is safe and untouched."
    }
    val chip = if (corrupt) "Unreadable · decode failed" else "Unsupported format"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JGalleryColors.ViewerCanvas)
            .testTag(MediaPlaceholderTags.VIEWER_CARD),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JGalleryColors.ViewerSheet)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = listOf(fileName, formatLabel, sizeLabel).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB8BCC4),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reassurance,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9AA0A8),
                textAlign = TextAlign.Center,
            )
            StateChip(text = chip, tint = accent)
            Spacer(Modifier.height(4.dp))
            CardActionRow(
                primaryLabel = primaryLabel,
                onPrimary = onPrimary,
                onInfo = onInfo,
                primaryTint = if (corrupt) JGalleryColors.Danger else JGalleryColors.Accent,
            )
        }
    }
}

@Composable
private fun StateChip(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
        )
    }
}

@Composable
private fun CardActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onInfo: () -> Unit,
    primaryTint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardButton(
            label = primaryLabel,
            fill = primaryTint,
            content = Color.White,
            onClick = onPrimary,
            testTag = "placeholder_card_primary",
            modifier = Modifier.weight(1f),
        )
        CardButton(
            label = "Info",
            fill = Color(0x1FFFFFFF),
            content = Color.White,
            onClick = onInfo,
            testTag = "placeholder_card_info",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CardButton(
    label: String,
    fill: Color,
    content: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
        )
    }
}
