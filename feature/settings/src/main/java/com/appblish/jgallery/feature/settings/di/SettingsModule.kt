package com.appblish.jgallery.feature.settings.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.feature.settings.DataStoreSettingsPreferences
import com.appblish.jgallery.feature.settings.SettingsPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single DataStore instance for app-wide Settings prefs (theme / defaults / slideshow). */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_settings",
)

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsPreferences(
        @ApplicationContext context: Context,
    ): SettingsPreferences = DataStoreSettingsPreferences(context.settingsDataStore)
}
