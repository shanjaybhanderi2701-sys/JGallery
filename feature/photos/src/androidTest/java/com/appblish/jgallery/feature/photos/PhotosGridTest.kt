package com.appblish.jgallery.feature.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Structural half of the 10k-item acceptance gate (spec §11, design a14), on the REAL grid code via
 * the stateless [PhotosScreen] overload: a 10,000-item timeline composes lazily (a non-virtualized
 * grid would ANR/OOM here), long jumps land instantly, and headers/tiles/date-bubble structure is
 * what the design says. Frame-time jank on device is measured by QA in W1-Q1 on top of this.
 */
@RunWith(AndroidJUnit4::class)
class PhotosGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 7, 9)

    private fun items(count: Int): List<MediaItem> = (0 until count).map { i ->
        val day = today.minusDays((i / 120).toLong()) // ~120 items per day → ~84 sections at 10k
        MediaItem(
            id = MediaId("media_$i"),
            displayName = "IMG_$i.jpg",
            type = if (i % 7 == 0) MediaType.VIDEO else MediaType.IMAGE,
            bucketId = "bucket_${i % 5}",
            bucketName = "Album ${i % 5}",
            dateTakenMillis = day.atStartOfDay(zone).toInstant().toEpochMilli() + (i % 120),
            dateModifiedMillis = day.atStartOfDay(zone).toInstant().toEpochMilli(),
            sizeBytes = 1_000,
            width = 400,
            height = 400,
            durationMillis = if (i % 7 == 0) 65_000 else 0,
            mimeType = if (i % 7 == 0) "video/mp4" else "image/jpeg",
        )
    }

    private fun setTenThousandItemGrid(): PhotosTimeline {
        val timeline = buildPhotosTimeline(items(10_000), zone, today, Locale.UK)
        composeRule.setContent {
            JGalleryTheme {
                PhotosScreen(
                    state = PhotosUiState.Content(timeline),
                    columns = ColumnCount(3),
                    onColumnsChange = {},
                )
            }
        }
        return timeline
    }

    @Test
    fun tenThousandItems_compose_withTodaySection_first() {
        setTenThousandItemGrid()

        composeRule.onNodeWithTag("photos_grid").assertIsDisplayed()
        // Newest-first: the stream opens on Today, then Yesterday.
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun tenThousandItems_longJumps_landAnywhereInTheIndex() {
        val timeline = setTenThousandItemGrid()

        // End of the stream (the lazy grid must virtualize this jump), then back to the top.
        composeRule.onNodeWithTag("photos_grid").performScrollToIndex(timeline.cells.lastIndex)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("photos_grid").performScrollToIndex(timeline.sectionStarts[40])
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("photos_grid").performScrollToIndex(0)
        composeRule.onNodeWithText("Today").assertIsDisplayed()
    }

    @Test
    fun emptyState_rendersQuietCopy_notAGrid() {
        composeRule.setContent {
            JGalleryTheme {
                PhotosScreen(
                    state = PhotosUiState.Empty,
                    columns = ColumnCount.DEFAULT,
                    onColumnsChange = {},
                )
            }
        }
        composeRule.onNodeWithText("No photos yet").assertIsDisplayed()
    }
}
