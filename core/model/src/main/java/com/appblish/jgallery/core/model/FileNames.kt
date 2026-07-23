package com.appblish.jgallery.core.model

/**
 * Pure, platform-free validation + normalization for a user-supplied media/album name (spec §7.3
 * Rename, §6 Create album).
 *
 * This is the *single source of truth* for what a legal name is, shared by every rename/create
 * surface so they all accept, reject, and error-message identically (APP-590):
 *  - the inline dialog check ([com.appblish.jgallery.core.ui] `NameInputDialog` validator, wired from
 *    each feature screen),
 *  - the media rename engine (`:core:storage` `FileOperationEngine.rename`),
 *  - album creation / rename (`:core:storage` `AlbumNames`, which delegates here).
 *
 * A name is a single path segment: after trimming it must be non-empty, not "." / "..", free of
 * control and FAT/ext-illegal characters, and within the per-segment length limit. Media renames
 * additionally preserve the original file extension so a `DISPLAY_NAME` write can never strip or
 * alter the file's type.
 */
object FileNames {

    /** Characters illegal in an Android external-storage path segment (FAT/ext volumes). */
    private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    private const val MAX_SEGMENT_LENGTH = 255

    sealed interface Result {
        data class Valid(val name: String) : Result
        data class Invalid(val reason: String) : Result
    }

    /**
     * Validate [raw] as a single path segment. Returns the trimmed name on success. Used directly for
     * album names (no extension) and as the base-name check inside [normalizeRename].
     */
    fun validate(raw: String): Result {
        val name = raw.trim()
        return when {
            name.isEmpty() -> Result.Invalid("Name can't be empty")
            name == "." || name == ".." -> Result.Invalid("That name isn't allowed")
            name.any { it.code < 0x20 } -> Result.Invalid("Name can't contain control characters")
            name.any { it in ILLEGAL_CHARS } -> Result.Invalid("Name can't contain / \\ : * ? \" < > |")
            name.length > MAX_SEGMENT_LENGTH -> Result.Invalid("Name is too long")
            else -> Result.Valid(name)
        }
    }

    /**
     * Normalize a rename of a file whose current name is [currentName], **always preserving the
     * original extension** (spec §7.3 "extension preservation"): the user edits the base name; if they
     * retype the original extension it is not doubled, and any different/absent extension is replaced
     * with the original's so the file's type can never be corrupted by a `DISPLAY_NAME` write. Files
     * that currently have no extension are validated as-is.
     *
     * The validated *base* must obey [validate]'s character rules; the assembled name is length-bounded
     * too so `base + "." + ext` can't exceed the per-segment limit.
     */
    fun normalizeRename(raw: String, currentName: String): Result {
        val ext = originalExtension(currentName)
        val trimmed = raw.trim()
        // Strip a trailing extension from the input only when it equals the original (case-insensitive),
        // so "Sunset" and "Sunset.JPG" both normalize to "Sunset.jpg" for an "old.jpg" source, while a
        // deliberately different typed extension is preserved as part of the base (never corrupts type).
        val typedExt = originalExtension(trimmed)
        val base = if (ext != null && typedExt != null && typedExt.equals(ext, ignoreCase = true)) {
            trimmed.substringBeforeLast('.')
        } else {
            trimmed
        }
        return when (val v = validate(base)) {
            is Result.Invalid -> v
            is Result.Valid -> {
                val full = if (ext != null) "${v.name}.$ext" else v.name
                if (full.length > MAX_SEGMENT_LENGTH) Result.Invalid("Name is too long") else Result.Valid(full)
            }
        }
    }

    /** The inline error for a media rename, or null when [raw] is an acceptable new name. */
    fun renameError(raw: String, currentName: String): String? =
        (normalizeRename(raw, currentName) as? Result.Invalid)?.reason

    /** The inline error for an album name, or null when [raw] is acceptable. */
    fun albumNameError(raw: String): String? =
        (validate(raw) as? Result.Invalid)?.reason

    /** The extension of [name] (without the dot), or null for extensionless names and dotfiles. */
    private fun originalExtension(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return null // no dot, or a leading-dot "dotfile" (no real extension)
        return name.substring(dot + 1).ifEmpty { null }
    }
}
