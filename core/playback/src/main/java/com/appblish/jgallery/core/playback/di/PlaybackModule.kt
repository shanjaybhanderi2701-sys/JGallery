package com.appblish.jgallery.core.playback.di

import com.appblish.jgallery.core.playback.PlaybackSources
import com.appblish.jgallery.core.playback.internal.StoragePlaybackSources
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface PlaybackModule {

    @Binds
    fun bindPlaybackSources(impl: StoragePlaybackSources): PlaybackSources
}
