package com.appblish.jgallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appblish.jgallery.core.model.BestEffortKind

/**
 * The honest amber "we're showing you *something*, but not the full thing" banner (design W3-04).
 * Copy is derived from the typed [BestEffortKind] so tile, viewer and Info never disagree about what
 * degraded — this is *graceful degradation, not an error* (the red error card is a different state).
 */
@Composable
internal fun BestEffortBanner(kind: BestEffortKind, modifier: Modifier = Modifier) {
    val message = when (kind) {
        BestEffortKind.RAW_EMBEDDED_JPEG ->
            "Showing the embedded JPEG from this RAW file. Full RAW editing isn't supported."
        BestEffortKind.SVG_PREVIEW ->
            "Showing a best-effort preview of this vector (SVG) image."
    }
    Row(
        modifier = modifier
            .background(AMBER, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = message },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text = message, color = Color.White, fontSize = 13.sp)
    }
}

// W3 design token: amber #E68A17 for best-effort / heads-up states (reused from D2 `color.warn`).
private val AMBER = Color(0xF2E68A17)
