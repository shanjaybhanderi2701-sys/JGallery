package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Per-tab header (design §3): 32sp/800 title on the left, tab actions (column count, camera, …) on
 * the right. Kept dumb — features decide which actions exist per wave.
 */
@Composable
fun GalleryTabHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = JGalleryColors.Text,
        )
        Spacer(modifier = Modifier.weight(1f))
        actions()
    }
}
