package com.appblish.jgallery.core.index.internal

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-disk cache of the media index. It is a *rebuildable cache*, not source of truth — MediaStore is
 * authoritative — so on a schema change we can drop and re-sync rather than migrate. Version stays at
 * 1 until the schema evolves; a destructive fallback is added alongside the first version bump.
 */
@Database(entities = [MediaEntity::class], version = 1, exportSchema = true)
internal abstract class MediaIndexDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "media-index.db"
    }
}
