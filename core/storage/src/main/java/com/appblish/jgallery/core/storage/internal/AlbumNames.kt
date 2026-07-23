package com.appblish.jgallery.core.storage.internal

import com.appblish.jgallery.core.model.FileNames

/**
 * Pure validation for a user-supplied album/folder name (spec §6 "Create folders/albums"). Split out
 * from the platform [MediaStoreStorageAccess.createAlbum] so the rules — which are the only non-IO
 * part of album creation — are covered by fast JVM unit tests rather than an on-device run.
 *
 * The rules themselves live in the shared [FileNames] (`:core:model`) so album create/rename, the
 * media rename engine, and the inline dialog check all accept/reject identically (APP-590); this type
 * is now just the storage-facing adapter that keeps the existing `Result` shape callers expect.
 */
internal object AlbumNames {

    sealed interface Result {
        data class Valid(val name: String) : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(raw: String): Result = when (val v = FileNames.validate(raw)) {
        is FileNames.Result.Valid -> Result.Valid(v.name)
        is FileNames.Result.Invalid -> Result.Invalid(v.reason)
    }
}
