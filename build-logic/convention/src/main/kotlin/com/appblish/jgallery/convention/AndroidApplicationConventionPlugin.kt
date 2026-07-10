package com.appblish.jgallery.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * The `:app` module. Applies the storage-boundary lint check too — the app wires DI bindings for
 * the storage abstraction but must not perform raw file ops itself.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                targetSdk = BuildConfig.TARGET_SDK
                versionCode = 1
                versionName = "0.1.0"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
                // The macrobenchmark target (spec §11 10k-scroll frame-time gate, APP-342).
                // A release-like, NON-debuggable, profileable variant — macrobenchmark refuses
                // debuggable builds because JIT/debug hooks distort frame timing. Kept minify-off
                // so the measured code path matches the tiles/list users actually scroll (not a
                // shrunk graph) and so the benchmark-only PhotosBenchmarkActivity survives without
                // keep rules. Debug-signed so it installs on CI/dev without a release keystore.
                // Profileable is declared in app/src/benchmark/AndroidManifest.xml.
                create("benchmark") {
                    initWith(getByName("release"))
                    isMinifyEnabled = false
                    isShrinkResources = false
                    isDebuggable = false
                    signingConfig = signingConfigs.getByName("debug")
                    // Feature/core modules only publish debug+release; consume their release
                    // variant from this benchmark variant.
                    matchingFallbacks += "release"
                }
            }
        }

        applyStorageBoundaryLint()
    }
}
