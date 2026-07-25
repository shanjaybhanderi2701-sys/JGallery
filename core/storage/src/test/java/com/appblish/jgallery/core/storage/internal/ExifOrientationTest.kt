package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.RotationDirection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Exhaustive coverage of the pure EXIF-orientation rotate math (spec §7 · G3-1 · APP-639) — the
 * "rotate photo, persist the orientation" logic, exercised without any Android/IO surface.
 */
class ExifOrientationTest {

    // The 8 valid orientations, ordered so the two 90° cycles are visible: the unflipped ring
    // (NORMAL → ROTATE_90 → ROTATE_180 → ROTATE_270) and the flipped ring.
    private val allOrientations = listOf(
        ExifOrientation.NORMAL,
        ExifOrientation.ROTATE_90,
        ExifOrientation.ROTATE_180,
        ExifOrientation.ROTATE_270,
        ExifOrientation.FLIP_HORIZONTAL,
        ExifOrientation.TRANSVERSE,
        ExifOrientation.FLIP_VERTICAL,
        ExifOrientation.TRANSPOSE,
    )

    @Test
    fun `rotate right advances each orientation one clockwise quarter-turn`() {
        // Unflipped ring rotates NORMAL→90→180→270→NORMAL; flipped ring stays flipped.
        assertThat(rotateR(ExifOrientation.NORMAL)).isEqualTo(ExifOrientation.ROTATE_90)
        assertThat(rotateR(ExifOrientation.ROTATE_90)).isEqualTo(ExifOrientation.ROTATE_180)
        assertThat(rotateR(ExifOrientation.ROTATE_180)).isEqualTo(ExifOrientation.ROTATE_270)
        assertThat(rotateR(ExifOrientation.ROTATE_270)).isEqualTo(ExifOrientation.NORMAL)

        assertThat(rotateR(ExifOrientation.FLIP_HORIZONTAL)).isEqualTo(ExifOrientation.TRANSVERSE)
        assertThat(rotateR(ExifOrientation.TRANSVERSE)).isEqualTo(ExifOrientation.FLIP_VERTICAL)
        assertThat(rotateR(ExifOrientation.FLIP_VERTICAL)).isEqualTo(ExifOrientation.TRANSPOSE)
        assertThat(rotateR(ExifOrientation.TRANSPOSE)).isEqualTo(ExifOrientation.FLIP_HORIZONTAL)
    }

    @Test
    fun `rotate left advances each orientation one counter-clockwise quarter-turn`() {
        assertThat(rotateL(ExifOrientation.NORMAL)).isEqualTo(ExifOrientation.ROTATE_270)
        assertThat(rotateL(ExifOrientation.ROTATE_270)).isEqualTo(ExifOrientation.ROTATE_180)
        assertThat(rotateL(ExifOrientation.ROTATE_180)).isEqualTo(ExifOrientation.ROTATE_90)
        assertThat(rotateL(ExifOrientation.ROTATE_90)).isEqualTo(ExifOrientation.NORMAL)

        assertThat(rotateL(ExifOrientation.FLIP_HORIZONTAL)).isEqualTo(ExifOrientation.TRANSPOSE)
        assertThat(rotateL(ExifOrientation.TRANSPOSE)).isEqualTo(ExifOrientation.FLIP_VERTICAL)
        assertThat(rotateL(ExifOrientation.FLIP_VERTICAL)).isEqualTo(ExifOrientation.TRANSVERSE)
        assertThat(rotateL(ExifOrientation.TRANSVERSE)).isEqualTo(ExifOrientation.FLIP_HORIZONTAL)
    }

    @Test
    fun `left then right is the identity for every orientation`() {
        for (o in allOrientations) {
            assertThat(rotateR(rotateL(o))).isEqualTo(o)
            assertThat(rotateL(rotateR(o))).isEqualTo(o)
        }
    }

    @Test
    fun `four right turns return to the original for every orientation`() {
        for (o in allOrientations) {
            assertThat(rotateR(rotateR(rotateR(rotateR(o))))).isEqualTo(o)
        }
    }

    @Test
    fun `rotate never preserves nor drops the horizontal flip`() {
        // A quarter-turn changes the rotation but leaves the mirrored-ness untouched.
        val flipped = setOf(
            ExifOrientation.FLIP_HORIZONTAL,
            ExifOrientation.FLIP_VERTICAL,
            ExifOrientation.TRANSPOSE,
            ExifOrientation.TRANSVERSE,
        )
        for (o in allOrientations) {
            assertThat(rotateR(o) in flipped).isEqualTo(o in flipped)
            assertThat(rotateL(o) in flipped).isEqualTo(o in flipped)
        }
    }

    @Test
    fun `an undefined or unknown orientation rotates as if it were normal`() {
        assertThat(rotateR(ExifOrientation.UNDEFINED)).isEqualTo(ExifOrientation.ROTATE_90)
        assertThat(rotateL(ExifOrientation.UNDEFINED)).isEqualTo(ExifOrientation.ROTATE_270)
        assertThat(rotateR(999)).isEqualTo(ExifOrientation.ROTATE_90)
    }

    private fun rotateR(o: Int) = ExifOrientation.rotate(o, RotationDirection.RIGHT)
    private fun rotateL(o: Int) = ExifOrientation.rotate(o, RotationDirection.LEFT)
}
