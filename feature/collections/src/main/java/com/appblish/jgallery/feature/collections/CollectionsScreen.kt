package com.appblish.jgallery.feature.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.component.PlaceholderHero
import com.appblish.jgallery.core.ui.component.PlaceholderScreenScaffold
import com.appblish.jgallery.core.ui.component.PreviewChip
import com.appblish.jgallery.core.ui.component.PreviewSectionHeader
import com.appblish.jgallery.core.ui.component.PreviewTile
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Collections tab — an **intentional preview** of the Phase G4 layout (spec §0, §12; design §4).
 * The Smart-Categories row, Places/Memories cards and utility pills mirror the geometry the live,
 * on-device ML features will use, so when G4 lands the previews are replaced with zero reflow. Most
 * surfaces are desaturated and non-tappable with a "Soon" badge — deliberate, not broken.
 *
 * The one **live** utility is "Recover" → the Recycle Bin (W2-E9, spec §7.5): when [onOpenTrash] is
 * provided it renders as a real accent chip that opens Trash. Left null (e.g. in isolation tests) it
 * falls back to the inert preview chip so the placeholder invariant is preserved.
 */
@Composable
fun CollectionsScreen(
    modifier: Modifier = Modifier,
    onOpenTrash: (() -> Unit)? = null,
) {
    PlaceholderScreenScaffold(
        title = "Collections",
        modifier = modifier.testTag("collections_screen"),
    ) {
        PlaceholderHero(
            icon = Icons.Filled.AutoAwesome,
            headline = "Smart Collections",
            body = "JGallery will group your library into Smart Categories, Places and Memories — all " +
                "on your device. Your photos and videos are never uploaded.",
        )

        PreviewSectionHeader("Smart Categories")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SmartCategories.forEach { (icon, label) ->
                PreviewTile(
                    icon = icon,
                    label = label,
                    modifier = Modifier.width(104.dp),
                )
            }
        }

        PreviewSectionHeader("Places & Memories")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PreviewTile(
                icon = Icons.Outlined.Map,
                label = "Places",
                modifier = Modifier.weight(1f),
                aspectRatio = 1.2f,
            )
            PreviewTile(
                icon = Icons.Outlined.Photo,
                label = "Memories",
                modifier = Modifier.weight(1f),
                aspectRatio = 1.2f,
            )
        }

        PreviewSectionHeader("Utilities")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PreviewChip("Duplicates", icon = Icons.Outlined.ContentCopy)
            PreviewChip("Favorites", icon = Icons.Outlined.FavoriteBorder)
            if (onOpenTrash != null) {
                RecoverChip(onClick = onOpenTrash)
            } else {
                PreviewChip("Recover", icon = Icons.Outlined.RestoreFromTrash)
            }
        }
    }
}

/** The live "Recover" utility — a real accent chip that opens the Recycle Bin (W2-E9). */
@Composable
private fun RecoverChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(JGalleryColors.AccentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("collections_recover"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.RestoreFromTrash,
            contentDescription = null,
            tint = JGalleryColors.Accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Recover",
            style = MaterialTheme.typography.bodySmall,
            color = JGalleryColors.Accent,
        )
    }
}

private val SmartCategories: List<Pair<ImageVector, String>> = listOf(
    Icons.Outlined.Landscape to "Beaches",
    Icons.Outlined.Restaurant to "Food",
    Icons.Outlined.Description to "Documents",
    Icons.Outlined.Pets to "Pets",
)
