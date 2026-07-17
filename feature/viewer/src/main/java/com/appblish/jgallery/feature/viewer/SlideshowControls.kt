package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * Slideshow controls overlay (APP-544, G2 · auto-play). A single compact pill floated at the bottom of
 * the immersive canvas while a slideshow is running: a position counter, a Pause/Resume toggle, and a
 * Stop that exits the slideshow back to normal viewing. Chrome (header + action bar) is hidden while the
 * slideshow runs, so this is the only affordance on screen — deliberately minimal for a lean-back mode.
 *
 * [position]/[count] are 1-based for display. [paused] flips the primary button between Pause and Resume.
 */
@Composable
internal fun SlideshowControls(
    position: Int,
    count: Int,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 24.dp)
            .background(JGalleryColors.ViewerSheet.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("slideshow_controls"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "$position / $count",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(start = 8.dp, end = 4.dp)
                .testTag("slideshow_position"),
        )
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier.testTag("slideshow_toggle"),
        ) {
            Icon(
                imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (paused) "Resume slideshow" else "Pause slideshow",
                tint = Color.White,
            )
        }
        IconButton(
            onClick = onStop,
            modifier = Modifier.testTag("slideshow_stop"),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Stop slideshow",
                tint = Color.White,
            )
        }
    }
}
