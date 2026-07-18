package com.appblish.jgallery.feature.albums.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.appblish.jgallery.feature.albums.AlbumViewPreferences
import com.appblish.jgallery.feature.albums.AlbumsPreferences
import com.appblish.jgallery.feature.albums.DataStoreAlbumViewPreferences
import com.appblish.jgallery.feature.albums.DataStoreAlbumsPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single DataStore instance for Albums-tab view settings (column count). */
private val Context.albumsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_albums_ui",
)

@Module
@InstallIn(SingletonComponent::class)
object AlbumsModule {

    @Provides
    @Singleton
    fun provideAlbumsPreferences(
        @ApplicationContext context: Context,
    ): AlbumsPreferences = DataStoreAlbumsPreferences(context.albumsDataStore)

    /** Per-album Sort / Grid-size / scope settings (G1-9). Shares the Albums-tab DataStore file. */
    @Provides
    @Singleton
    fun provideAlbumViewPreferences(
        @ApplicationContext context: Context,
        viewDefaults: ViewDefaults,
    ): AlbumViewPreferences = DataStoreAlbumViewPreferences(context.albumsDataStore, viewDefaults)
}
