package com.appblish.jgallery.core.storage.di

import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StoragePermissionController
import com.appblish.jgallery.core.storage.ThumbnailBitmapSource
import com.appblish.jgallery.core.storage.internal.AllFilesAccessPermissionController
import com.appblish.jgallery.core.storage.internal.DataStoreTrashMetadataStore
import com.appblish.jgallery.core.storage.internal.MediaStoreStorageAccess
import com.appblish.jgallery.core.storage.internal.TrashMetadataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the IO dispatcher so consumers never hard-code `Dispatchers.IO` (testable, swappable). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Binds the storage boundary for the whole app. Swapping the permission model = swapping the single
 * `StorageAccess` provider here; no feature module changes (spec §1.6).
 */
/** Single DataStore instance for the Recycle Bin's retention metadata (spec §7.5). */
private val Context.trashDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_trash",
)

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    // The Recycle Bin's persistent metadata store is an internal type, so it is constructed in the
    // body here (not exposed as its own @Provides) — the module only ever surfaces the public
    // StorageAccess boundary. Swapping the storage backend swaps this one provider (spec §1.6).
    @Provides
    @Singleton
    fun provideStorageAccess(
        @ApplicationContext context: Context,
        resolver: ContentResolver,
        @IoDispatcher io: CoroutineDispatcher,
    ): StorageAccess {
        val trashStore: TrashMetadataStore = DataStoreTrashMetadataStore(context.trashDataStore)
        return MediaStoreStorageAccess(context, resolver, io, trashStore)
    }

    /**
     * The APP-391 R1 decode-once SPI (`:core:thumbs` consumer only). The singleton [StorageAccess]
     * already implements [ThumbnailBitmapSource] — bind the SAME instance rather than a second one, so
     * there is still exactly one MediaStore surface (spec §1.6). Kept out of [StorageAccess] so the
     * JVM fakes never have to implement an Android `Bitmap` method.
     */
    @Provides
    @Singleton
    fun provideThumbnailBitmapSource(storage: StorageAccess): ThumbnailBitmapSource =
        storage as ThumbnailBitmapSource

    /**
     * The permission half of the boundary. Swapping to media permissions / SAF means returning a
     * different [StoragePermissionController] here (and a matching [StorageAccess] above) — onboarding
     * and every feature are untouched because they depend only on the interfaces (spec §1.6, §9.4).
     */
    @Provides
    @Singleton
    fun provideStoragePermissionController(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): StoragePermissionController = AllFilesAccessPermissionController(context, io)
}
