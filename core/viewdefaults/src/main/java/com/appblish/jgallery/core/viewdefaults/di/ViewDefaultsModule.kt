package com.appblish.jgallery.core.viewdefaults.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.core.viewdefaults.DataStoreViewDefaults
import com.appblish.jgallery.core.viewdefaults.ViewDefaults
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Single DataStore instance for the app-wide view-defaults seam (default sort + grid density). Its
 * own store so it is a peer of the Settings / Photos / Albums stores, not owned by any one feature —
 * Settings writes it, Photos and Albums read it.
 */
private val Context.viewDefaultsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_view_defaults",
)

@Module
@InstallIn(SingletonComponent::class)
object ViewDefaultsModule {

    @Provides
    @Singleton
    fun provideViewDefaults(
        @ApplicationContext context: Context,
    ): ViewDefaults = DataStoreViewDefaults(context.viewDefaultsDataStore)
}
