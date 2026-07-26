package com.appblish.jgallery.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reworked Favorites badge (APP-670, spec `favorites-rework-spec` §3): an indicator, not a control.
 * It renders **only** on favorited items (no outline / unfavorited state) and carries no click target —
 * favoriting from a grid now happens through the selection overflow or the viewer header heart.
 */
@RunWith(AndroidJUnit4::class)
class FavoriteHeartBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun favoritedItem_showsExactlyOneBadge() {
        composeRule.setContent {
            JGalleryTheme {
                Box(Modifier.size(96.dp)) { FavoriteHeartBadge(favorite = true) }
            }
        }
        composeRule.onNodeWithTag("tile_favorited", useUnmergedTree = true).assertExists()
        // The retired interactive/outline "unfavorited" tag must never appear.
        composeRule.onNodeWithTag("tile_unfavorited", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun unfavoritedItem_rendersNoBadge() {
        composeRule.setContent {
            JGalleryTheme {
                Box(Modifier.size(96.dp)) { FavoriteHeartBadge(favorite = false) }
            }
        }
        // No favorited badge on an unfavorited item — the badge is favorited-only.
        composeRule.onAllNodesWithTag("tile_favorited", useUnmergedTree = true).assertCountEquals(0)
    }
}
