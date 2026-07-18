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

    // Bottom-sheet grab handle (D4 spec §D4-01/§D4-05 "handle #E3E6EB"). A neutral cooler grey than the
    // #F2F3F6 utility fill so the 44×5dp handle reads distinctly against the white sheet surface. Not a
    // new hue — the exact value the accepted APP-453 redlines enumerate; centralized here so every sheet
    // shares one handle instead of each falling back to M3's thin grey pill.
    val SheetHandle = Color(0xFFE3E6EB)

    // Warning amber (`color.warn`, W2 design spec §token): Trash ≤5-days-left badge, partial-success
    // summary glyph. Reserved for "heads-up before something irreversible", not a generic accent.
    val Warn = Color(0xFFE68A17)
    // Destructive red: permanent-delete actions (W2-08 step 2, Empty bin). Guarded paths only.
    val Destructive = Color(0xFFD5342B)

    // Wave 3 format-state tokens (W3 design spec §8 "can't fully show this" taxonomy). No new hues:
    // Danger reuses D2's #E5484D, amber/green already exist above. Two neutral surface fills carry
    // the two "degraded tile" states so unsupported (grey, file is fine) and corrupt (warm-red,
    // file is damaged) are visually distinct at a glance and never a blank tile (spec §8, §1).
    val Danger = Color(0xFFE5484D)               // corrupt / codec-unsupported glyph + chrome
    val UnsupportedFill = Color(0xFFEBEEF3)      // neutral tile — healthy file we don't render (W3-01)
    val CorruptFill = Color(0xFFF6EAEA)          // warm-red tile — damaged/undecodable file (W3-06)

    // Dark theme tokens (G2 Settings §5, SET-05). The first real app-wide dark scheme (Wave 1 was
    // light-only; the viewer had its own black chrome). Accent is lifted from #2D6FF7 → #5B8CFF so
    // it clears AA 4.5:1 on the dark background; AccentSoft becomes a deep-blue pill container.
    val DarkBackground = Color(0xFF121317)      // app background
    val DarkSurface = Color(0xFF1C1E24)         // cards, sheets
    val DarkOutline = Color(0xFF2A2D35)         // dividers, 1dp lines
    val DarkText = Color(0xFFF3F4F7)            // primary text
    val DarkTextSecondary = Color(0xFF9BA1AC)   // captions, muted body
    val DarkAccent = Color(0xFF5B8CFF)          // selection, CTAs, pills — AA on dark
    val DarkAccentSoft = Color(0xFF243156)      // pill container
    val DarkWarn = Color(0xFFF0A23A)            // warn hue, lightened ~1 step for dark
    val DarkDestructive = Color(0xFFE4635B)     // destructive hue, lightened ~1 step for dark

    // Viewer-only dark chrome.
    val ViewerCanvas = Color(0xFF000000)
    val ViewerSheet = Color(0xFF232428)

    // Trust overlay shield ONLY (spec §9.3 integrity rule — never reuse for generic UI).
    val TrustGreen = Color(0xFF23A55A)

    val OnAccent = Color(0xFFFFFFFF)
}
