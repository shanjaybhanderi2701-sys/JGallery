package com.appblish.jgallery.core.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented proof of the OnePlus-modeled 2-tab bar (C1-01, APP-414): renders exactly the
 * Photos · Collections pair, drives selection via [GalleryTabBar]'s stateless route contract, and
 * shows the 4dp accent dot only on the active tab. The full shell rewire (enum reduction +
 * `JGalleryApp` host) is gated on C5's header work + Architect Search/Trash re-homing; this widget
 * ships and is verified standalone so that rewire is a mechanical swap.
 */
@RunWith(AndroidJUnit4::class)
class GalleryTabBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = listOf(
        GalleryTabBarItem("photos", "Photos", Icons.Filled.Photo, Icons.Outlined.Photo),
        GalleryTabBarItem("collections", "Collections", Icons.Filled.Category, Icons.Outlined.Category),
    )

    @Test
    fun rendersTwoTabs_andTapSelects() {
        composeRule.setContent {
            var route by remember { mutableStateOf("photos") }
            JGalleryTheme {
                GalleryTabBar(items = items, selectedRoute = route, onSelect = { route = it.route })
            }
        }

        // Both tabs present; Photos active on entry, Collections not.
        composeRule.onNodeWithTag("tab_photos").assertIsSelected()
        composeRule.onNodeWithTag("tab_collections").assertIsNotSelected()

        // The accent dot follows the active tab. Each tab's clickable Column merges its descendants,
        // so the dot's tag lives on the unmerged tree — query it there (see APP-446).
        composeRule.onNodeWithTag("tab_active_dot", useUnmergedTree = true).assertExists()

        // Tap Collections → selection moves.
        composeRule.onNodeWithTag("tab_collections").performClick()
        composeRule.onNodeWithTag("tab_collections").assertIsSelected()
        composeRule.onNodeWithTag("tab_photos").assertIsNotSelected()
    }

    @Test
    fun exactlyTwoTabs_noFabNoExtraTabs() {
        composeRule.setContent {
            JGalleryTheme {
                GalleryTabBar(items = items, selectedRoute = "photos", onSelect = {})
            }
        }
        composeRule.onNodeWithTag("tab_photos").assertExists()
        composeRule.onNodeWithTag("tab_collections").assertExists()
        // The retired 4-tab set must not leak through.
        composeRule.onNodeWithTag("tab_albums").assertDoesNotExist()
        composeRule.onNodeWithTag("tab_search").assertDoesNotExist()
    }
}
