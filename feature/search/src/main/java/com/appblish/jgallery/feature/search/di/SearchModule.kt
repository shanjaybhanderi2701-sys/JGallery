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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** CPU dispatcher search matching runs on, off the main thread (tests substitute a test dispatcher). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SearchDispatcher

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

    @Provides
    @SearchDispatcher
    fun provideSearchDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
