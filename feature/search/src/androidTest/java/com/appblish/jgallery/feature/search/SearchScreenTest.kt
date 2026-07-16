package com.appblish.jgallery.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/**
 * The Search surface (spec §4.5, design 502-D) via the stateless [SearchScreen] — the UI half of the
 * open→type→results→tap smoke: the search field accepts text, the results grid renders and a tile tap
 * both records the query and routes to the viewer, and the recents / no-results states wire their
 * controls. The debounce→state-machine transitions are proven in [SearchViewModelTest] (JVM).
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun image(name: String) = MediaItem(
        id = MediaId(name),
        displayName = name,
        type = MediaType.IMAGE,
        bucketId = "b",
        bucketName = "Camera",
        dateTakenMillis = 1_700_000_000_000,
        dateModifiedMillis = 0,
        sizeBytes = 10,
        width = 400,
        height = 400,
        durationMillis = 0,
        mimeType = "image/jpeg",
    )

    @Test
    fun typingText_reportsThroughCallback() {
        var typed: String? = null
        composeRule.setContent {
            JGalleryTheme {
                SearchScreen(
                    state = SearchUiState.Empty(emptyList()),
                    text = "",
                    mediaType = MediaFilter.ALL,
                    dateFacet = null,
                    columns = ColumnCount.DEFAULT,
                    onBack = {},
                    onTextChange = { typed = it },
                    onClearText = {},
                    onMediaTypeChange = {},
                    onDateToggle = {},
                    onColumnsChange = {},
                    onCommitQuery = {},
                    onMediaClick = {},
                    onReRunRecent = {},
                    onRemoveRecent = {},
                    onClearRecents = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_input").performTextInput("beach")
        assertEquals("beach", typed)
    }

    @Test
    fun resultsState_showsGrid_andTileTapRecordsThenOpensViewer() {
        var opened: MediaItem? = null
        var recorded = false
        composeRule.setContent {
            JGalleryTheme {
                SearchScreen(
                    state = SearchUiState.Results(
                        items = listOf(image("Beach.jpg"), image("Sunset.jpg")),
                        text = "beach",
                        mediaType = MediaFilter.ALL,
                        dateFacet = null,
                    ),
                    text = "beach",
                    mediaType = MediaFilter.ALL,
                    dateFacet = null,
                    columns = ColumnCount.DEFAULT,
                    onBack = {},
                    onTextChange = {},
                    onClearText = {},
                    onMediaTypeChange = {},
                    onDateToggle = {},
                    onColumnsChange = {},
                    onCommitQuery = { recorded = true },
                    onMediaClick = { opened = it },
                    onReRunRecent = {},
                    onRemoveRecent = {},
                    onClearRecents = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_results_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("search_result_count").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Beach.jpg").performClick()

        assertEquals(MediaId("Beach.jpg"), opened?.id)
        assertEquals(true, recorded)
    }

    @Test
    fun noResultsState_namesTheQuery_andKeepsChipsVisible() {
        composeRule.setContent {
            JGalleryTheme {
                SearchScreen(
                    state = SearchUiState.NoResults(
                        text = "zzz",
                        mediaType = MediaFilter.ALL,
                        dateFacet = null,
                    ),
                    text = "zzz",
                    mediaType = MediaFilter.ALL,
                    dateFacet = null,
                    columns = ColumnCount.DEFAULT,
                    onBack = {},
                    onTextChange = {},
                    onClearText = {},
                    onMediaTypeChange = {},
                    onDateToggle = {},
                    onColumnsChange = {},
                    onCommitQuery = {},
                    onMediaClick = {},
                    onReRunRecent = {},
                    onRemoveRecent = {},
                    onClearRecents = {},
                )
            }
        }

        composeRule.onNodeWithText("No matches for “zzz”").assertIsDisplayed()
        // Both filter rows stay reachable so the user can loosen a facet (never a dead end).
        composeRule.onNodeWithTag("format_filter_chips").assertIsDisplayed()
        composeRule.onNodeWithTag("date_filter_chips").assertIsDisplayed()
    }

    @Test
    fun recents_reRunAndRemoveAndClearAll_wireDistinctActions() {
        var reRun: RecentSearch? = null
        var removed: RecentSearch? = null
        var cleared = false
        val recent = RecentSearch("dogs")
        composeRule.setContent {
            JGalleryTheme {
                SearchScreen(
                    state = SearchUiState.Empty(listOf(recent)),
                    text = "",
                    mediaType = MediaFilter.ALL,
                    dateFacet = null,
                    columns = ColumnCount.DEFAULT,
                    onBack = {},
                    onTextChange = {},
                    onClearText = {},
                    onMediaTypeChange = {},
                    onDateToggle = {},
                    onColumnsChange = {},
                    onCommitQuery = {},
                    onMediaClick = {},
                    onReRunRecent = { reRun = it },
                    onRemoveRecent = { removed = it },
                    onClearRecents = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithTag("recent_chip_dogs").performClick()
        assertEquals(recent, reRun)

        composeRule.onNodeWithContentDescription("Remove dogs").performClick()
        assertEquals(recent, removed)

        composeRule.onNodeWithTag("recents_clear_all").performClick()
        assertEquals(true, cleared)
    }
}
