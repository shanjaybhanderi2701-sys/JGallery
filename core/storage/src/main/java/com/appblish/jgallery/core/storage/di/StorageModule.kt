package com.appblish.jgallery.core.storage.di

import android.content.ContentResolver
import android.content.Context
import com.appblish.jgallery.core.storage.StorageAccess
import com.appblish.jgallery.core.storage.StoragePermissionController
import com.appblish.jgallery.core.storage.internal.AllFilesAccessPermissionController
import com.appblish.jgallery.core.storage.internal.MediaStoreStorageAccess
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
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideStorageAccess(
        resolver: ContentResolver,
        @IoDispatcher io: CoroutineDispatcher,
    ): StorageAccess = MediaStoreStorageAccess(resolver, io)

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
