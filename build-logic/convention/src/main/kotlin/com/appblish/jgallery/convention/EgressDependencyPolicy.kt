package com.appblish.jgallery.convention

/**
 * Pure, unit-testable egress classification for a resolved runtime graph. Kept free of any Gradle
 * API type so the branch logic can be exercised in isolation (see `scripts/verify_egress_policy.sh`,
 * which compiles this file with a standalone `kotlinc` — build-logic can't carry a `src/test` source
 * set without breaking the configuration cache).
 *
 * **APP-637 — variant-aware (release stays zero-egress).** Firebase Crashlytics is now a DEBUG-ONLY
 * testing aid ([APP-636] decision); the shipped RELEASE/benchmark build returns to the original
 * [APP-289] hard zero-egress guarantee. [classify] therefore takes a [strict] flag:
 *
 *  - `strict = false` (**debug**): the bounded [APP-614]/[APP-619] policy — EXACTLY the reviewed
 *    Crashlytics artifact set under an approved Google group is [Verdict.Approved]; any OTHER
 *    artifact under an approved group is [Verdict.Drift] (fail closed, new Firebase/GMS surface);
 *    everything else falls through to the [DENYLIST].
 *  - `strict = true` (**release / benchmark**): zero-egress. There is NO exemption — ANY artifact
 *    under an approved Google egress group is [Verdict.Denylisted], and the denylist still catches
 *    every other network/analytics library. A shipped artifact must carry no egress at all.
 *
 * **APP-619 Finding 1 (retained for the debug branch).** The original APP-614 allowlist was
 * group-level (`com.google.firebase:`, `com.google.android.gms:`, `com.google.android.datatransport:`),
 * so the ENTIRE Firebase/GMS product surface — firebase-messaging, firebase-storage (user-file
 * upload), firestore, auth, play-services-ads, remote-config — would pass green even though only
 * Crashlytics is wanted. The debug branch narrows the exemption to an EXACT, reviewed artifact set
 * and fails closed ([Verdict.Drift]) on anything else under an approved group.
 */
internal object EgressDependencyPolicy {

    sealed interface Verdict {
        /** Not a network/analytics coordinate (or an un-versioned project component). Ignore. */
        object Clean : Verdict

        /** Exact member of the reviewed Crashlytics artifact set on the DEBUG classpath. Exempt. */
        object Approved : Verdict

        /**
         * Under an approved Google egress GROUP but NOT on the reviewed artifact allowlist — a new
         * Firebase/GMS surface. Fail closed pending review; do NOT treat as approved. (Debug branch.)
         */
        object Drift : Verdict

        /** A forbidden network/analytics library (APP-289 denylist hit, or ANY egress-group artifact under strict). */
        data class Denylisted(val token: String) : Verdict
    }

    /**
     * @param strict `true` for the release/benchmark zero-egress branch (no Firebase exemption);
     *   `false` for the debug bounded-allowlist branch.
     */
    fun classify(group: String, coordinate: String, strict: Boolean): Verdict {
        val g = group.lowercase()
        val c = coordinate.lowercase()
        return when {
            g.isEmpty() -> Verdict.Clean
            g in APPROVED_EGRESS_GROUPS ->
                // Release (strict): an egress-group artifact must never reach a shipped classpath.
                if (strict) {
                    Verdict.Denylisted("egress-group:$g (release is zero-egress — APP-289/APP-637)")
                } else {
                    // Debug (bounded): exact reviewed artifact = Approved; anything else = Drift.
                    if (c in APPROVED_EGRESS_ARTIFACTS) Verdict.Approved else Verdict.Drift
                }
            else -> DENYLIST.firstOrNull { c.contains(it) }?.let(Verdict::Denylisted) ?: Verdict.Clean
        }
    }

    /** APP-289 denylist. Matched as substrings of the lowercased `group:name` coordinate. */
    val DENYLIST = listOf(
        "retrofit",
        "okhttp",
        "ktor",
        "volley",
        "firebase",
        "analytics",
        "crashlytics",
        "sentry",
        "amplitude",
        "mixpanel",
        "segment",
        "apollo",
        "grpc",
    )

    /** The Google groups the board approved for the (debug-only) Crashlytics egress stack (APP-613/APP-614). */
    val APPROVED_EGRESS_GROUPS = setOf(
        "com.google.firebase",
        "com.google.android.gms",
        "com.google.android.datatransport",
    )

    /**
     * APP-619 Finding 1: the EXACT `group:name` artifacts the Crashlytics (+ its transitive
     * measurement) stack resolves to on `debugRuntimeClasspath` (Firebase BOM 33.5.1), captured from
     * a real `:app:dependencies` resolution. Only these are exempt on the DEBUG branch; any OTHER
     * artifact under an [APPROVED_EGRESS_GROUPS] group is [Verdict.Drift] and fails the build. This
     * is a permit-set, not a require-set: dropping firebase-analytics (APP-637) simply resolves a
     * SUBSET of these, which still passes.
     *
     * To legitimately add one (e.g. a future Firebase product the board approves), resolve the new
     * graph, add the exact coordinate here, and obtain a fresh Security sign-off in the SAME change.
     */
    val APPROVED_EGRESS_ARTIFACTS = setOf(
        "com.google.android.datatransport:transport-api",
        "com.google.android.datatransport:transport-backend-cct",
        "com.google.android.datatransport:transport-runtime",
        "com.google.android.gms:play-services-ads-identifier",
        "com.google.android.gms:play-services-base",
        "com.google.android.gms:play-services-basement",
        "com.google.android.gms:play-services-measurement",
        "com.google.android.gms:play-services-measurement-api",
        "com.google.android.gms:play-services-measurement-base",
        "com.google.android.gms:play-services-measurement-impl",
        "com.google.android.gms:play-services-measurement-sdk",
        "com.google.android.gms:play-services-measurement-sdk-api",
        "com.google.android.gms:play-services-stats",
        "com.google.android.gms:play-services-tasks",
        "com.google.firebase:firebase-analytics",
        "com.google.firebase:firebase-annotations",
        "com.google.firebase:firebase-bom",
        "com.google.firebase:firebase-common",
        "com.google.firebase:firebase-common-ktx",
        "com.google.firebase:firebase-components",
        "com.google.firebase:firebase-config-interop",
        "com.google.firebase:firebase-crashlytics",
        "com.google.firebase:firebase-datatransport",
        "com.google.firebase:firebase-encoders",
        "com.google.firebase:firebase-encoders-json",
        "com.google.firebase:firebase-encoders-proto",
        "com.google.firebase:firebase-installations",
        "com.google.firebase:firebase-installations-interop",
        "com.google.firebase:firebase-measurement-connector",
        "com.google.firebase:firebase-sessions",
    )
}
