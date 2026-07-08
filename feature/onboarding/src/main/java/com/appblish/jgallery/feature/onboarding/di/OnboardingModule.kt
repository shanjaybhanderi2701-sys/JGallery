package com.appblish.jgallery.feature.onboarding.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.appblish.jgallery.feature.onboarding.DataStoreOnboardingPreferences
import com.appblish.jgallery.feature.onboarding.OnboardingPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Single DataStore instance for onboarding prefs (language + language-picked flag). */
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jgallery_onboarding",
)

@Module
@InstallIn(SingletonComponent::class)
object OnboardingModule {

    @Provides
    @Singleton
    fun provideOnboardingPreferences(
        @ApplicationContext context: Context,
    ): OnboardingPreferences = DataStoreOnboardingPreferences(context.onboardingDataStore)
}
