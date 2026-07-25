package com.appblish.jgallery.core.ui.window

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * App-wide access to the current [WindowSizeClass] (APP-651 — adaptive foundation, APP-645 §6 #1).
 *
 * Computed **once** in `MainActivity` from the activity's window metrics and provided at the app root,
 * so any feature composable can branch its layout on Compact / Medium / Expanded (nav rail, adaptive
 * grids, two-pane Collections, viewer landscape) without re-deriving window metrics itself.
 *
 * There is intentionally no default: reading it outside the root provider is a programming error, so
 * it fails loudly rather than silently reporting a wrong size class.
 */
val LocalWindowSizeClass = compositionLocalOf<WindowSizeClass> {
    error(
        "LocalWindowSizeClass not provided. The app root (MainActivity) must wrap content in " +
            "CompositionLocalProvider(LocalWindowSizeClass provides calculateWindowSizeClass(this)).",
    )
}
