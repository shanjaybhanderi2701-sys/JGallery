package com.appblish.jgallery.core.storage.internal

/**
 * Pure validation for a user-supplied album/folder name (spec §6 "Create folders/albums"). Split out
 * from the platform [MediaStoreStorageAccess.createAlbum] so the rules — which are the only non-IO
 * part of album creation — are covered by fast JVM unit tests rather than an on-device run.
 *
 * A name is rejected when, after trimming, it is empty, resolves to a path segment that would escape
 * its parent ("." / ".."), contains a path separator or another character illegal on the FAT/ext
 * volumes Android media lives on, or exceeds the filesystem's per-segment length limit.
 */
internal object AlbumNames {

    /** Characters that are illegal in an Android external-storage path segment. */
    private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private const val MAX_SEGMENT_LENGTH = 255

    sealed interface Result {
        data class Valid(val name: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(raw: String): Result {
        val name = raw.trim()
        return when {
            name.isEmpty() -> Result.Invalid("Album name can't be empty")
            name == "." || name == ".." -> Result.Invalid("That name isn't allowed")
            name.any { it.code < 0x20 } -> Result.Invalid("Album name can't contain control characters")
            name.any { it in ILLEGAL_CHARS } -> Result.Invalid("Album name can't contain / \\ : * ? \" < > |")
            name.length > MAX_SEGMENT_LENGTH -> Result.Invalid("Album name is too long")
            else -> Result.Valid(name)
        }
    }
}
