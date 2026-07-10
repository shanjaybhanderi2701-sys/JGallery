package com.appblish.jgallery.feature.onboarding

import com.appblish.jgallery.core.storage.StorageAccessState

/**
 * The UI shape the mid-session permission-recovery flow should take (design W3-07). Derived purely
 * from the reactive [StorageAccessState] plus two boundary-agnostic facts the app shell already
 * knows — whether there is cached content to fall back on, and whether the last request was
 * permanently denied. Keeping this a pure function (no Compose, no Android, no permission name) means
 * every transition is unit-testable and the §1.6 boundary stays intact: if the permission model
 * swaps, only how the shell derives [permanentlyDenied] changes, never this mapping.
 */
enum class PermissionRecoveryUi {
    /** Access is present (or was just re-granted): no recovery chrome — clear the banner, re-index. */
    None,

    /** Revoked but the last index is still cached: show the banner over the dimmed last-known grid. */
    RevokedWithCache,

    /** Revoked with nothing cached: full-screen empty-permission state (reuses the W1-02 primer). */
    RevokedCold,

    /** Revoked and the system won't re-prompt: banner + sheet must deep-link to system settings. */
    PermanentlyDenied,
}

/**
 * Map the observed access [state] to the recovery UI (design W3-07's four states). `Granted`/`Unknown`
 * yield [PermissionRecoveryUi.None] — the "re-granted" case is exactly `Revoked → Granted`, which the
 * shell handles by re-running indexing and letting this drop back to `None`.
 *
 * @param hasCachedContent true when the previous index still has media to render behind the banner.
 * @param permanentlyDenied true when the last access request was denied with "don't ask again"
 *   semantics, so an in-app re-request can't re-prompt and must send the user to system settings.
 */
fun permissionRecoveryUi(
    state: StorageAccessState,
    hasCachedContent: Boolean,
    permanentlyDenied: Boolean,
): PermissionRecoveryUi = when (state) {
    StorageAccessState.Granted, StorageAccessState.Unknown -> PermissionRecoveryUi.None
    StorageAccessState.Revoked -> when {
        permanentlyDenied -> PermissionRecoveryUi.PermanentlyDenied
        hasCachedContent -> PermissionRecoveryUi.RevokedWithCache
        else -> PermissionRecoveryUi.RevokedCold
    }
}
