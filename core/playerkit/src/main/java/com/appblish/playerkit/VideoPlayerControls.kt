package com.appblish.playerkit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/**
 * The shared player's transport controls (APP-408): a scrub [Slider] over timestamps. The slider
 * consumes its own drags, so scrubbing can never leak into the host pager (design §3 gesture
 * ownership). App-agnostic — the accent [color] is a parameter so JGallery and CalcVault each brand
 * it without the kit depending on either theme. Timestamps use [VideoGestureMath.formatTime].
 */
@Composable
fun VideoPlayerControls(
    positionMs: Long,
    durationMs: Long,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("player_controls")) {
        val playedFraction =
            if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Slider(
            value = scrubFraction ?: playedFraction,
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = Color.White.copy(alpha = 0.38f),
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            val scrubMs = scrubFraction?.let { (it * durationMs).toLong() }
            Text(
                text = VideoGestureMath.formatTime(scrubMs ?: positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = VideoGestureMath.formatTime(durationMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
