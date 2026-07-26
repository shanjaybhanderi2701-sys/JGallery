package com.appblish.jgallery.convention

/**
 * Standalone unit checks for [EgressDependencyPolicy.classify] covering BOTH the release (strict
 * zero-egress) and debug (bounded Crashlytics allowlist) branches introduced in APP-637.
 *
 * build-logic can't host a Gradle `src/test` source set without breaking the configuration cache,
 * so this is compiled + run against the REAL policy via `scripts/verify_egress_policy.sh`, which
 * invokes `kotlinc EgressDependencyPolicy.kt EgressPolicyChecks.kt` in one module (so this file can
 * see the `internal` object) and runs `main`. Exit code 1 on any failed assertion.
 */

private var failures = 0

private fun check(label: String, actual: EgressDependencyPolicy.Verdict, expected: EgressDependencyPolicy.Verdict) {
    // Denylisted carries a token payload; compare by type so token wording isn't pinned by the test.
    val ok = when {
        actual is EgressDependencyPolicy.Verdict.Denylisted && expected is EgressDependencyPolicy.Verdict.Denylisted -> true
        else -> actual == expected
    }
    if (ok) {
        println("  PASS  $label -> ${actual::class.simpleName}")
    } else {
        failures++
        println("  FAIL  $label -> got ${actual::class.simpleName}, expected ${expected::class.simpleName}")
    }
}

private fun classify(coordinate: String, strict: Boolean): EgressDependencyPolicy.Verdict {
    val group = coordinate.substringBefore(':')
    return EgressDependencyPolicy.classify(group, coordinate, strict)
}

fun main() {
    val Approved = EgressDependencyPolicy.Verdict.Approved
    val Drift = EgressDependencyPolicy.Verdict.Drift
    val Clean = EgressDependencyPolicy.Verdict.Clean
    val Denylisted = EgressDependencyPolicy.Verdict.Denylisted("")

    println("== DEBUG branch (strict = false): bounded Crashlytics allowlist ==")
    // Exact reviewed Crashlytics artifacts are exempt.
    check("firebase-crashlytics exempt", classify("com.google.firebase:firebase-crashlytics", strict = false), Approved)
    check("firebase-sessions exempt", classify("com.google.firebase:firebase-sessions", strict = false), Approved)
    check("play-services-measurement exempt", classify("com.google.android.gms:play-services-measurement", strict = false), Approved)
    check("transport-runtime exempt", classify("com.google.android.datatransport:transport-runtime", strict = false), Approved)
    // A Firebase/GMS surface NOT on the allowlist fails closed (Drift) — the APP-619 F1 hardening.
    check("firebase-messaging drifts", classify("com.google.firebase:firebase-messaging", strict = false), Drift)
    check("firebase-firestore drifts", classify("com.google.firebase:firebase-firestore", strict = false), Drift)
    check("play-services-ads drifts", classify("com.google.android.gms:play-services-ads", strict = false), Drift)
    // Non-Google network/analytics libs are always denylisted.
    check("okhttp denylisted", classify("com.squareup.okhttp3:okhttp", strict = false), Denylisted)
    check("retrofit denylisted", classify("com.squareup.retrofit2:retrofit", strict = false), Denylisted)
    check("sentry denylisted", classify("io.sentry:sentry-android", strict = false), Denylisted)
    // Ordinary deps are clean.
    check("androidx.core clean", classify("androidx.core:core-ktx", strict = false), Clean)
    check("empty group clean", EgressDependencyPolicy.classify("", "", strict = false), Clean)

    println("== RELEASE/benchmark branch (strict = true): hard zero-egress (APP-289/APP-637) ==")
    // NO Firebase exemption on a shipped variant — every egress-group artifact is a violation.
    check("firebase-crashlytics denylisted", classify("com.google.firebase:firebase-crashlytics", strict = true), Denylisted)
    check("firebase-analytics denylisted", classify("com.google.firebase:firebase-analytics", strict = true), Denylisted)
    check("play-services-measurement denylisted", classify("com.google.android.gms:play-services-measurement", strict = true), Denylisted)
    check("transport-runtime denylisted", classify("com.google.android.datatransport:transport-runtime", strict = true), Denylisted)
    // Non-Google network libs stay denylisted.
    check("okhttp denylisted", classify("com.squareup.okhttp3:okhttp", strict = true), Denylisted)
    // No Drift verdict exists on the strict branch — egress groups go straight to Denylisted.
    check("firebase-messaging denylisted (not drift)", classify("com.google.firebase:firebase-messaging", strict = true), Denylisted)
    // Ordinary deps remain clean on release too.
    check("androidx.core clean", classify("androidx.core:core-ktx", strict = true), Clean)
    check("kotlin-stdlib clean", classify("org.jetbrains.kotlin:kotlin-stdlib", strict = true), Clean)

    println()
    if (failures == 0) {
        println("ALL EGRESS POLICY CHECKS PASSED (both variant branches).")
    } else {
        println("$failures EGRESS POLICY CHECK(S) FAILED.")
        kotlin.system.exitProcess(1)
    }
}
