package com.appblish.jgallery.feature.onboarding

import com.appblish.jgallery.core.storage.AccessRequest
import com.appblish.jgallery.core.storage.StorageBackend
import com.appblish.jgallery.core.storage.StoragePermissionController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit-tests the onboarding state machine (boundary contract §4) against fakes of the two boundary
 * seams. The ViewModel names no `android.*` storage API, so these fakes fully stand in for the real
 * All-Files controller — the same property the §1.6 swap-safety rule guarantees.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `already-granted access skips onboarding straight to Complete`() = runTest {
        val vm = OnboardingViewModel(FakeController(granted = true), FakePreferences())
        advanceUntilIdle()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Complete)
    }

    @Test
    fun `first run with no access starts at the language screen`() = runTest {
        val vm = OnboardingViewModel(FakeController(granted = false), FakePreferences())
        advanceUntilIdle()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Language)
    }

    @Test
    fun `revoked re-entry after language pick lands on the primer, not the language screen`() = runTest {
        val prefs = FakePreferences(picked = true, language = OnboardingLanguage.Hindi)
        val vm = OnboardingViewModel(FakeController(granted = false), prefs)
        advanceUntilIdle()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Primer)
        assertThat(vm.state.value.language).isEqualTo(OnboardingLanguage.Hindi)
    }

    @Test
    fun `confirming language persists it and advances to the primer`() = runTest {
        val prefs = FakePreferences()
        val vm = OnboardingViewModel(FakeController(granted = false), prefs)
        advanceUntilIdle()

        vm.onLanguageSelected(OnboardingLanguage.Spanish)
        vm.onLanguageConfirmed()
        advanceUntilIdle()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Primer)
        assertThat(prefs.languageState.value).isEqualTo(OnboardingLanguage.Spanish)
        assertThat(prefs.pickedState.value).isTrue()
    }

    @Test
    fun `Allow emits the request effect and awaits access`() = runTest {
        // Unit-tests exercise the RuntimePermissions arm (no android Intent to mock — the same reason
        // StorageBoundarySwapTest leaves the SystemSettings/startActivity arm to instrumented tests).
        val vm = OnboardingViewModel(FakeController(granted = false), FakePreferences())
        advanceUntilIdle()

        val effects = mutableListOf<OnboardingEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }
        runCurrent() // start the collector so it's subscribed before the effect is sent

        vm.onAllowClicked()
        advanceUntilIdle()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.AwaitingAccess)
        assertThat(effects).hasSize(1)
        val effect = effects.first()
        assertThat(effect).isInstanceOf(OnboardingEffect.RequestRuntimePermissions::class.java)
        assertThat((effect as OnboardingEffect.RequestRuntimePermissions).permissions)
            .containsExactly("android.permission.READ_EXTERNAL_STORAGE")
    }

    @Test
    fun `refreshAccess after a grant advances into the app`() = runTest {
        val controller = FakeController(granted = false)
        val vm = OnboardingViewModel(controller, FakePreferences())
        advanceUntilIdle()
        vm.onAllowClicked()
        advanceUntilIdle()

        controller.granted = true // user flipped the system toggle
        vm.refreshAccess()
        advanceUntilIdle()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Complete)
    }

    @Test
    fun `refreshAccess while still denied falls back to the primer`() = runTest {
        val vm = OnboardingViewModel(FakeController(granted = false), FakePreferences())
        advanceUntilIdle()
        vm.onAllowClicked() // -> AwaitingAccess
        advanceUntilIdle()

        vm.refreshAccess() // returned without granting
        advanceUntilIdle()

        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.Primer)
    }

    @Test
    fun `trust claim is ON with a recorded Security sign-off`() {
        // Integrity rule (contract §5): the branded claim renders only against an auditable
        // Approved record. Flipped per the APP-285 security-signoff doc, gated by the APP-289
        // CI egress guard (:app:verifyNoEgress). If egress ever lands, revert to Pending and
        // pull the registered claim files in the same change.
        assertThat(TrustCopy.claimApproved).isTrue()
        val signOff = TrustCopy.signOff
        assertThat(signOff).isInstanceOf(SecuritySignOff.Approved::class.java)
        assertThat((signOff as SecuritySignOff.Approved).approver).isEqualTo("Security Engineer")
        assertThat(signOff.date).isEqualTo("2026-07-08")
    }

    // --- Fakes -------------------------------------------------------------------------------------

    private class FakeController(
        @Volatile var granted: Boolean,
        override val backend: StorageBackend = StorageBackend.ALL_FILES_ACCESS,
    ) : StoragePermissionController {
        override suspend fun hasAccess(): Boolean = granted
        // Pre-R All-Files arm: a runtime grant. Avoids constructing an android Intent in a JVM test.
        override fun accessRequest(): AccessRequest =
            AccessRequest.RuntimePermissions(listOf("android.permission.READ_EXTERNAL_STORAGE"))
    }

    private class FakePreferences(
        picked: Boolean = false,
        language: OnboardingLanguage = OnboardingLanguage.SystemDefault,
    ) : OnboardingPreferences {
        val languageState = MutableStateFlow(language)
        val pickedState = MutableStateFlow(picked)
        override val language: Flow<OnboardingLanguage> get() = languageState
        override val hasPickedLanguage: Flow<Boolean> get() = pickedState
        override suspend fun setLanguage(language: OnboardingLanguage) {
            languageState.value = language
            pickedState.value = true
        }
    }
}
