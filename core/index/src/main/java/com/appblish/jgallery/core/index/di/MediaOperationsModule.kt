package com.appblish.jgallery.core.index.di

import com.appblish.jgallery.core.index.MediaOperationsRepository
import com.appblish.jgallery.core.index.internal.StorageMediaOperationsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the feature-facing media mutation API (spec §7) to its storage-delegating implementation.
 * Kept in its own module (rather than folded into [IndexModule]) so the E11 bulk-ops seam evolves
 * independently of the read/index bindings — a small decoupling that also keeps concurrent Wave 2
 * features from contending on one Hilt module file.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class MediaOperationsModule {

    @Binds
    @Singleton
    abstract fun bindMediaOperationsRepository(
        impl: StorageMediaOperationsRepository,
    ): MediaOperationsRepository
}
