package com.appblish.jgallery.feature.albums

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
 * Albums tab (default). Scaffold placeholder — the full 3-column album grid with covers + counts,
 * pinch-zoom column count, and the fast-scroll thumb is built in the Wave-1 Albums-grid ticket on
 * top of `MediaIndexRepository`. This screen exists so the 4-tab shell is navigable now.
 */
@Composable
fun AlbumsScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp).testTag("albums_screen")) {
        Text(text = "Albums", style = MaterialTheme.typography.displaySmall)
    }
}
