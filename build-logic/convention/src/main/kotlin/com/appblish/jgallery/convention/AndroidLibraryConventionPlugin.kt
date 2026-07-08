package com.appblish.jgallery.convention

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Standard Android library module. Applies the storage-boundary lint check, so any raw
 * `java.io.File` / `MediaStore` / `Environment` / `MANAGE_EXTERNAL_STORAGE` usage here fails
 * the build. Only `:core:storage` (via [AndroidStorageConventionPlugin]) is exempt.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig {
                // Library modules must NOT set defaultConfig.targetSdk (deprecated in AGP 8).
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }
            testOptions.targetSdk = BuildConfig.TARGET_SDK
        }

        applyStorageBoundaryLint()

        dependencies {
            add("implementation", libs.library("kotlinx-coroutines-core"))
            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.library("androidx-test-runner"))
            add("androidTestImplementation", libs.library("androidx-test-rules"))
        }
    }
}
