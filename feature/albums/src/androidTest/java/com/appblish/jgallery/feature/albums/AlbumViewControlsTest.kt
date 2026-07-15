package com.appblish.jgallery.feature.albums

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented wiring for the in-album control cluster (G1-9, APP-468): the album-detail overflow opens
 * the shared Sort / Grid-size sheets and the scope toggle reports the chosen [ViewScope].
 */
@RunWith(AndroidJUnit4::class)
class AlbumViewControlsTest {

    @get:Rule val composeRule = createComposeRule()

    private val items = listOf(
        mediaItem("a", MediaType.IMAGE),
        mediaItem("b", MediaType.VIDEO),
    )

    @Test
    fun overflow_opens_the_sort_sheet() {
        composeRule.setContent {
            JGalleryTheme {
                AlbumDetailScreen(
                    title = "Camera",
                    sourceBucketId = "camera",
                    state = AlbumDetailUiState.Content(items),
                    onBack = {},
                    onMediaClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("album_detail_overflow_action").performClick()
        composeRule.onNodeWithTag("album_detail_menu_sort_by").performClick()
        composeRule.onNodeWithTag("sort_by_sheet").assertIsDisplayed()
    }

    @Test
    fun overflow_opens_the_grid_size_sheet() {
        composeRule.setContent {
            JGalleryTheme {
                AlbumDetailScreen(
                    title = "Camera",
                    sourceBucketId = "camera",
                    state = AlbumDetailUiState.Content(items),
                    onBack = {},
                    onMediaClick = {},
                )
            }
        }
        composeRule.onNodeWithTag("album_detail_overflow_action").performClick()
        composeRule.onNodeWithTag("album_detail_menu_grid_size").performClick()
        composeRule.onNodeWithTag("column_count_sheet").assertIsDisplayed()
    }

    @Test
    fun scope_toggle_reports_this_album_only() {
        var scope: ViewScope? = null
        composeRule.setContent {
            JGalleryTheme {
                AlbumDetailScreen(
                    title = "Camera",
                    sourceBucketId = "camera",
                    state = AlbumDetailUiState.Content(items),
                    onBack = {},
                    onMediaClick = {},
                    onScopeChange = { scope = it },
                )
            }
        }
        composeRule.onNodeWithTag("album_detail_overflow_action").performClick()
        composeRule.onNodeWithTag("album_detail_scope_this").performClick()
        assertEquals(ViewScope.THIS_ALBUM, scope)
    }

    private fun mediaItem(id: String, type: MediaType) = MediaItem(
        id = MediaId(id),
        displayName = "$id.jpg",
        type = type,
        bucketId = "camera",
        bucketName = "Camera",
        dateTakenMillis = 0,
        dateModifiedMillis = 0,
        sizeBytes = 0,
        width = 100,
        height = 100,
        durationMillis = if (type == MediaType.VIDEO) 1000 else 0,
        mimeType = "image/jpeg",
    )
}
