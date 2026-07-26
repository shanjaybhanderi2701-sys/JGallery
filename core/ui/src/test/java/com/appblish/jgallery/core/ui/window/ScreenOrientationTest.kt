package com.appblish.jgallery.core.ui.window

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Policy coverage for [desiredOrientation] (APP-651; APP-676 — orientation-stable device signal). The
 * three [ActivityInfo] constants are Java compile-time `static final int`s, so they inline into this
 * JVM unit test without an Android runtime.
 *
 * The input is `isCompactWidthDevice` (derived from the rotation-invariant `smallestScreenWidthDp`),
 * not the live window width class — a compact-width phone stays "compact device" in both orientations,
 * so the portrait lock re-fires even when the phone is currently held landscape.
 */
class ScreenOrientationTest {

    @Test
    fun compactDeviceNonViewer_locksPortrait() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            desiredOrientation(isCompactWidthDevice = true, isViewer = false),
        )
    }

    @Test
    fun compactDeviceViewer_allowsRotationHonoringSystemLock() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            desiredOrientation(isCompactWidthDevice = true, isViewer = true),
        )
    }

    @Test
    fun nonCompactDevice_neverLocks_evenInViewer() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(isCompactWidthDevice = false, isViewer = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            desiredOrientation(isCompactWidthDevice = false, isViewer = true),
        )
    }
}
