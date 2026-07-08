package com.appblish.jgallery.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.appblish.jgallery.core.model.ColumnCount

/** Shape/spacing tokens from the signed-off Wave 1 spec (§1). */
object JGalleryDimens {
    val AlbumCoverRadius = RoundedCornerShape(16.dp)
    val SheetRadius = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp) // iOS-style bottom sheets
    val ButtonRadius = RoundedCornerShape(28.dp) // 56dp-high pills
    val ButtonHeight = 56.dp
    val NavHeight = 86.dp
    val PhotosGutter = 4.dp
    val AlbumsGutter = 12.dp
    val GrabHandleWidth = 44.dp
    val GrabHandleHeight = 5.dp

    /** Tile corner radius scales down as columns get denser (spec §1: 14 / 12 / 9dp). */
    fun tileRadius(columns: ColumnCount): RoundedCornerShape = when (columns.value) {
        2, 3 -> RoundedCornerShape(14.dp)
        4, 5 -> RoundedCornerShape(12.dp)
        else -> RoundedCornerShape(9.dp)
    }
}
