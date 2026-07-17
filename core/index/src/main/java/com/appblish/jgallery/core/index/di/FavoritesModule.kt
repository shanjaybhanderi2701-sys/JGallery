package com.appblish.jgallery.core.index.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.core.index.DataStoreFavoritesStore
import com.appblish.jgallery.core.index.FavoritesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single DataStore instance for the user's favorite media (G2 · APP-543). */
private val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_favorites",
)

/** Binds the persistent [FavoritesStore]. Its own DataStore file, separate from the index cache. */
@Module
@InstallIn(SingletonComponent::class)
object FavoritesModule {

    @Provides
    @Singleton
    fun provideFavoritesStore(
        @ApplicationContext context: Context,
    ): FavoritesStore = DataStoreFavoritesStore(context.favoritesDataStore)
}
