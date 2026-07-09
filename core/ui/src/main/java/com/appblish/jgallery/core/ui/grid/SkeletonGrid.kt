package com.appblish.jgallery.core.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.ui.theme.JGalleryColors
import com.appblish.jgallery.core.ui.theme.JGalleryDimens

/**
 * First-index loading skeleton (design a13): a static grid of neutral placeholder tiles. Only ever
 * seen on the very first open while the E3 index does its one-time enumeration — every later open
 * renders cached rows instantly. Static by design: never a shimmer (design §6).
 */
@Composable
fun SkeletonGrid(
    columns: ColumnCount,
    modifier: Modifier = Modifier,
    tileCount: Int = 30,
) {
    val shape = JGalleryDimens.tileRadius(columns)
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.value),
        modifier = modifier.fillMaxSize().testTag("skeleton_grid"),
        horizontalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
        verticalArrangement = Arrangement.spacedBy(JGalleryDimens.PhotosGutter),
        userScrollEnabled = false,
    ) {
        items((0 until tileCount).toList()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(JGalleryColors.TilePlaceholder, shape),
            )
        }
    }
}
