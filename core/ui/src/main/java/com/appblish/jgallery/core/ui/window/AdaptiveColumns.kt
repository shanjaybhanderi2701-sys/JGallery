package com.appblish.jgallery.core.ui.window

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.appblish.jgallery.core.model.ColumnCount

/**
 * Grid families that scale differently across width tiers (APP-645 §5 table, APP-653).
 */
enum class GridContent {
    /** Media thumbnail grids (Photos tab, album detail): base = the user's density preference. */
    MEDIA,

    /** Album cover tiles: fewer, larger tiles, so they scale one step slower than media. */
    ALBUM_TILES,
}

/**
 * Width-derived grid column count (APP-645 §5, approach **B**). Adds a size-class *bonus* to the
 * phone-portrait [base] preference and clamps to [ColumnCount.MAX].
 *
 * The bonus is **render-only**: callers keep passing the persisted [base] to pinch / DataStore, so
 * rotating or folding never corrupts the one app-wide saved value (spec §5 "Column preference scope").
 * Compact returns [base] unchanged, so phone layouts are byte-for-byte untouched. [base] must be the
 * APP-644 density decision token (`ViewDefaults.defaultColumns`), never a literal.
 *
 * Per the §5 table (with the default base of 3):
 *  - [GridContent.MEDIA]:       Compact = pref, Medium = pref+1 (≈4), Expanded = pref+2 (≈5).
 *  - [GridContent.ALBUM_TILES]: Compact = pref, Medium = pref,       Expanded = pref+1.
 *
 * Expanded photos land at ≈5 for the default pref; the §5 target is ≈5–6, and a user who has pinched
 * their pref up to [ColumnCount.PREF_MAX] reaches the raised [ColumnCount.MAX] cap, so the whole
 * ≈5–6+ range is reachable without a second persisted key.
 */
fun WindowWidthSizeClass.adaptiveColumns(base: ColumnCount, content: GridContent): ColumnCount {
    val bonus = when (content) {
        GridContent.MEDIA -> when (this) {
            WindowWidthSizeClass.Expanded -> 2
            WindowWidthSizeClass.Medium -> 1
            else -> 0
        }
        GridContent.ALBUM_TILES -> when (this) {
            WindowWidthSizeClass.Expanded -> 1
            else -> 0
        }
    }
    if (bonus == 0) return base
    return ColumnCount((base.value + bonus).coerceIn(ColumnCount.MIN, ColumnCount.MAX))
}

/**
 * Composable convenience reading the app-wide [LocalWindowSizeClass] (APP-651 seam). Call this at the
 * grid render site to size `GridCells.Fixed`, and keep driving pinch with the un-adapted [base] so the
 * bonus is never persisted.
 */
@Composable
@ReadOnlyComposable
fun adaptiveColumns(base: ColumnCount, content: GridContent): ColumnCount =
    LocalWindowSizeClass.current.widthSizeClass.adaptiveColumns(base, content)
