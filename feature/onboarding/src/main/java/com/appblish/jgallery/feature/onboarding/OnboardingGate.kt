package com.appblish.jgallery.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.appblish.jgallery.feature.onboarding.screen.OnboardingLanguageScreen
import com.appblish.jgallery.feature.onboarding.screen.PermissionPrimerScreen
import com.appblish.jgallery.feature.onboarding.screen.TrustOverlayScreen

/**
 * The onboarding gate — wrap this around the app shell in `MainActivity` (spec §9 hard constraint:
 * "Wire the gate ahead of `JGalleryApp()`"). It shows [content] once storage access is held, and the
 * language → primer → trust-overlay flow otherwise. All access decisions go through the injected
 * [OnboardingViewModel] → [com.appblish.jgallery.core.storage.StoragePermissionController]; this
 * composable references no `android.*` storage API (only an opaque `Intent` handed back to it).
 *
 * The `onResume` re-check (contract §4: return path from the system All-Files page) is driven by the
 * host `MainActivity` calling [OnboardingViewModel.refreshAccess], so pass the same VM instance in.
 */
@Composable
fun OnboardingGate(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Runtime-permissions arm (pre-R / media-permissions swap). Result → re-check access.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshAccess() }

    // Run the one-shot effects: open the system All-Files page, or request runtime permissions.
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OnboardingEffect.OpenSystemSettings -> context.startActivity(effect.intent)
                is OnboardingEffect.RequestRuntimePermissions ->
                    permissionLauncher.launch(effect.permissions.toTypedArray())
            }
        }
    }

    when (state.step) {
        OnboardingStep.Loading -> OnboardingLoading(modifier)
        OnboardingStep.Language -> OnboardingLanguageScreen(
            selected = state.language,
            onSelect = viewModel::onLanguageSelected,
            onDone = viewModel::onLanguageConfirmed,
            modifier = modifier,
        )
        OnboardingStep.Primer -> PermissionPrimerScreen(
            onAllow = viewModel::onAllowClicked,
            modifier = modifier,
        )
        OnboardingStep.AwaitingAccess -> TrustOverlayScreen(modifier = modifier)
        OnboardingStep.Complete -> content()
    }
}

/** Brief spinner while the entry gate resolves `hasAccess()` (no visible flash for granted users). */
@Composable
private fun OnboardingLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
