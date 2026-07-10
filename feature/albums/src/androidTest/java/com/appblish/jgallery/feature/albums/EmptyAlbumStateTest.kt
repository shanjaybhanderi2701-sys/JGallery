package com.appblish.jgallery.feature.albums

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

/** Instrumented checks for the W3-08 empty-folder state: copy renders and both CTAs fire. */
@RunWith(AndroidJUnit4::class)
class EmptyAlbumStateTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shows_empty_copy_and_both_ctas() {
        composeRule.setContent {
            JGalleryTheme { EmptyAlbumState(onAddPhotos = {}, onOpenCamera = {}) }
        }
        composeRule.onNodeWithText("This folder is empty").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_album_add_photos").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_album_camera").assertIsDisplayed()
    }

    @Test
    fun ctas_fire_their_callbacks() {
        var addPhotos = 0
        var camera = 0
        composeRule.setContent {
            JGalleryTheme { EmptyAlbumState(onAddPhotos = { addPhotos++ }, onOpenCamera = { camera++ }) }
        }
        composeRule.onNodeWithTag("empty_album_add_photos").performClick()
        composeRule.onNodeWithTag("empty_album_camera").performClick()
        assertEquals(1, addPhotos)
        assertEquals(1, camera)
    }
}
