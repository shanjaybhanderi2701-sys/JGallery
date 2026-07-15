package com.appblish.jgallery.core.ui.nav

/**
 * The 2 top-level tabs (design C1-01, item 10 — OnePlus-modeled). The old 4-tab set
 * (Albums/Photos/Collections/Search) collapses to **Photos · Albums**:
 *  - The second tab hosts the Albums grid (AlbumsCatalog: Recent/Video/folders/pin). Its
 *    human-facing label is **"Albums"** (G1-D5, APP-454); the internal route id stays
 *    `collections` so deep-links, `testTag("tab_collections")`, and nav routing keep working.
 *  - **Search** is no longer a tab — it is a header action (full-screen route) on both tabs.
 *
 * Photos is the default. Order here is the bottom-nav order.
 */
enum class GalleryTab(val route: String, val label: String) {
    PHOTOS("photos", "Photos"),
    COLLECTIONS("collections", "Albums"),
    ;

    companion object {
        val Default = PHOTOS
    }
}
