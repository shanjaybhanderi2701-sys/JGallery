package com.appblish.jgallery

import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.core.ui.window.LocalWindowSizeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented DoD check for the 2-tab shell (design C1-01 item 10; G1-D5 label APP-454): the bar is
 * **Photos · Albums** (the second tab's route id stays `collections`), Photos is the default, both tabs
 * are reachable, and the retired 4-tab ids (`tab_albums`/`tab_search`) are gone (the Albums grid is the
 * second tab's body; Search is a header action). The shell is exercised through its DI-free
 * [JGalleryApp] `tabContent` seam with tagged stubs — routing is what this test owns. The real grid
 * screens are covered by their feature-module tests against the stateless overloads.
 */
@RunWith(AndroidJUnit4::class)
class GalleryShellTest {

    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun twoTabShell_defaultsToPhotos_andBothTabsReachable_noRetiredTabs() {
        // APP-651: the shell reads LocalWindowSizeClass for the orientation writer; provide a Compact
        // (phone) size class so JGalleryApp renders as it does on a phone.
        val compactWindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp))
        composeRule.setContent {
            JGalleryTheme {
                CompositionLocalProvider(LocalWindowSizeClass provides compactWindowSizeClass) {
                    JGalleryApp { tab ->
                        Text(
                            text = "${tab.label} stub",
                            modifier = Modifier.testTag("${tab.route}_screen"),
                        )
                    }
                }
            }
        }

        // Photos is the default tab.
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        // The second tab now reads "Albums" (G1-D5); its route/testTag stay `collections`.
        composeRule.onNodeWithText("Albums").performClick()
        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()

        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()

        // Retired tabs: Albums (now the Collections body) and Search (now a header action) are gone.
        composeRule.onNodeWithTag("tab_albums").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_search").assertDoesNotExist()

        // Compact shows the bottom bar, never the rail.
        composeRule.onNodeWithTag("gallery_tab_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_nav_rail").assertDoesNotExist()
    }

    /**
     * APP-652 DoD: on a **Medium** width (tablet / unfolded foldable) the shell replaces the bottom
     * bar with a leading [com.appblish.jgallery.core.ui.nav.GalleryNavRail] — same two destinations,
     * same route ids (the `tab_<route>` tags are shared with the bar), same VM state — while the
     * bottom `gallery_tab_bar` is gone. Photos stays the default and both tabs remain reachable.
     */
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun mediumWidth_showsNavigationRail_replacingBottomBar_bothTabsReachable() {
        val mediumWindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(800.dp, 1000.dp))
        composeRule.setContent {
            JGalleryTheme {
                CompositionLocalProvider(LocalWindowSizeClass provides mediumWindowSizeClass) {
                    JGalleryApp { tab ->
                        Text(
                            text = "${tab.label} stub",
                            modifier = Modifier.testTag("${tab.route}_screen"),
                        )
                    }
                }
            }
        }

        // The rail is on the leading edge; the bottom bar is gone (container swap, not a fork).
        composeRule.onNodeWithTag("gallery_nav_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("gallery_tab_bar").assertDoesNotExist()

        // Photos is still the default; both tabs reachable via the rail.
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").performClick()
        composeRule.onNodeWithTag("collections_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").performClick()
        composeRule.onNodeWithTag("photos_screen").assertIsDisplayed()
    }
}
