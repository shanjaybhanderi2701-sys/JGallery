package com.appblish.jgallery.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive storage-access state for mid-session permission changes (spec §9, design W3-07).
 *
 * The access a JGallery user granted during onboarding can be revoked from system Settings while the
 * app is backgrounded. [StoragePermissionController.hasAccess] answers "right now?" on demand, but the
 * UI needs to *observe* the transition to raise the revoked banner + re-request sheet and to recover
 * silently once access returns. This monitor is that observable, layered over the §1.6 boundary so
 * feature code still never names a permission — swapping the backend (media permissions / SAF) leaves
 * every collector unchanged (spec §1.6, §9.4).
 *
 * Detection is pull-based on purpose: Android gives no reliable broadcast when All-Files Access is
 * toggled off, so the app shell drives [refresh] from the activity's `onResume` (and after returning
 * from the re-request sheet's "Open settings"). That is the exact lifecycle hook design W3-07 calls
 * for, and it keeps the boundary free of any Android lifecycle type.
 */
enum class StorageAccessState {
    /** Not yet checked this process — the initial value before the first [StorageAccessMonitor.refresh]. */
    Unknown,

    /** The app holds sufficient access to enumerate and operate on media. */
    Granted,

    /** Access was present and is now gone (or never granted): show the W3-07 banner, gate new file ops. */
    Revoked,
}

/**
 * Observable wrapper over [StoragePermissionController]. Injected wherever the app must react to
 * access being revoked or re-granted mid-session; the permission *check* still lives entirely behind
 * the boundary.
 */
@Singleton
class StorageAccessMonitor @Inject constructor(
    private val controller: StoragePermissionController,
) {

    private val _state = MutableStateFlow(StorageAccessState.Unknown)

    /**
     * Cold-collectable access state. Starts [StorageAccessState.Unknown] until the first [refresh];
     * thereafter it holds the last observed value so a newly-composed collector renders the correct
     * banner state immediately, without hitting the system again.
     */
    val state: StateFlow<StorageAccessState> = _state.asStateFlow()

    /**
     * Re-check access through the §1.6 boundary and publish the result. Idempotent and cheap; safe to
     * call on any dispatcher because [StoragePermissionController.hasAccess] hops to IO itself. A
     * [StateFlow] conflates equal values, so repeated resumes with unchanged access emit nothing.
     */
    suspend fun refresh() {
        _state.value =
            if (controller.hasAccess()) StorageAccessState.Granted else StorageAccessState.Revoked
    }
}
