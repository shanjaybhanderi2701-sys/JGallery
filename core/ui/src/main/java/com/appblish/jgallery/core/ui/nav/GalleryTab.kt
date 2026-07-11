package com.appblish.jgallery.core.ui.nav

/**
 * The 2 top-level tabs (design C1-01, item 10 — OnePlus-modeled). The old 4-tab set
 * (Albums/Photos/Collections/Search) collapses to **Photos · Collections**:
 *  - **Collections** now hosts the Albums grid (AlbumsCatalog: Recent/Video/folders/pin).
 *  - **Search** is no longer a tab — it is a header action (full-screen route) on both tabs.
 *
 * Photos is the default. Order here is the bottom-nav order.
 */
enum class GalleryTab(val route: String, val label: String) {
    PHOTOS("photos", "Photos"),
    COLLECTIONS("collections", "Collections"),
    ;

    companion object {
        val Default = PHOTOS
    }
}
