package com.appblish.jgallery.feature.onboarding

import com.appblish.jgallery.core.storage.StorageAccessState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The four W3-07 recovery states derive purely from access state + cache/permanent-denial facts. */
class PermissionRecoveryStateTest {

    @Test
    fun `granted or unknown shows no recovery chrome`() {
        for (cached in listOf(true, false)) {
            for (denied in listOf(true, false)) {
                assertThat(permissionRecoveryUi(StorageAccessState.Granted, cached, denied))
                    .isEqualTo(PermissionRecoveryUi.None)
                assertThat(permissionRecoveryUi(StorageAccessState.Unknown, cached, denied))
                    .isEqualTo(PermissionRecoveryUi.None)
            }
        }
    }

    @Test
    fun `revoked with cache shows the banner over the last-known grid`() {
        assertThat(
            permissionRecoveryUi(StorageAccessState.Revoked, hasCachedContent = true, permanentlyDenied = false),
        ).isEqualTo(PermissionRecoveryUi.RevokedWithCache)
    }

    @Test
    fun `revoked cold falls back to the full-screen empty-permission state`() {
        assertThat(
            permissionRecoveryUi(StorageAccessState.Revoked, hasCachedContent = false, permanentlyDenied = false),
        ).isEqualTo(PermissionRecoveryUi.RevokedCold)
    }

    @Test
    fun `permanent denial wins over cache so recovery routes to settings`() {
        // Even with a cached view, "don't ask again" must send the user to system settings.
        assertThat(
            permissionRecoveryUi(StorageAccessState.Revoked, hasCachedContent = true, permanentlyDenied = true),
        ).isEqualTo(PermissionRecoveryUi.PermanentlyDenied)
        assertThat(
            permissionRecoveryUi(StorageAccessState.Revoked, hasCachedContent = false, permanentlyDenied = true),
        ).isEqualTo(PermissionRecoveryUi.PermanentlyDenied)
    }
}
