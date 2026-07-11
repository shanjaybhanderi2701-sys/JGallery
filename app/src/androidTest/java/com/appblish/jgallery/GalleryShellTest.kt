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
 * Instrumented DoD check for the 2-tab shell (design C1-01 item 10): the bar is **Photos · Collections**,
 * Photos is the default, both tabs are reachable, and the retired Albums/Search tabs are gone (Albums
 * is now the Collections body; Search is a header action). The shell is exercised through its DI-free
 * [JGalleryApp] `tabContent` seam with tagged stubs — routing is what this test owns. The real grid
 * screens are covered by their feature-module tests against the stateless overloads.
 */
@RunWith(AndroidJUnit4::class)
class GalleryShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun twoTabShell_defaultsToPhotos_andBothTabsReachable_noRetiredTabs() {
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

        // Photos is the default tab.
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Collections").performClick()
        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        // Retired tabs: Albums (now the Collections body) and Search (now a header action) are gone.
        composeRule.onNodeWithTag("tab_albums").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_search").assertDoesNotExist()
    }
}
