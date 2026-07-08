package com.appblish.jgallery.feature.onboarding

import android.content.Intent

/**
 * The onboarding state machine from the boundary contract §4:
 *
 *   Loading ─ hasAccess? ─true─▶ Complete
 *      │ false
 *      ▼
 *   Language ─Done─▶ Primer ─Allow─▶ AwaitingAccess ─onResume/hasAccess─▶ Complete (or back to Primer)
 *
 * Every transition is driven by [com.appblish.jgallery.core.storage.StoragePermissionController]; the
 * step names are backend-agnostic, so swapping the permission model never changes this graph.
 */
enum class OnboardingStep { Loading, Language, Primer, AwaitingAccess, Complete }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Loading,
    val language: OnboardingLanguage = OnboardingLanguage.SystemDefault,
)

/**
 * One-shot side effects the host must run — the two arms of `AccessRequest`, kept abstract so the UI
 * layer never constructs a Settings-action intent or a permission string itself.
 */
sealed interface OnboardingEffect {

    /** All Files Access (R+): open the system settings page the controller resolved. */
    data class OpenSystemSettings(val intent: Intent) : OnboardingEffect

    /** Runtime-permissions arm (pre-R / media-permissions swap): launch the Activity Result request. */
    data class RequestRuntimePermissions(val permissions: List<String>) : OnboardingEffect
}
