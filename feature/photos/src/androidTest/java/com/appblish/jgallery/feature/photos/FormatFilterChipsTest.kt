package com.appblish.jgallery.feature.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaFilter
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Item 3 (design C1-06) on the real Photos surface via the stateless [PhotosScreen]: the format chip
 * row renders under the header, selecting a chip reports through the callback, and an empty filtered
 * result shows the filter-scoped empty state (keeping the chips reachable).
 */
@RunWith(AndroidJUnit4::class)
class FormatFilterChipsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 9)

    private fun timeline(count: Int): PhotosTimeline {
        val items = (0 until count).map { i ->
            MediaItem(
                id = MediaId("m_$i"),
                displayName = "IMG_$i.jpg",
                type = if (i % 3 == 0) MediaType.VIDEO else MediaType.IMAGE,
                bucketId = "b",
                bucketName = "Album",
                dateTakenMillis = today.atStartOfDay(zone).toInstant().toEpochMilli() + i,
                dateModifiedMillis = 0,
                sizeBytes = 0,
                width = 400,
                height = 400,
                durationMillis = if (i % 3 == 0) 65_000 else 0,
                mimeType = if (i % 3 == 0) "video/mp4" else "image/jpeg",
            )
        }
        return buildPhotosTimeline(items, zone, today, Locale.UK)
    }

    @Test
    fun chipRow_isShown_andReportsSelection() {
        var picked: MediaFilter? = null
        composeRule.setContent {
            JGalleryTheme {
                PhotosScreen(
                    state = PhotosUiState.Content(timeline(30), MediaFilter.ALL),
                    columns = ColumnCount(3),
                    onColumnsChange = {},
                    filter = MediaFilter.ALL,
                    onFilterChange = { picked = it },
                )
            }
        }

        composeRule.onNodeWithTag("format_filter_chips").assertIsDisplayed()
        composeRule.onNodeWithTag("filter_chip_VIDEOS").performClick()
        composeRule.runOnIdle { assertEquals(MediaFilter.VIDEOS, picked) }
    }

    @Test
    fun emptyFilteredResult_showsScopedEmptyState_withChipsStillShown() {
        composeRule.setContent {
            JGalleryTheme {
                PhotosScreen(
                    // Non-empty library but an empty (filtered) timeline → scoped empty state.
                    state = PhotosUiState.Content(buildPhotosTimeline(emptyList(), zone, today, Locale.UK), MediaFilter.VIDEOS),
                    columns = ColumnCount(3),
                    onColumnsChange = {},
                    filter = MediaFilter.VIDEOS,
                )
            }
        }

        composeRule.onNodeWithTag("format_filter_chips").assertIsDisplayed()
        composeRule.onNodeWithText("No videos").assertIsDisplayed()
    }
}
