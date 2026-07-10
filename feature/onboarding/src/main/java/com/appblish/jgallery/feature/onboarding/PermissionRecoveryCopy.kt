package com.appblish.jgallery.feature.onboarding

/**
 * Copy for the mid-session permission-recovery banner + re-request sheet (design W3-07, spec §9).
 *
 * Integrity rule (spec §9.3): the recovery sheet reuses the SAME safety claim as onboarding —
 * [TrustCopy.BODY], gated by [TrustCopy.claimApproved] — and introduces **no new claims** beyond
 * W1-02/03. Everything here is either a neutral status/instruction line or points back at [TrustCopy];
 * a reviewer can therefore still audit every safety promise in [TrustCopy] alone.
 */
object PermissionRecoveryCopy {

    /** Banner heading — a neutral status statement, no claim. */
    const val BANNER_TITLE: String = "Storage access was turned off"

    /** Banner subtext: why the grid is frozen. Honest, no blame, no claim. */
    const val BANNER_BODY: String =
        "JGallery can't read your photos until access is turned back on. What you see is the last view."

    /** Banner action that opens the re-request sheet. */
    const val BANNER_ACTION: String = "Turn access back on"

    /** Re-request sheet heading. */
    const val SHEET_TITLE: String = "Turn storage access back on"

    /** Sheet reason line — honest, matches the onboarding primer's framing (no new claim). */
    const val SHEET_REASON: String =
        "JGallery needs access to show and manage your photos and videos. That's the only reason it asks."

    /** Primary action: deep-links to the system All-Files page (spec §9.3). */
    const val SHEET_OPEN_SETTINGS: String = "Open settings"

    /** Secondary action: dismiss and keep browsing the cached view. */
    const val SHEET_NOT_NOW: String = "Not now"

    /** Extra line shown only when the system won't re-prompt — the toggle lives in settings now. */
    const val PERMANENTLY_DENIED_HINT: String =
        "Access is off and the app can no longer ask directly. Turn it on for JGallery in system settings."
}
