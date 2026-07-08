package com.appblish.jgallery.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.onboarding.screen.OnboardingLanguageScreen
import com.appblish.jgallery.feature.onboarding.screen.PermissionPrimerScreen
import com.appblish.jgallery.feature.onboarding.screen.TrustOverlayScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Instrumented checks for the onboarding screens (stateless composables — no Hilt needed). Covers the
 * DoD-critical facts: the primer copy is spec-locked, and the trust CLAIM must NOT render before the
 * Security sign-off (integrity rule §5 / spec §9.3).
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreensTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun primer_shows_spec_locked_copy_and_single_allow_cta() {
        composeTestRule.setContent {
            JGalleryTheme { PermissionPrimerScreen(onAllow = {}) }
        }
        composeTestRule.onNodeWithText("Permission Required").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("We only use this permission to display and manage your media files.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow").assertIsDisplayed()
    }

    @Test
    fun primer_allow_fires_callback() {
        var clicks = 0
        composeTestRule.setContent {
            JGalleryTheme { PermissionPrimerScreen(onAllow = { clicks++ }) }
        }
        composeTestRule.onNodeWithText("Allow").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun trust_overlay_shows_the_signed_off_safety_claim() {
        // Sign-off flipped to Approved per the APP-285 security-signoff doc (gated by the APP-289
        // CI egress guard). Guard the precondition this test asserts against.
        assertEquals(true, TrustCopy.claimApproved)

        composeTestRule.setContent {
            JGalleryTheme { TrustOverlayScreen() }
        }
        // The branded claim renders now that sign-off is Approved…
        composeTestRule.onNodeWithText(TrustCopy.TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText(TrustCopy.BODY).assertIsDisplayed()
        // …and the claim-free fallback heading is gone (the bottom POINTER line remains).
        composeTestRule.onNodeWithText("Enable access for JGallery").assertDoesNotExist()
        composeTestRule.onNodeWithText(TrustCopy.POINTER).assertIsDisplayed()
    }

    @Test
    fun language_screen_shows_title_and_done() {
        composeTestRule.setContent {
            JGalleryTheme {
                OnboardingLanguageScreen(
                    selected = OnboardingLanguage.English,
                    onSelect = {},
                    onDone = {},
                )
            }
        }
        composeTestRule.onNodeWithText("App Language").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }
}
