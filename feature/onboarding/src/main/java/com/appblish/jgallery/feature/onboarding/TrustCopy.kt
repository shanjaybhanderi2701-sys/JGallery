package com.appblish.jgallery.feature.onboarding

/**
 * The SINGLE, auditable home for the trust-overlay copy.
 *
 * §1.6 boundary contract §5 + spec §9.3 integrity rule (HARD): the green "JGallery is Safe & Secure"
 * overlay may be shown ONLY because it is TRUE — on-device operation, no upload, no collection. The
 * copy is therefore centralised here (never inline free-text per screen) so a reviewer / Security
 * Engineer can audit every claim in one place, and so it can be pulled in one edit if any future code
 * adds network/analytics egress of file data.
 *
 * Allowed claims (spec §9.3, locked): only [TITLE] + [BODY]. No "encrypted", no "certified", no
 * security promise beyond on-device / no-upload.
 *
 * ── Security sign-off gate ─────────────────────────────────────────────────────────────────────
 * The branded claim ([TITLE] / [BODY]) is BLOCKED on Security Engineer sign-off (contract §5, §6).
 * Until [signOff] is flipped to [SecuritySignOff.Approved] — a one-line, reviewable change the
 * sign-off authorises — the overlay renders the claim-free [POINTER] variant only. This makes it
 * impossible to ship the unverified safety claim before Security has confirmed it.
 */
object TrustCopy {

    /** Overlay heading. Shown ONLY when [claimApproved]. */
    const val TITLE: String = "JGallery is Safe & Secure"

    /** Overlay body — the only permitted safety claim (spec §9.3). Shown ONLY when [claimApproved]. */
    const val BODY: String =
        "JGallery works fully on your device. Your photos and videos are never uploaded or shared."

    /**
     * Claim-free instruction that always renders (no safety promise, so it needs no sign-off). Points
     * the user at the system toggle on the All-Files page.
     */
    const val POINTER: String = "Turn on access for JGallery below to let it show your photos and videos."

    /**
     * Security Engineer sign-off state. Flipped to [SecuritySignOff.Approved] per the APP-285
     * `security-signoff` document (pre-authorized, one-line change, gated on the APP-289 CI egress
     * guard being in place). Standing rule: if egress capability is ever introduced, this sign-off
     * is VOID — revert to [SecuritySignOff.Pending] and pull the registered claim files in the
     * same change. The egress guard (`:app:verifyNoEgress`) forces exactly that by failing CI.
     */
    val signOff: SecuritySignOff = SecuritySignOff.Approved(
        approver = "Security Engineer",
        date = "2026-07-08",
        note = "On-device only; no INTERNET permission, no network/analytics deps, no egress APIs. " +
            "Claim is gated by the CI egress guard and MUST be pulled if egress is ever added.",
    )

    /** True only once Security has signed off — the branded safety claim may render. */
    val claimApproved: Boolean get() = signOff is SecuritySignOff.Approved
}

/** Auditable record of whether the trust claim has cleared Security review (contract §5). */
sealed interface SecuritySignOff {

    /** No sign-off yet — the overlay must NOT show the safety claim. */
    data object Pending : SecuritySignOff

    /** Security confirmed the claim is true; the branded overlay may ship. */
    data class Approved(val approver: String, val date: String, val note: String) : SecuritySignOff
}
