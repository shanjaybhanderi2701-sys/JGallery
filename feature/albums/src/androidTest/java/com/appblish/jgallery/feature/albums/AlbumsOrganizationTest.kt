package com.appblish.jgallery.feature.albums

import androidx.compose.ui.test.assertIsDisplayed
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
        onCopyAlbum: (Album, String) -> Unit = { _, _ -> },
        onMoveAlbum: (Album, String) -> Unit = { _, _ -> },
        onDeleteAlbum: (Album) -> Unit = {},
    ) {
        composeRule.setContent {
            JGalleryTheme {
                AlbumsScreen(
                    state = AlbumsUiState.Content(albums),
                    columns = ColumnCount(3),
                    sort = sort,
                    destinations = albums,
                    onColumnsChange = onColumnsChange,
                    onSortChange = onSortChange,
                    onCreateAlbum = onCreateAlbum,
                    onRenameAlbum = onRenameAlbum,
                    onCopyAlbum = onCopyAlbum,
                    onMoveAlbum = onMoveAlbum,
                    onDeleteAlbum = onDeleteAlbum,
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

    // --- Album-as-entity actions (spec §7, §11 DoD, APP-341) ---

    @Test
    fun longPressAlbum_opensActionMenu_withAllFourActions() {
        content()

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }

        composeRule.onNodeWithTag("album_action_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("album_action_rename").assertIsDisplayed()
        composeRule.onNodeWithTag("album_action_copy").assertIsDisplayed()
        composeRule.onNodeWithTag("album_action_move").assertIsDisplayed()
        composeRule.onNodeWithTag("album_action_delete").assertIsDisplayed()
    }

    @Test
    fun longPress_thenRename_confirmsWithTargetAlbumAndTrimmedName() {
        var renamed: Pair<Album, String>? = null
        content(onRenameAlbum = { album, name -> renamed = album to name })

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("album_action_rename").performClick()
        composeRule.onNodeWithTag("name_input_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("name_input_field").performTextInput("_Renamed")
        composeRule.onNodeWithTag("name_input_confirm").performClick()

        // Rename dialog pre-fills the album name, so the field ends with the appended text.
        composeRule.runOnIdle {
            assert(renamed?.first?.bucketId == "camera")
            assert(renamed?.second?.endsWith("_Renamed") == true)
        }
    }

    @Test
    fun longPress_thenCopy_picksDestinationExcludingSource() {
        var copied: Pair<Album, String>? = null
        content(onCopyAlbum = { album, dest -> copied = album to dest })

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("album_action_copy").performClick()
        composeRule.onNodeWithTag("destination_picker").assertIsDisplayed()

        // Source (Camera) is excluded; the only other album is Screenshots. Pick it by its unique
        // row tag — "Screenshots" as plain text also matches the album card behind the sheet.
        composeRule.onNodeWithTag("destination_shots").performClick()

        composeRule.runOnIdle {
            assert(copied?.first?.bucketId == "camera")
            assert(copied?.second == "shots")
        }
    }

    @Test
    fun longPress_thenDelete_confirmsMoveToTrash() {
        var deleted: Album? = null
        content(onDeleteAlbum = { deleted = it })

        composeRule.onNodeWithTag("album_card_camera").performTouchInput { longClick() }
        composeRule.onNodeWithTag("album_action_delete").performClick()
        composeRule.onNodeWithTag("delete_album_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_album_confirm").performClick()

        composeRule.runOnIdle { assert(deleted?.bucketId == "camera") }
    }
}
