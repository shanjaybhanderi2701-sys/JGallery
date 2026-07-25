package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec

/**
 * "Sort By" bottom sheet (spec §6): the four cached-index sort keys — File Name / File Path /
 * File Size / Last Modified — crossed with an Ascending / Descending toggle. Operates on the cached
 * index, so applying a choice is a re-sort of already-loaded rows (no re-scan, no IO).
 *
 * Interaction: the sheet stays open while the user tunes key and direction (so a key pick followed by
 * a direction flip is one gesture set); it dismisses on outside tap. Every change is reported via
 * [onSelect] with the full [SortSpec] so the caller can persist immediately.
 */
@Composable
fun SortBySheet(
    current: SortSpec,
    onSelect: (SortSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    JGallerySheet(
        onDismiss = onDismiss,
        title = "Sort by",
        modifier = Modifier.testTag("sort_by_sheet"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            SortKey.entries.forEach { key ->
                SortKeyRow(
                    label = key.label,
                    selected = key == current.key,
                    onClick = { onSelect(current.copy(key = key)) },
                    modifier = Modifier.testTag("sort_key_${key.name}"),
                )
            }

            // Ascending / Descending, as a two-segment toggle under the keys (spec §6 "× Asc/Desc").
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DirectionChip(
                    label = "Ascending",
                    icon = Icons.Filled.ArrowUpward,
                    selected = current.direction == SortDirection.ASCENDING,
                    onClick = { onSelect(current.copy(direction = SortDirection.ASCENDING)) },
                    modifier = Modifier.weight(1f).testTag("sort_dir_ASCENDING"),
                )
                DirectionChip(
                    label = "Descending",
                    icon = Icons.Filled.ArrowDownward,
                    selected = current.direction == SortDirection.DESCENDING,
                    onClick = { onSelect(current.copy(direction = SortDirection.DESCENDING)) },
                    modifier = Modifier.weight(1f).testTag("sort_dir_DESCENDING"),
                )
            }
        }
    }
}

@Composable
private fun SortKeyRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DirectionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Human labels for the sort keys (spec §6 wording). UI-only, so it lives with the sheet. */
private val SortKey.label: String
    get() = when (this) {
        SortKey.FILE_NAME -> "File Name"
        SortKey.FILE_PATH -> "File Path"
        SortKey.FILE_SIZE -> "File Size"
        SortKey.LAST_MODIFIED -> "Last Modified"
    }
