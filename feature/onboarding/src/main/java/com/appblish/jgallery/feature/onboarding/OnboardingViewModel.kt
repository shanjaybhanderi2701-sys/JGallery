package com.appblish.jgallery.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appblish.jgallery.core.storage.AccessRequest
import com.appblish.jgallery.core.storage.StoragePermissionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the onboarding state machine (boundary contract §4) purely through the
 * [StoragePermissionController] abstraction — it never names a permission string, a Settings action,
 * or any `android.*` storage API, so migrating the permission model touches only the controller
 * binding in `StorageModule`, not this flow (spec §1.6).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissions: StoragePermissionController,
    private val preferences: OnboardingPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    init {
        evaluateEntry()
    }

    /** Decide the initial step: skip onboarding when access is already held (spec §9 DoD). */
    private fun evaluateEntry() {
        viewModelScope.launch {
            val language = preferences.language.first()
            val step = when {
                permissions.hasAccess() -> OnboardingStep.Complete
                preferences.hasPickedLanguage.first() -> OnboardingStep.Primer // revoked-later re-entry
                else -> OnboardingStep.Language
            }
            _state.value = OnboardingUiState(step = step, language = language)
        }
    }

    fun onLanguageSelected(language: OnboardingLanguage) {
        _state.update { it.copy(language = language) }
    }

    /** "Done" on the language screen: persist the pick, advance to the primer. */
    fun onLanguageConfirmed() {
        viewModelScope.launch {
            preferences.setLanguage(_state.value.language)
            _state.update { it.copy(step = OnboardingStep.Primer) }
        }
    }

    /**
     * "Allow" on the primer: act on the abstract [AccessRequest] without knowing which backend produced
     * it. Either arm moves us to [OnboardingStep.AwaitingAccess]; the host runs the emitted effect.
     */
    fun onAllowClicked() {
        _state.update { it.copy(step = OnboardingStep.AwaitingAccess) }
        val effect = when (val request = permissions.accessRequest()) {
            is AccessRequest.SystemSettings -> OnboardingEffect.OpenSystemSettings(request.intent)
            is AccessRequest.RuntimePermissions -> OnboardingEffect.RequestRuntimePermissions(request.permissions)
        }
        _effects.trySend(effect)
    }

    /**
     * Re-check access — called from `onResume` (return from the system page) and from the runtime
     * permission result. Grants advance into the app; a return without a grant falls back to the
     * primer (spec §9: the primer persists and is the honest re-entry point).
     */
    fun refreshAccess() {
        viewModelScope.launch {
            when {
                permissions.hasAccess() ->
                    _state.update { it.copy(step = OnboardingStep.Complete) }
                _state.value.step == OnboardingStep.AwaitingAccess ->
                    _state.update { it.copy(step = OnboardingStep.Primer) }
            }
        }
    }
}
