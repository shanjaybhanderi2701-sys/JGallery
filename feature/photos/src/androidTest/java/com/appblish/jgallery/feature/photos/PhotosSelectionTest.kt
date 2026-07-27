package com.appblish.jgallery.feature.photos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.MediaItem
import com.appblish.jgallery.core.model.MediaType
import com.appblish.jgallery.core.ui.selection.BulkAction
import com.appblish.jgallery.core.ui.selection.SelectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * E11 multi-select DoD gate (spec §7.6) on the REAL Photos grid via its stateless overload:
 * selection chrome swaps in, the bulk bar drives the right action, tapping in selection mode
 * toggles instead of opening the viewer, and Copy/Delete open the picker/confirm.
 *
 * Uses [WindowedPhotosTimeline.fromItems] so all items are pre-loaded in windowCache[0] — no async
 * window loading needed for the test fixture.
 */
@RunWith(AndroidJUnit4::class)
class PhotosSelectionTest {

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

    private fun render(
        selection: SelectionState<MediaId>,
        destinations: List<Album> = emptyList(),
        allOrderedIds: List<MediaId> = emptyList(),
        onToggle: (MediaId) -> Unit = {},
        onMediaClick: (MediaItem) -> Unit = {},
        onSelectAll: () -> Unit = {},
        onRunBulk: (BulkAction, String?) -> Unit = { _, _ -> },
    ) {
        val timeline = WindowedPhotosTimeline.fromItems(items(10), zone, today, Locale.UK)
        composeRule.setContent {
            TestGalleryHost {
                PhotosScreen(
                    state = PhotosUiState.Content(timeline),
                    columns = ColumnCount(3),
                    selection = selection,
                    destinations = destinations,
                    allOrderedIds = allOrderedIds,
                    onColumnsChange = {},
                    onMediaClick = onMediaClick,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                    onRunBulk = onRunBulk,
                )
            }
        }
    }

    private fun preset() = SelectionState(setOf(MediaId("media_1"), MediaId("media_2")), anchor = MediaId("media_1"))

    @Test
    fun selectionMode_showsSelectionBarAndBulkActions() {
        render(preset())
        composeRule.onNodeWithTag("selection_top_bar").assertIsDisplayed()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("bulk_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("bulk_move").assertIsDisplayed()
        composeRule.onNodeWithTag("bulk_delete").assertIsDisplayed()
    }

    @Test
    fun tapInSelectionMode_togglesInsteadOfOpeningViewer() {
        var toggled: MediaId? = null
        var opened = false
        render(preset(), onToggle = { toggled = it }, onMediaClick = { opened = true })
        composeRule.onNodeWithContentDescription("IMG_3.jpg").performClick()
        assertEquals(MediaId("media_3"), toggled)
        assertFalse(opened)
    }

    @Test
    fun selectAll_invokesSelectAllCallback() {
        // Virtual select-all (D6): the callback triggers ViewModel.selectAllFiltered() which loads
        // ids via mediaIdsMatching. The stateless screen wires onSelectAll directly to the scaffold.
        var selectAllCalled = false
        render(preset(), onSelectAll = { selectAllCalled = true })
        composeRule.onNodeWithTag("selection_select_all").performClick()
        assertTrue(selectAllCalled)
    }

    @Test
    fun bulkDelete_opensTrashConfirm() {
        render(preset())
        composeRule.onNodeWithTag("bulk_delete").performClick()
        composeRule.onNodeWithTag("bulk_delete_confirm").assertIsDisplayed()
    }

    @Test
    fun bulkCopy_opensDestinationSheet() {
        render(preset(), destinations = listOf(Album("bucket_9", "Trips", 4, MediaId("media_9"), 0L)))
        composeRule.onNodeWithTag("bulk_copy").performClick()
        composeRule.onNodeWithTag("move_destination_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("move_sheet_new_album").assertIsDisplayed()
    }

    @Test
    fun noSelection_showsNormalHeaderNotSelectionBar() {
        var opened = false
        render(SelectionState(), onMediaClick = { opened = true })
        composeRule.onNodeWithTag("selection_top_bar").assertDoesNotExist()
        composeRule.onNodeWithTag("photos_search_action").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("IMG_3.jpg").performClick()
        assertTrue(opened)
    }
}
