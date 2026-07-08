package com.appblish.jgallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.appblish.jgallery.core.ui.theme.JGalleryTheme
import com.appblish.jgallery.feature.onboarding.OnboardingGate
import com.appblish.jgallery.feature.onboarding.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Hoisted to the activity so onResume can re-check access when the user returns from the system
    // All-Files page (spec §9 / boundary contract §4). The same instance backs the gate below.
    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JGalleryTheme {
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
