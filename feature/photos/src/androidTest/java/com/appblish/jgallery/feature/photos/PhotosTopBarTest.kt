package com.appblish.jgallery.feature.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * G1-D7 §1–§3 (APP-486): the Photos tab adopts the canonical Search + 3-dot overflow top bar. The
 * loose grid/group icons are gone; Sort by / Column count / Create album / Recycle bin all live in
 * the overflow, and Sort is now wired (it was previously missing on Photos entirely).
 */
@RunWith(AndroidJUnit4::class)
class PhotosTopBarTest {

    @get:Rule val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 9)

    private fun items(count: Int) = (0 until count).map { i ->
        MediaItem(
            id = MediaId("media_$i"),
            displayName = "IMG_$i.jpg",
            type = MediaType.IMAGE,
            bucketId = "bucket_0",
            bucketName = "Camera",
            dateTakenMillis = today.atStartOfDay(zone).toInstant().toEpochMilli() + i,
            dateModifiedMillis = today.atStartOfDay(zone).toInstant().toEpochMilli(),
            sizeBytes = 1_000, width = 400, height = 400, durationMillis = 0, mimeType = "image/jpeg",
        )
    }

    private fun render(onSortChange: (SortSpec) -> Unit = {}) {
        val timeline = buildPhotosTimeline(items(6), zone, today, Locale.UK)
        composeRule.setContent {
            JGalleryTheme {
                PhotosScreen(
                    state = PhotosUiState.Content(timeline),
                    columns = ColumnCount(3),
                    onColumnsChange = {},
                    onSortChange = onSortChange,
                )
            }
        }
    }

    @Test
    fun topBar_isSearchPlusOverflow_withNoLooseGridOrGroupIcons() {
        render()
        composeRule.onNodeWithTag("photos_search_action").assertIsDisplayed()
        composeRule.onNodeWithTag("photos_overflow_action").assertIsDisplayed()
        // The old loose bar icons must be gone — everything folds into the overflow now.
        composeRule.onNodeWithTag("photos_column_count_action").assertDoesNotExist()
        composeRule.onNodeWithTag("photos_group_by_action").assertDoesNotExist()
    }

    @Test
    fun overflow_hostsSortColumnCreateAndRecycleBin() {
        render()
        composeRule.onNodeWithTag("photos_overflow_action").performClick()
        composeRule.onNodeWithTag("photos_menu_sort_by").assertIsDisplayed()
        composeRule.onNodeWithTag("photos_menu_column_count").assertIsDisplayed()
        composeRule.onNodeWithTag("photos_menu_create_album").assertIsDisplayed()
        composeRule.onNodeWithTag("photos_menu_recycle_bin").assertIsDisplayed()
    }

    @Test
    fun sortBy_opensTheSortSheet() {
        render()
        composeRule.onNodeWithTag("photos_overflow_action").performClick()
        composeRule.onNodeWithTag("photos_menu_sort_by").performClick()
        composeRule.onNodeWithTag("sort_by_sheet").assertIsDisplayed()
    }
}
