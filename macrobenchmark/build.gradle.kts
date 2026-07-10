import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * :macrobenchmark — the Wave-2 headline-gate harness (spec §11, APP-342).
 *
 * A `com.android.test` module (NOT shipped): it builds a separate test APK that drives the real
 * `:app` from another process and records `androidx.benchmark.macro` FrameTimingMetric while
 * scrolling a 10 000+ item Photos grid. It targets the app's non-debuggable, profileable
 * `benchmark` variant (see AndroidApplicationConventionPlugin) so the numbers are trustworthy.
 *
 * Deliberately does NOT apply the jgallery convention plugins: it needs neither the storage-boundary
 * lint (it touches no storage) nor the egress guard (that guards the shipped :app), and it is
 * test-only code held to test standards, not warningsAsErrors production lint.
 *
 * Run:  ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 * On an emulator add: -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
 */
plugins {
    // Applied by id (no version): AGP + the Kotlin plugin are already on the build classpath via
    // build-logic (the same way every convention plugin applies them), so a versioned request here
    // fails with "already on the classpath". This is a plain test module, not a convention consumer.
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.appblish.jgallery.macrobenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 29 // FrameTimingMetric needs API 29+ (dumpsys gfxinfo frame data)
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // This test module measures the app's `benchmark` build type. The name matches the app's
    // build type so variant-aware resolution wires them together; the fallback covers the core/
    // feature libraries that only publish `release`. Run via `connectedBenchmarkAndroidTest`.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
