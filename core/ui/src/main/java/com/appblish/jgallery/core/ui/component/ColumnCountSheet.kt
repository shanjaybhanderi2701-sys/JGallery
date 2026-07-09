package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * "Column count" bottom sheet (design W1-06): a 2–6 selector that mirrors the pinch value — picking
 * here and pinching write the same per-tab persisted count. iOS-style sheet per the token set: 26dp
 * top radius, 44x5dp grab handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnCountSheet(
    current: ColumnCount,
    onSelect: (ColumnCount) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = JGalleryDimens.SheetRadius,
        containerColor = JGalleryColors.Background,
        dragHandle = { GrabHandle() },
        modifier = Modifier.testTag("column_count_sheet"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(
                text = "Column count",
                style = MaterialTheme.typography.headlineSmall,
                color = JGalleryColors.Text,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                (ColumnCount.MIN..ColumnCount.MAX).forEach { count ->
                    val selected = count == current.value
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = if (selected) JGalleryColors.Accent else JGalleryColors.Surface,
                                shape = CircleShape,
                            )
                            .clickable {
                                onSelect(ColumnCount(count))
                                onDismiss()
                            }
                            .testTag("column_count_option_$count"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) JGalleryColors.OnAccent else JGalleryColors.Text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrabHandle() {
    Box(modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        Box(
            modifier = Modifier
                .size(width = JGalleryDimens.GrabHandleWidth, height = JGalleryDimens.GrabHandleHeight)
                .background(JGalleryColors.Surface, CircleShape),
        )
    }
}
