package com.appblish.jgallery.core.model

/**
 * The direction a 90° photo rotation turns the *displayed* image (spec §7 · G3-1). [LEFT] is a
 * 90° counter-clockwise turn, [RIGHT] a 90° clockwise turn — the two viewer rotate affordances.
 * A pure domain concept (no Android): the storage layer persists it as an EXIF-orientation change
 * or a pixel re-encode, but the surface only ever names the user's intent.
 */
enum class RotationDirection { LEFT, RIGHT }
