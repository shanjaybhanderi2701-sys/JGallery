package com.appblish.jgallery.core.storage

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * W3-07 mid-session permission robustness (spec §9): the monitor must observe access being revoked
 * from Settings while the app is backgrounded and, crucially, recover to [StorageAccessState.Granted]
 * once the user re-grants — with no feature code ever naming a permission (spec §1.6).
 */
class StorageAccessMonitorTest {

    @Test
    fun `initial state is Unknown before any refresh`() = runTest {
        val monitor = StorageAccessMonitor(FakePermissionController(hasAccess = false))

        assertThat(monitor.state.value).isEqualTo(StorageAccessState.Unknown)
    }

    @Test
    fun `refresh publishes Granted when access is held`() = runTest {
        val monitor = StorageAccessMonitor(FakePermissionController(hasAccess = true))

        monitor.refresh()

        assertThat(monitor.state.value).isEqualTo(StorageAccessState.Granted)
    }

    @Test
    fun `access revoked mid-session flips Granted to Revoked on the next resume`() = runTest {
        val controller = FakePermissionController(hasAccess = true)
        val monitor = StorageAccessMonitor(controller)
        monitor.refresh()

        controller.hasAccess = false // user turned All-Files Access off in Settings
        monitor.refresh() // driven by onResume

        assertThat(monitor.state.value).isEqualTo(StorageAccessState.Revoked)
    }

    @Test
    fun `re-granting recovers to Granted — state is not latched`() = runTest {
        val controller = FakePermissionController(hasAccess = true)
        val monitor = StorageAccessMonitor(controller)
        monitor.refresh()
        controller.hasAccess = false
        monitor.refresh()

        controller.hasAccess = true // user re-granted from the re-request sheet
        monitor.refresh()

        assertThat(monitor.state.value).isEqualTo(StorageAccessState.Granted)
    }
}

private class FakePermissionController(var hasAccess: Boolean) : StoragePermissionController {
    override val backend = StorageBackend.ALL_FILES_ACCESS
    override suspend fun hasAccess(): Boolean = hasAccess
    override fun accessRequest(): AccessRequest =
        AccessRequest.RuntimePermissions(permissions = emptyList())
}
