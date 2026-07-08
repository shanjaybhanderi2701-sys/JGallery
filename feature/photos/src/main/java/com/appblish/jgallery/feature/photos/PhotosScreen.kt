package com.appblish.jgallery.feature.photos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Photos tab. Scaffold placeholder — the time-grouped stream (Today/Yesterday/date headers),
 * pinch-zoom column count, and fast-scroll date bubble are built in the Wave-1 Photos-grid ticket on
 * top of `MediaIndexRepository` + the cached thumbnail pipeline.
 */
@Composable
fun PhotosScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).testTag("photos_screen")) {
        Text(text = "Photos", style = MaterialTheme.typography.displaySmall)
    }
}
