package com.appblish.jgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
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
