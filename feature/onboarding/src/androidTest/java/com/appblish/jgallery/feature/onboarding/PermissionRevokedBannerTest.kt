package com.appblish.jgallery.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented checks for the W3-07 revoked banner. Copy comes from [PermissionRecoveryCopy]; the
 * permanently-denied hint only appears in that mode, and the action opens the recovery path.
 */
@RunWith(AndroidJUnit4::class)
class PermissionRevokedBannerTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun banner_shows_status_and_action() {
        composeRule.setContent {
            JGalleryTheme { PermissionRevokedBanner(onReRequest = {}) }
        }
        composeRule.onNodeWithText(PermissionRecoveryCopy.BANNER_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(PermissionRecoveryCopy.BANNER_ACTION).assertIsDisplayed()
        // The settings-only hint is NOT shown when the system can still re-prompt.
        composeRule.onNodeWithText(PermissionRecoveryCopy.PERMANENTLY_DENIED_HINT).assertDoesNotExist()
    }

    @Test
    fun permanently_denied_adds_settings_hint() {
        composeRule.setContent {
            JGalleryTheme { PermissionRevokedBanner(onReRequest = {}, permanentlyDenied = true) }
        }
        composeRule.onNodeWithText(PermissionRecoveryCopy.PERMANENTLY_DENIED_HINT).assertIsDisplayed()
    }

    @Test
    fun action_fires_callback() {
        var clicks = 0
        composeRule.setContent {
            JGalleryTheme { PermissionRevokedBanner(onReRequest = { clicks++ }) }
        }
        composeRule.onNodeWithTag("permission_revoked_action").performClick()
        assertEquals(1, clicks)
    }
}
