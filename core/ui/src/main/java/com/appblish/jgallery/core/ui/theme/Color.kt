package com.appblish.jgallery.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * JGallery Wave 1 color tokens — signed off on APP-269 (`w1-design-spec` §1).
 * Light, image-forward: white background, blue accent, dark chrome ONLY inside the viewer.
 */
object JGalleryColors {
    val Accent = Color(0xFF2D6FF7)      // selection, CTAs, fast-scroll thumb, active tab
    val AccentSoft = Color(0xFFE4EDFE)  // active-tab pill, chips, hero tints
    val Background = Color(0xFFFFFFFF)  // app background — image-forward
    val Text = Color(0xFF111114)        // primary text
    val TextSecondary = Color(0xFF7A7F87) // counts, captions, muted body
    val Surface = Color(0xFFF2F3F6)     // search bar, secondary buttons, utility pills
    val TilePlaceholder = Color(0xFFEDEFF3) // grid tile before its thumbnail lands — never blank white (design §6)

    // Viewer-only dark chrome.
    val ViewerCanvas = Color(0xFF000000)
    val ViewerSheet = Color(0xFF232428)

    // Trust overlay shield ONLY (spec §9.3 integrity rule — never reuse for generic UI).
    val TrustGreen = Color(0xFF23A55A)

    val OnAccent = Color(0xFFFFFFFF)
}
