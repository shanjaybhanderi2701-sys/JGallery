package com.appblish.jgallery.core.index.di

import android.content.Context
import androidx.room.Room
import com.appblish.jgallery.core.index.MediaIndexRepository
import com.appblish.jgallery.core.index.internal.CachedMediaIndexRepository
import com.appblish.jgallery.core.index.internal.MediaDao
import com.appblish.jgallery.core.index.internal.MediaIndexDatabase
import com.appblish.jgallery.core.index.internal.MediaIndexStore
import com.appblish.jgallery.core.index.internal.RoomMediaIndexStore
import com.appblish.jgallery.core.storage.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/** Binds the index abstractions to their implementations. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class IndexModule {

    @Binds
    @Singleton
    abstract fun bindMediaIndexRepository(impl: CachedMediaIndexRepository): MediaIndexRepository

    @Binds
    @Singleton
    abstract fun bindMediaIndexStore(impl: RoomMediaIndexStore): MediaIndexStore
}

/** Provides the Room database, DAO, and the app-lifetime sync scope. */
@Module
@InstallIn(SingletonComponent::class)
internal object IndexProvidesModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MediaIndexDatabase =
        Room.databaseBuilder(context, MediaIndexDatabase::class.java, MediaIndexDatabase.NAME).build()

    @Provides
    fun provideMediaDao(database: MediaIndexDatabase): MediaDao = database.mediaDao()

    @Provides
    @Singleton
    @IndexSyncScope
    fun provideIndexSyncScope(@IoDispatcher io: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + io)
}
