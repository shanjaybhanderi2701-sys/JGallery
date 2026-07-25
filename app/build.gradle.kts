plugins {
    alias(libs.plugins.jgallery.android.application)
    alias(libs.plugins.jgallery.android.compose)
    alias(libs.plugins.jgallery.android.hilt)
    // CI egress guard (APP-289) — now VARIANT-AWARE (APP-637): the `release`/`benchmark` (shipped)
    // variants enforce the original hard zero-egress policy; the `debug` variant keeps the bounded
    // APP-614/APP-619 Crashlytics allowlist. See EgressGuardConventionPlugin.
    alias(libs.plugins.jgallery.egress.guard)
    // Firebase Crashlytics is a DEBUG-ONLY testing aid (APP-637 / APP-636), feeding the APP-615 MCP
    // crash-reading loop. Gradle plugins can't be scoped to a variant, so these are applied
    // project-wide but their config lives in app/src/debug/ (google-services.json) and every
    // non-debug google-services/crashlytics task is DISABLED below — nothing Firebase is ever baked
    // into a shipped release/benchmark artifact, which stays zero-egress by construction.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.appblish.jgallery"
}

// APP-637: keep Firebase processing on the DEBUG variant only. google-services.json lives under
// app/src/debug/, so the release/benchmark google-services tasks (which would look for it) and the
// matching Crashlytics tasks are disabled — the shipped artifacts process no Firebase config and
// carry no Crashlytics. Debug is unminified, so it needs no R8 mapping upload either (mapping upload
// stays off everywhere). `matching { }.configureEach { }` is lazy and configuration-cache-safe.
tasks.matching { task ->
    val name = task.name
    val nonDebug = !name.contains("Debug")
    nonDebug && (
        (name.startsWith("process") && name.endsWith("GoogleServices")) ||
            name.contains("Crashlytics")
        )
}.configureEach { enabled = false }

dependencies {
    // Core — depended on directly so all Hilt DI modules are aggregated by the app.
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:index"))
    implementation(project(":core:thumbs"))
    implementation(project(":core:playback"))
    implementation(project(":core:ui"))

    // Features.
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:albums"))
    implementation(project(":feature:photos"))
    implementation(project(":feature:collections"))
    implementation(project(":feature:search"))
    implementation(project(":feature:viewer"))
    implementation(project(":feature:trash"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.hilt.navigation.compose)

    // Firebase Crashlytics — DEBUG-ONLY crash-capture aid (APP-637 / APP-636), feeding the APP-615
    // MCP crash-reading loop. Deliberately on `debugImplementation` so the RELEASE/benchmark runtime
    // classpath resolves with ZERO com.google.firebase / com.google.android.gms artifacts (verify:
    // `:app:dependencies --configuration releaseRuntimeClasspath`), keeping the shipped build's §9.3
    // zero-egress guarantee intact. Crashlytics auto-initializes via its ContentProvider (no
    // Application code). firebase-analytics is intentionally DROPPED (APP-637): this is a pure crash
    // aid — no product analytics, no AdID/SSAID, nothing beyond crash capture.
    debugImplementation(platform(libs.firebase.bom))
    debugImplementation(libs.firebase.crashlytics)

    // Benchmark-variant ONLY: real HEIC encoding for the macrobenchmark corpus seeder (APP-390).
    // Never on the shipped debug/release classpath, so the egress guard is unaffected.
    "benchmarkImplementation"(libs.androidx.heifwriter)

    // Instrumented-test lane (Compose UI). BOM + ui-test-junit4 come from the compose convention.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
