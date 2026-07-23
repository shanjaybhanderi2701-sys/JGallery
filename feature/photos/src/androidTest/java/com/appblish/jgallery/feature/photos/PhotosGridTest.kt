package com.appblish.jgallery.feature.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    fun fastScrollThumb_appearsOnScroll_withDeepEnoughContent() {
        // 10k items → deepEnough is satisfied; swipe triggers isScrollInProgress → thumb visible.
        // Don't waitForIdle after swipe: that would advance past the 1.5s linger, hiding the thumb.
        setTenThousandItemGrid()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag("photos_grid").performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeBy(300) // past fade-in, inside 1.5s linger window
        composeRule.onNodeWithTag("fast_scroll_thumb").assertIsDisplayed()

        // Fling may take ~1s to settle; then AUTO_HIDE_MS=1500ms; then fade-out ~300ms. 5s covers all.
        composeRule.mainClock.advanceTimeBy(5000)
        composeRule.onNodeWithTag("fast_scroll_thumb").assertDoesNotExist()
    }

    /**
     * APP-592 regression: the context bubble must LAY OUT and be visible during an active drag, and
     * fade away after release. The old bug drew the pill inside the 48dp touch column while it applied
     * `padding(end = 56dp)`, collapsing its content to zero width — [assertIsDisplayed] fails on a
     * zero-size node, so this test fails on the pre-fix code and passes only once the pill lives in a
     * full-width overlay. APP-496 only unit-tested the label string, never the render, so this is the
     * layer that was missing.
     */
    @Test
    fun fastScrollBubble_appearsDuringDrag_thenFadesOnRelease() {
        setTenThousandItemGrid()
        composeRule.mainClock.autoAdvance = false

        // Grab the handle and hold mid-drag — do NOT release, so `dragging` stays true. topCenter →
        // center is far past touch slop, so detectVerticalDragGestures reports an active vertical drag.
        composeRule.onNodeWithTag("fast_scroll_track").performTouchInput {
            down(topCenter)
            moveTo(center)
        }
        composeRule.mainClock.advanceTimeBy(300) // past the bubble fade-in

        // The pill is actually on screen with non-zero size (the exact thing the old bug broke)...
        composeRule.onNodeWithTag("fast_scroll_bubble").assertIsDisplayed()
        // ...and carries the sort-aware context label ("Month 2026 · item N of 10,000" for the default
        // Last-Modified sort). All fixture dates are in 2026, so the year proves a real readout.
        composeRule.onNodeWithTag("fast_scroll_bubble").assertTextContains("2026", substring = true)

        // Release → `dragging` flips false → the bubble fades out (design §3), unlike the thumb which
        // lingers AUTO_HIDE_MS. 1s covers the fade-out.
        composeRule.onNodeWithTag("fast_scroll_track").performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("fast_scroll_bubble").assertDoesNotExist()
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
