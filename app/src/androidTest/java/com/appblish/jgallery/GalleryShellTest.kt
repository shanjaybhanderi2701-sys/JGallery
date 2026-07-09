package com.appblish.jgallery

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DoD check for the Wave-1 shell (spec §11): the 4-tab structure exists, Albums is the
 * default, and every tab is reachable. Since E6 the Albums/Photos tabs are Hilt-backed grids, so the
 * shell is exercised through its DI-free [JGalleryApp] `tabContent` seam with tagged stubs — routing
 * is what this test owns. The real grid screens are covered by their feature-module tests against
 * the stateless overloads (e.g. `PhotosGridTest`'s 10k-item fixture).
 */
@RunWith(AndroidJUnit4::class)
class GalleryShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fourTabShell_defaultsToAlbums_andEveryTabIsReachable() {
        composeRule.setContent {
            JGalleryTheme {
                JGalleryApp { tab ->
                    Text(
                        text = "${tab.label} stub",
                        modifier = Modifier.testTag("${tab.route}_screen"),
                    )
                }
            }
        }

        // Albums is the default tab.
        composeRule.onNodeWithTag("albums_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Collections").performClick()
        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Albums").performClick()
        composeRule.onNodeWithTag("albums_screen").assertIsDisplayed()
    }
}
