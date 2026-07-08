package com.appblish.jgallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.collections.CollectionsScreen
import com.appblish.jgallery.feature.search.SearchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DoD check for the E5 placeholder tabs (spec §0/§11, design §4): Collections and Search must ship as
 * *intentional previews of their final layout* — a "Soon"-badged scaffold with honest on-device copy —
 * not a generic empty "coming soon" wall. These assert the preview content is actually rendered.
 */
@RunWith(AndroidJUnit4::class)
class PlaceholderScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collections_isIntentionalPreview_withSoonBadges_andOnDeviceCopy() {
        composeRule.setContent { JGalleryTheme { CollectionsScreen() } }

        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()
        // Previews the G4 layout (not a bare "coming soon").
        composeRule.onNodeWithText("Smart Categories").assertIsDisplayed()
        composeRule.onNodeWithText("Places & Memories").assertIsDisplayed()
        // Honest, on-device hero copy (product-integrity commitment, spec §9.3).
        composeRule.onNodeWithText("never uploaded", substring = true).assertIsDisplayed()
        // At least one "Soon" badge marks the previewed-but-not-live surfaces.
        assertTrue(
            "expected at least one 'Soon' badge on the Collections preview",
            composeRule.onAllNodesWithSoon().isNotEmpty(),
        )
    }

    @Test
    fun search_isIntentionalPreview_withVisibleSearchBar_andPreviewRows() {
        composeRule.setContent { JGalleryTheme { SearchScreen() } }

        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()
        // Search bar is visible this phase (spec §0), even though it is inert.
        composeRule.onNodeWithText("Search text, location, time").assertIsDisplayed()
        // Previews the G3 filter rows.
        composeRule.onNodeWithText("By time").assertIsDisplayed()
        composeRule.onNodeWithText("By place").assertIsDisplayed()
        composeRule.onNodeWithText("processed on your device", substring = true).assertIsDisplayed()
        assertTrue(
            "expected at least one 'Soon' badge on the Search preview",
            composeRule.onAllNodesWithSoon().isNotEmpty(),
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithSoon() =
        onAllNodesWithText("Soon").fetchSemanticsNodes()
}
