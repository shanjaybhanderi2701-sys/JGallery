package com.appblish.jgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appblish.jgallery.core.model.ThemeMode
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.onboarding.OnboardingGate
import com.appblish.jgallery.feature.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hoisted to the activity so onResume can re-check access when the user returns from the system
    // All-Files page (spec §9 / boundary contract §4). The same instance backs the gate below.
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    // Root theme preference (G2 Settings §3): drives JGalleryTheme(darkTheme=…) so the whole app
    // re-themes when the user changes the theme in Settings.
    private val appThemeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by appThemeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            JGalleryTheme(darkTheme = darkTheme) {
                // APP-643 (#7): drive the system status-/nav-bar icon contrast off the app's *resolved*
                // theme, not the OS uiMode. `enableEdgeToEdge()`'s auto style keys icon colour off the
                // system dark setting, so a Settings-forced dark theme on a light OS (or vice-versa)
                // leaves black icons on our dark chrome (the APP-603 override gotcha). Light icons in
                // dark theme, dark icons in light theme, re-applied whenever the resolved theme flips.
                // Applied at the window via WindowInsetsControllerCompat appearance flags — the deprecated
                // setStatusBarColor is a no-op on targetSdk 35 (APP-593/605). Scoped surfaces (the viewer,
                // the selection bar) still override this while active and restore it on exit.
                val view = LocalView.current
                if (!view.isInEditMode) {
                    LaunchedEffect(darkTheme) {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                // Gate the app shell behind storage access (spec §9): already-granted launches drop
                // straight into JGalleryApp(); first-run users get language → primer → trust overlay.
                OnboardingGate(viewModel = onboardingViewModel) {
                    JGalleryApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Return path from the system All-Files page: advance into the app if access was granted,
        // otherwise fall back to the primer. No android.* storage refs here — all via the controller.
        onboardingViewModel.refreshAccess()
    }
}
