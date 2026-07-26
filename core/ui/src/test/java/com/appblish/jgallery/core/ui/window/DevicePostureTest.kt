package com.appblish.jgallery.core.ui.window

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Policy coverage for the pure posture mapping + hinge-avoidance math (APP-655 §7.2). */
class DevicePostureTest {

    private val verticalHinge = IntRect(left = 1000, top = 0, right = 1080, bottom = 1920)

    @Test
    fun notHalfOpen_isFlat() {
        assertEquals(
            DevicePosture.Flat,
            devicePostureFrom(isHalfOpen = false, isVertical = true, occludes = true, boundsPx = verticalHinge),
        )
    }

    @Test
    fun halfOpenSeamless_isHalfOpen_butNoOccludingBounds() {
        val posture = devicePostureFrom(
            isHalfOpen = true, isVertical = true, occludes = false, boundsPx = verticalHinge,
        )
        assertEquals(
            DevicePosture.HalfOpen(HingeOrientation.VERTICAL, isSeparating = true, occludingBoundsPx = null),
            posture,
        )
        assertNull(posture.occludingHingePx)
    }

    @Test
    fun halfOpenOccluding_carriesBounds() {
        val posture = devicePostureFrom(
            isHalfOpen = true, isVertical = false, occludes = true, boundsPx = verticalHinge,
        )
        assertEquals(
            DevicePosture.HalfOpen(HingeOrientation.HORIZONTAL, isSeparating = true, occludingBoundsPx = verticalHinge),
            posture,
        )
        assertEquals(verticalHinge, posture.occludingHingePx)
    }

    @Test
    fun chromeInset_flat_isZero() {
        assertEquals(0 to 0, chromeHingeInsetPx(2080, DevicePosture.Flat))
    }

    @Test
    fun chromeInset_nonOccludingHinge_isZero() {
        val posture = DevicePosture.HalfOpen(HingeOrientation.VERTICAL, isSeparating = true, occludingBoundsPx = null)
        assertEquals(0 to 0, chromeHingeInsetPx(2080, posture))
    }

    @Test
    fun chromeInset_horizontalHinge_isZero() {
        // A center horizontal crease never crosses bars pinned to the top/bottom of the window.
        val posture = DevicePosture.HalfOpen(
            HingeOrientation.HORIZONTAL, isSeparating = true, occludingBoundsPx = IntRect(0, 900, 2080, 980),
        )
        assertEquals(0 to 0, chromeHingeInsetPx(2080, posture))
    }

    @Test
    fun chromeInset_verticalOccluding_widerLeftPanel_padsEnd() {
        // Hinge at [1000,1200] in a 2080-wide window → left panel 1000, right panel 880 → keep on left.
        val hinge = IntRect(left = 1000, top = 0, right = 1200, bottom = 1920)
        val posture = DevicePosture.HalfOpen(HingeOrientation.VERTICAL, isSeparating = true, occludingBoundsPx = hinge)
        assertEquals(0 to (2080 - 1000), chromeHingeInsetPx(2080, posture))
    }

    @Test
    fun chromeInset_verticalOccluding_widerRightPanel_padsStart() {
        // Hinge at [800,1000] in a 2080-wide window → left panel 800, right panel 1080 → keep on right.
        val hinge = IntRect(left = 800, top = 0, right = 1000, bottom = 1920)
        val posture = DevicePosture.HalfOpen(HingeOrientation.VERTICAL, isSeparating = true, occludingBoundsPx = hinge)
        assertEquals(1000 to 0, chromeHingeInsetPx(2080, posture))
    }
}
