package com.appblish.jgallery.feature.photos.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import com.appblish.jgallery.feature.photos.DataStorePhotosPreferences
import com.appblish.jgallery.feature.photos.PhotosPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** CPU dispatcher the date-grouped timeline is built on (tests substitute a test dispatcher). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimelineDispatcher

/** Single DataStore instance for Photos-tab view settings (column count). */
private val Context.photosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_photos_ui",
)

@Module
@InstallIn(SingletonComponent::class)
object PhotosModule {

    @Provides
    @Singleton
    fun providePhotosPreferences(
        @ApplicationContext context: Context,
        viewDefaults: ViewDefaults,
    ): PhotosPreferences = DataStorePhotosPreferences(context.photosDataStore, viewDefaults)

    @Provides
    @TimelineDispatcher
    fun provideTimelineDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
