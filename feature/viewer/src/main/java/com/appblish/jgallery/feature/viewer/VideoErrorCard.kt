package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.ui.theme.JGalleryColors

/**
 * W3-05 video-codec-unsupported card (spec §8). Sits over the (dimmed) poster: a red-accented panel
 * that makes clear the FILE is fine but this device can't decode it, names the codec, and offers
 * "Open with" (hand the clip to a player that can) plus "Info". This is the graceful §8 fallback —
 * the viewer surfaces it instead of crashing on an undecodable clip.
 */
@Composable
internal fun VideoErrorCard(
    error: VideoError,
    onOpenWith: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)) // dim the poster behind the card
            .testTag("viewer_video_error"),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = JGalleryColors.ViewerSheet,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = JGalleryColors.Destructive,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Can't play this video",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = error.bodyText(),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                (error as? VideoError.Unsupported)?.codecLabel?.let { label ->
                    Spacer(Modifier.height(14.dp))
                    CodecChip(label)
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onOpenWith,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JGalleryColors.Accent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.testTag("viewer_video_open_with"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Open with")
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        onClick = onInfo,
                        modifier = Modifier.testTag("viewer_video_error_info"),
                    ) {
                        Text("Info", color = Color.White)
                    }
                }
            }
        }
    }
}

/** The red "CODEC · unsupported" pill under the title. Absent when Media3 didn't name the format. */
@Composable
private fun CodecChip(label: String) {
    Text(
        text = "$label · unsupported",
        color = JGalleryColors.Destructive,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = JGalleryColors.Destructive.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
            )
            .background(
                color = JGalleryColors.Destructive.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .testTag("viewer_video_error_codec"),
    )
}

private fun VideoError.bodyText(): String = when (this) {
    is VideoError.Unsupported ->
        "The file is fine, but this device can't decode its video codec."
    VideoError.Playback ->
        "Something went wrong while playing this file."
}
