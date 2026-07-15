package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.GroupBy
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * "Group by" bottom sheet (design G1-10): the four time-sectioning dimensions — Day / Month / Year /
 * None — as a single-select list with a `#2D6FF7` checkmark. Re-sectioning is a re-derive of the
 * already-loaded, already-sorted stream (no rescan), so applying a choice is instant.
 *
 * A sibling of [SortBySheet] in the D4/G1 sheet family; the top-bar control cluster (G1-9) hosts the
 * pill that opens it, but the sheet is standalone so any grid screen can drive it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBySheet(
    current: GroupBy,
    onSelect: (GroupBy) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = JGalleryDimens.SheetRadius,
        containerColor = JGalleryColors.Background,
        dragHandle = { GrabHandle() },
        modifier = Modifier.testTag("group_by_sheet"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = "Group by",
                style = MaterialTheme.typography.headlineSmall,
                color = JGalleryColors.Text,
            )

            GroupBy.entries.forEach { option ->
                GroupOptionRow(
                    label = option.label,
                    selected = option == current,
                    onClick = { onSelect(option) },
                    modifier = Modifier.testTag("group_by_${option.name}"),
                )
            }
        }
    }
}

@Composable
private fun GroupOptionRow(
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
            color = if (selected) JGalleryColors.Accent else JGalleryColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = JGalleryColors.Accent,
            )
        }
    }
}

@Composable
private fun GrabHandle() {
    Box(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .size(width = JGalleryDimens.GrabHandleWidth, height = JGalleryDimens.GrabHandleHeight)
                .background(JGalleryColors.Surface, RoundedCornerShape(50)),
        )
    }
}

/** Human labels for the group-by options (design G1-10 wording). UI-only, so it lives with the sheet. */
private val GroupBy.label: String
    get() = when (this) {
        GroupBy.DAY -> "Day"
        GroupBy.MONTH -> "Month"
        GroupBy.YEAR -> "Year"
        GroupBy.NONE -> "None"
    }
