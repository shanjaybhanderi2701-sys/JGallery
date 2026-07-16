package com.appblish.jgallery.feature.search.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.feature.search.DataStoreRecentSearchStore
import com.appblish.jgallery.feature.search.RecentSearchStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single DataStore instance for recent-search history (spec S4.4/S6). */
private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_recent_searches",
)

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideRecentSearchStore(
        @ApplicationContext context: Context,
    ): RecentSearchStore = DataStoreRecentSearchStore(context.recentSearchDataStore)
}
