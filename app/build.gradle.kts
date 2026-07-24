import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension

plugins {
    alias(libs.plugins.jgallery.android.application)
    alias(libs.plugins.jgallery.android.compose)
    alias(libs.plugins.jgallery.android.hilt)
    // CI egress guard (APP-289): now RELAXED (APP-614, board Option A) to allow INTERNET +
    // Google Firebase/GMS deps for Crashlytics; every other network permission and dependency
    // stays blocked. See EgressGuardConventionPlugin for the exact allowlist.
    alias(libs.plugins.jgallery.egress.guard)
    // Firebase Crashlytics (APP-614). google-services binds app/google-services.json; crashlytics
    // uploads the release R8 mapping file so production stack traces de-obfuscate.
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.appblish.jgallery"

    buildTypes {
        getByName("release") {
            // Release symbol upload (APP-614 · into the 1.0.0 .aab lane, APP-513): the R8 mapping
            // file is uploaded to Firebase on every release build (assemble/bundleRelease) so
            // obfuscated crash stack traces resolve to source symbols in the console.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = true
                // JGallery is pure Kotlin/JVM (no CMake/.so, no externalNativeBuild) → there are
                // no native symbols to upload. Kept explicit so a future NDK dep is a deliberate flip.
                nativeSymbolUploadEnabled = false
            }
        }
        // Debug + benchmark: no mapping upload (unminified / local-only) to keep those lanes offline.
        getByName("debug") {
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
        getByName("benchmark") {
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
    }
}

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

    // Firebase Crashlytics (APP-614) — BOM pins the crashlytics/analytics versions together.
    // Crashlytics auto-initializes via its ContentProvider (no Application code); analytics gives
    // crash-free-users + breadcrumb context. These are the ONLY egress-guard-approved network deps.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    // Benchmark-variant ONLY: real HEIC encoding for the macrobenchmark corpus seeder (APP-390).
    // Never on the shipped debug/release classpath, so the egress guard is unaffected.
    "benchmarkImplementation"(libs.androidx.heifwriter)

    // Instrumented-test lane (Compose UI). BOM + ui-test-junit4 come from the compose convention.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
