package com.appblish.jgallery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DoD check for the Wave-1 shell (spec §11): the 4-tab structure exists, Albums is the
 * default, and every tab is reachable. The shell is DI-free, so it runs without a Hilt test runner.
 * Wave-1 feature tickets add the large-library scroll/perf instrumented tests on top of this lane.
 */
@RunWith(AndroidJUnit4::class)
class GalleryShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fourTabShell_defaultsToAlbums_andEveryTabIsReachable() {
        composeRule.setContent {
            JGalleryTheme { JGalleryApp() }
        }

        // Albums is the default tab.
        composeRule.onNodeWithTag("albums_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Photos").performClick()
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Collections").performClick()
        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Albums").performClick()
        composeRule.onNodeWithTag("albums_screen").assertIsDisplayed()
    }
}
