package com.appblish.jgallery.feature.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted onboarding state (spec §9 hard constraint: "Persist selected language + onboarding flag").
 * An interface so the [OnboardingViewModel] is unit-testable with an in-memory fake — the DataStore
 * binding lives in [di.OnboardingModule].
 */
interface OnboardingPreferences {

    /** The user's chosen language (defaults to [OnboardingLanguage.SystemDefault]). */
    val language: Flow<OnboardingLanguage>

    /**
     * True once the user has passed the language screen at least once. Drives re-entry: if access is
     * later revoked we return to the primer, not the language picker (spec §9: "primer is the re-entry").
     */
    val hasPickedLanguage: Flow<Boolean>

    /** Record the language pick and mark the language step complete (single atomic write). */
    suspend fun setLanguage(language: OnboardingLanguage)
}

/** DataStore-backed [OnboardingPreferences]. */
internal class DataStoreOnboardingPreferences(
    private val dataStore: DataStore<Preferences>,
) : OnboardingPreferences {

    override val language: Flow<OnboardingLanguage> =
        dataStore.data.map { OnboardingLanguage.fromTag(it[KEY_LANGUAGE_TAG]) }

    override val hasPickedLanguage: Flow<Boolean> =
        dataStore.data.map { it[KEY_LANGUAGE_PICKED] ?: false }

    override suspend fun setLanguage(language: OnboardingLanguage) {
        dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE_TAG] = language.tag
            prefs[KEY_LANGUAGE_PICKED] = true
        }
    }

    private companion object {
        val KEY_LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val KEY_LANGUAGE_PICKED = booleanPreferencesKey("language_picked")
    }
}
