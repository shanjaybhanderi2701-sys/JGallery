package com.appblish.jgallery.core.ui.window

import android.content.pm.ActivityInfo
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Policy coverage for [desiredOrientation] (APP-651). The three [ActivityInfo] constants are Java
 * compile-time `static final int`s, so they inline into this JVM unit test without an Android runtime.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class ScreenOrientationTest {

    @Test
    fun compactNonViewer_locksPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            desiredOrientation(WindowWidthSizeClass.Compact, isViewer = false),
        )
    }

    @Test
    fun compactViewer_allowsRotationHonoringSystemLock() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            desiredOrientation(WindowWidthSizeClass.Compact, isViewer = true),
        )
    }

    @Test
    fun medium_neverLocks_evenInViewer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(WindowWidthSizeClass.Medium, isViewer = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(WindowWidthSizeClass.Medium, isViewer = true),
        )
    }

    @Test
    fun expanded_neverLocks_evenInViewer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(WindowWidthSizeClass.Expanded, isViewer = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(WindowWidthSizeClass.Expanded, isViewer = true),
        )
    }
}
