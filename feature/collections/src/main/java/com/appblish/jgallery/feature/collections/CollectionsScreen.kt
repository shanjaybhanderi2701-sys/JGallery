package com.appblish.jgallery.feature.collections

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.component.PlaceholderHero
import com.appblish.jgallery.core.ui.component.PlaceholderScreenScaffold
import com.appblish.jgallery.core.ui.component.PreviewChip
import com.appblish.jgallery.core.ui.component.PreviewSectionHeader
import com.appblish.jgallery.core.ui.component.PreviewTile

/**
 * Collections tab — an **intentional preview** of the Phase G4 layout (spec §0, §12; design §4).
 * The Smart-Categories row, Places/Memories cards and utility pills mirror the geometry the live,
 * on-device ML features will use, so when G4 lands the previews are replaced with zero reflow. Every
 * surface is desaturated and non-tappable with a "Soon" badge — deliberate, not broken, no dead ends.
 */
@Composable
fun CollectionsScreen(modifier: Modifier = Modifier) {
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
            PreviewChip("Recover", icon = Icons.Outlined.RestoreFromTrash)
        }
    }
}

private val SmartCategories: List<Pair<ImageVector, String>> = listOf(
    Icons.Outlined.Landscape to "Beaches",
    Icons.Outlined.Restaurant to "Food",
    Icons.Outlined.Description to "Documents",
    Icons.Outlined.Pets to "Pets",
)
