package com.appblish.jgallery.core.storage.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The pure RELATIVE_PATH math behind an album rename (spec §7.3, §11). */
class AlbumPathsTest {

    @Test
    fun `leaf returns the album's own folder segment`() {
        assertThat(AlbumPaths.leaf("Pictures/Trip 2026/")).isEqualTo("Trip 2026")
        assertThat(AlbumPaths.leaf("DCIM/Camera/")).isEqualTo("Camera")
    }

    @Test
    fun `leaf tolerates a missing trailing slash and a top-level folder`() {
        assertThat(AlbumPaths.leaf("Pictures/Trip")).isEqualTo("Trip")
        assertThat(AlbumPaths.leaf("Pictures")).isEqualTo("Pictures")
    }

    @Test
    fun `renameLeaf swaps the last segment and keeps the parent chain plus trailing slash`() {
        assertThat(AlbumPaths.renameLeaf("Pictures/Trip/", "Holiday")).isEqualTo("Pictures/Holiday/")
        assertThat(AlbumPaths.renameLeaf("DCIM/Sub/Camera/", "Snaps")).isEqualTo("DCIM/Sub/Snaps/")
    }

    @Test
    fun `renameLeaf on a top-level folder yields just the new leaf`() {
        assertThat(AlbumPaths.renameLeaf("Pictures", "Holiday")).isEqualTo("Holiday/")
        assertThat(AlbumPaths.renameLeaf("Pictures/", "Holiday")).isEqualTo("Holiday/")
    }

    @Test
    fun `newAlbumPath places the album under Pictures with a trailing slash`() {
        // The create-and-move destination (C6 item 12) a row-less new album is addressed by.
        assertThat(AlbumPaths.newAlbumPath("Holiday")).isEqualTo("Pictures/Holiday/")
        assertThat(AlbumPaths.newAlbumPath("Trip 2026")).isEqualTo("Pictures/Trip 2026/")
    }

    @Test
    fun `newAlbumPath output contains a slash so the boundary treats it as a concrete folder`() {
        // Guards the '/' discriminator MediaStoreStorageOps uses to tell a path handle from a bucket id.
        assertThat(AlbumPaths.newAlbumPath("Holiday")).contains("/")
    }
}
