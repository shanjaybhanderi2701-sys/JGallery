package com.appblish.jgallery.core.ui.nav

/** The 4 tabs (spec §2). Albums is the default. Order here is the bottom-nav order. */
enum class GalleryTab(val route: String, val label: String) {
    ALBUMS("albums", "Albums"),
    PHOTOS("photos", "Photos"),
    COLLECTIONS("collections", "Collections"),
    SEARCH("search", "Search"),
    ;

    companion object {
        val Default = ALBUMS
    }
}
