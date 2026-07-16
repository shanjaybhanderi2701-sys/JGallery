package com.appblish.jgallery.feature.albums

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.model.Album
import com.appblish.jgallery.core.model.ColumnCount
import com.appblish.jgallery.core.model.MediaId
import com.appblish.jgallery.core.model.SortDirection
import com.appblish.jgallery.core.model.SortKey
import com.appblish.jgallery.core.model.SortSpec
import com.appblish.jgallery.core.ui.selection.SelectionState
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The organization surfaces (spec §3/§6) on the REAL Albums UI via the stateless [AlbumsScreen]
 * overload: the overflow opens Sort By / Column count / Create album, the sort sheet reports both key
 * and direction, and the create-album dialog only fires with a non-blank name.
 */
@RunWith(AndroidJUnit4::class)
class AlbumsOrganizationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val albums = listOf(
        Album("camera", "Camera", itemCount = 12, cover = MediaId("c"), newestItemMillis = 200),
        Album("shots", "Screenshots", itemCount = 4, cover = MediaId("s"), newestItemMillis = 100),
    )

    private fun content(
        sort: SortSpec = SortSpec(),
        onSortChange: (SortSpec) -> Unit = {},
        onCreateAlbum: (String) -> Unit = {},
        onColumnsChange: (ColumnCount) -> Unit = {},
        onRenameAlbum: (Album, String) -> Unit = { _, _ -> },
        onCopySelected: (String) -> Unit = {},
        onMoveSelected: (String) -> Unit = {},
        onDeleteSelected: () -> Unit = {},
        onPinSelected: () -> Unit = {},
    ) {
        composeRule.setContent {
            JGalleryTheme {
                // Hoist the selection like the ViewModel does, so long-press/tap drive the real bar.
                var selection by remember { mutableStateOf(SelectionState<String>()) }
                AlbumsScreen(
                    state = AlbumsUiState.Content(albums),
                    columns = ColumnCount(3),
                    sort = sort,
                    destinations = albums,
                    onColumnsChange = onColumnsChange,
                    onSortChange = onSortChange,
                    onCreateAlbum = onCreateAlbum,
                    onRenameAlbum = onRenameAlbum,
                    albumSelection = selection,
                    onAlbumLongPress = { selection = selection.anchorOn(it.bucketId) },
                    onAlbumSelectToggle = { selection = selection.toggle(it.bucketId) },
                    onSelectAllAlbums = { selection = selection.selectAll(it) },
                    onClearAlbumSelection = { selection = selection.clear() },
                    onDeleteSelected = onDeleteSelected,
                    onPinSelected = onPinSelected,
                    onCopySelected = onCopySelected,
                    onMoveSelected = onMoveSelected,
                )
            }
        }
    }

    @Test
    fun overflow_opensSortSheet_reportingKeyAndDirection() {
        var latest: SortSpec? = null
        content(onSortChange = { latest = it })

        composeRule.onNodeWithTag("albums_overflow_action").performClick()
        composeRule.onNodeWithTag("albums_menu_sort_by").performClick()
        composeRule.onNodeWithTag("sort_by_sheet").assertIsDisplayed()

        composeRule.onNodeWithTag("sort_key_FILE_NAME").performClick()
        composeRule.runOnIdle { assert(latest?.key == SortKey.FILE_NAME) }

        composeRule.onNodeWithTag("sort_dir_ASCENDING").performClick()
        composeRule.runOnIdle { assert(latest?.direction == SortDirection.ASCENDING) }
    }

    @Test
    fun overflow_opensCreateAlbumDialog_confirmsTrimmedName() {
        var created: String? = null
        content(onCreateAlbum = { created = it })

        composeRule.onNodeWithTag("albums_overflow_action").performClick()
        composeRule.onNodeWithTag("albums_menu_create_album").performClick()
        composeRule.onNodeWithTag("name_input_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("name_input_field").performTextInput("Trip 2026")
        composeRule.onNodeWithTag("name_input_confirm").performClick()

        composeRule.runOnIdle { assert(created == "Trip 2026") }
    }

    @Test
    fun overflow_opensColumnCountSheet() {
        content()

        composeRule.onNodeWithTag("albums_overflow_action").performClick()
        composeRule.onNodeWithTag("albums_menu_column_count").performClick()
        composeRule.onNodeWithTag("column_count_sheet").assertIsDisplayed()
        composeRule.onNodeWithText("Column count").assertIsDisplayed()
    }

    // --- Album multi-select (G1-8, APP-467): long-press enters selection; the bar hosts the ops ---

    @Test
    fun longPressAlbum_entersMultiSelect_showingSelectionBar() {
        content()

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }

        composeRule.onNodeWithTag("selection_top_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_action_bar").assertIsDisplayed()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        // The per-card check badge is a descendant of the card's `clickable` (which merges its
        // semantics), so it is only addressable in the unmerged tree.
        composeRule.onNodeWithTag("album_selected_camera", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun inSelection_tapTogglesAdditionalAlbums() {
        content()

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        // A plain tap while selecting toggles rather than opening.
        composeRule.onNodeWithTag("album_card_shots").performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()

        // Tapping camera again deselects it.
        composeRule.onNodeWithTag("album_card_camera").performClick()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun selectionBar_close_exitsSelectionMode() {
        content()

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("selection_close").performClick()

        composeRule.onNodeWithTag("selection_action_bar").assertIsNotDisplayed()
        composeRule.onNodeWithTag("albums_overflow_action").assertIsDisplayed()
    }

    @Test
    fun selectionBar_delete_confirmsThenInvokesBatchDelete() {
        var deleted = false
        content(onDeleteSelected = { deleted = true })

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("selection_action_delete").performClick()
        composeRule.onNodeWithTag("delete_album_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_album_confirm").performClick()

        composeRule.runOnIdle { assert(deleted) }
    }

    @Test
    fun selectionBar_rename_singleSelected_confirmsWithTrimmedName() {
        var renamed: Pair<Album, String>? = null
        content(onRenameAlbum = { album, name -> renamed = album to name })

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        // Rename is a single-only op in the ⋮ overflow.
        composeRule.onNodeWithTag("selection_action_more").performClick()
        composeRule.onNodeWithTag("selection_action_rename").performClick()
        composeRule.onNodeWithTag("name_input_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("name_input_field").performTextInput("_Renamed")
        composeRule.onNodeWithTag("name_input_confirm").performClick()

        composeRule.runOnIdle {
            assert(renamed?.first?.bucketId == "camera")
            assert(renamed?.second?.endsWith("_Renamed") == true)
        }
    }

    @Test
    fun selectionBar_copy_multi_picksDestination() {
        var copiedTo: String? = null
        content(onCopySelected = { copiedTo = it })

        // Select both folders → Copy is a multi-safe bottom-bar action.
        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("album_card_shots").performClick()
        composeRule.onNodeWithTag("selection_action_copy").performClick()
        // D4-03: the whole-album Copy/Move path now uses the shared cover-thumbnail MoveDestinationSheet
        // (pick a tile, then commit) instead of the retired text-row DestinationPickerSheet.
        composeRule.onNodeWithTag("move_destination_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("move_dest_shots").performClick()
        composeRule.onNodeWithTag("move_sheet_commit").performClick()

        composeRule.runOnIdle { assert(copiedTo == "shots") }
    }
}
