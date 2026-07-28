package com.appblish.jgallery.core.index.internal

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-disk cache of the media index. It is a *rebuildable cache*, not source of truth — MediaStore is
 * authoritative — so on a schema change we drop and re-sync rather than migrate. Version 2 (APP-700)
 * adds the denormalized sort/section-authority columns + indices; the destructive fallback in
 * `IndexProvidesModule` drops the old table and the next sync repopulates it (a one-time full
 * re-enumeration, exactly like a fresh install).
 */
@Database(entities = [MediaEntity::class], version = 2, exportSchema = true)
internal abstract class MediaIndexDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "media-index.db"
    }
}
