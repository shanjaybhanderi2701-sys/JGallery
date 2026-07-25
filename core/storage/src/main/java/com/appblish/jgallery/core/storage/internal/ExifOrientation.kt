package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.RotationDirection

/**
 * Pure EXIF-orientation transform math (spec §7 · G3-1). Given a file's current TIFF/EXIF
 * `Orientation` tag value and the direction the user turned the photo, computes the new tag value —
 * the whole "rotate photo, persist the orientation" logic with zero Android/IO surface, so it is
 * exhaustively JVM-unit-testable (the device half is only the ExifInterface write itself).
 *
 * The 8 orientation values are a dihedral group: each is a rotation (0/90/180/270° clockwise) plus an
 * optional horizontal flip. Decompose to `(degrees, flipped)`, add the turn's delta to the rotation
 * (flip is preserved — turning a mirrored image still leaves it mirrored), and recompose. Because the
 * flip is the innermost operation, rotations simply add: `deg' = (deg + delta) mod 360`.
 *
 * Values mirror `androidx.exifinterface.media.ExifInterface.ORIENTATION_*`; they are re-declared as
 * plain Ints so this stays a pure-Kotlin, no-dependency unit under test.
 */
internal object ExifOrientation {

    const val UNDEFINED = 0
    const val NORMAL = 1
    const val FLIP_HORIZONTAL = 2
    const val ROTATE_180 = 3
    const val FLIP_VERTICAL = 4
    const val TRANSPOSE = 5
    const val ROTATE_90 = 6
    const val TRANSVERSE = 7
    const val ROTATE_270 = 8

    /**
     * The new EXIF orientation after turning the displayed image 90° in [direction]. An
     * [UNDEFINED]/unknown current value is treated as [NORMAL] (upright, unflipped) so a file with no
     * orientation tag still rotates predictably. Turning [RIGHT] four times (or any right/left pair)
     * is a round-trip back to the original value.
     */
    fun rotate(current: Int, direction: RotationDirection): Int {
        val (degrees, flipped) = decompose(current)
        val delta = if (direction == RotationDirection.RIGHT) 90 else 270 // 270° CW == 90° CCW
        return recompose((degrees + delta) % 360, flipped)
    }

    /** Orientation value → (clockwise degrees, horizontally flipped). Unknown → upright, unflipped. */
    private fun decompose(orientation: Int): Pair<Int, Boolean> = when (orientation) {
        NORMAL -> 0 to false
        ROTATE_90 -> 90 to false
        ROTATE_180 -> 180 to false
        ROTATE_270 -> 270 to false
        FLIP_HORIZONTAL -> 0 to true
        TRANSVERSE -> 90 to true
        FLIP_VERTICAL -> 180 to true
        TRANSPOSE -> 270 to true
        else -> 0 to false // UNDEFINED / out-of-range → treat as upright
    }

    /** (clockwise degrees, horizontally flipped) → orientation value. Inverse of [decompose]. */
    private fun recompose(degrees: Int, flipped: Boolean): Int = when (degrees to flipped) {
        0 to false -> NORMAL
        90 to false -> ROTATE_90
        180 to false -> ROTATE_180
        270 to false -> ROTATE_270
        0 to true -> FLIP_HORIZONTAL
        90 to true -> TRANSVERSE
        180 to true -> FLIP_VERTICAL
        270 to true -> TRANSPOSE
        else -> NORMAL // unreachable: degrees is always one of 0/90/180/270
    }
}
