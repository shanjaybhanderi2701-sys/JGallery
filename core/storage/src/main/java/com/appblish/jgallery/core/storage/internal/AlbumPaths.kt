package com.appblish.jgallery.core.storage.internal

/**
 * Pure `RELATIVE_PATH` math for an album (folder) rename (spec §7.3, §11). Renaming an album through
 * MediaStore means moving every member row into a sibling folder whose only difference is the last
 * path segment; the segment arithmetic is the one non-IO part of that operation, so it lives here as
 * a fast JVM-unit-tested helper (mirroring [AlbumNames]) rather than inside the on-device primitive.
 *
 * MediaStore stores a `RELATIVE_PATH` like `"Pictures/Trip 2026/"` — a parent chain plus the album's
 * own folder as the final segment, conventionally with a trailing slash. These helpers preserve that
 * shape so the rewritten value is one MediaStore accepts verbatim.
 */
internal object AlbumPaths {

    /** The album's own folder name — the last segment of [relativePath] (e.g. `"Trip 2026"`). */
    fun leaf(relativePath: String): String =
        relativePath.trim('/').substringAfterLast('/')

    /**
     * [relativePath] with its last segment replaced by [newLeaf], keeping the parent chain and the
     * trailing slash MediaStore expects. `"Pictures/Trip/"` + `"Holiday"` → `"Pictures/Holiday/"`; a
     * top-level `"Pictures"` (no parent) → `"Holiday/"`.
     */
    fun renameLeaf(relativePath: String, newLeaf: String): String {
        val trimmed = relativePath.trim('/')
        val parent = trimmed.substringBeforeLast('/', missingDelimiterValue = "")
        val rebuilt = if (parent.isEmpty()) newLeaf else "$parent/$newLeaf"
        return "$rebuilt/"
    }

    /** The public Pictures root every newly-created album folder lives under (`Environment.DIRECTORY_PICTURES`). */
    const val PICTURES_ROOT = "Pictures"

    /**
     * The `RELATIVE_PATH` a freshly-created album named [name] occupies. `createAlbum` makes the folder
     * directly under the public Pictures root, so a new album's canonical destination is
     * `"Pictures/<name>/"` (e.g. `"Pictures/Holiday/"`). The create-and-move flow (C6 item 12) hands
     * this concrete path to the copy/move engine because a row-less new album has no member row to
     * reverse-look-up a bucket id from. [name] is assumed already validated by [AlbumNames].
     */
    fun newAlbumPath(name: String): String = "$PICTURES_ROOT/$name/"
}
