package com.appblish.jgallery.feature.onboarding

/**
 * Non-trust onboarding UI copy. Kept out of [TrustCopy] on purpose: only the safety-claim strings are
 * integrity-gated, but the primer copy below is still SPEC-LOCKED (spec §9): the title, body and CTA
 * are fixed verbatim and must not be reworded.
 */
object OnboardingCopy {
    const val LANGUAGE_TITLE: String = "App Language"
    const val LANGUAGE_SUBTITLE: String = "Choose the language you'd like to use JGallery in."
    const val LANGUAGE_CTA: String = "Done"

    // Spec §9 — locked verbatim.
    const val PRIMER_TITLE: String = "Permission Required"
    const val PRIMER_BODY: String = "We only use this permission to display and manage your media files."
    const val PRIMER_CTA: String = "Allow"

    /** Mock of the system toggle the primer previews (spec §9.2). */
    const val PRIMER_TOGGLE_LABEL: String = "Allow access to manage all files"
}
